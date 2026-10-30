package com.podcapture.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    ERROR
}

data class PlaybackState(
    val playerState: PlayerState = PlayerState.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
)

/**
 * Clamps a seek target to the playable range.
 *
 * Before the media is prepared, Media3 reports `C.TIME_UNSET` (a large negative value)
 * for the duration. Clamping against it would force every seek to 0 and silently drop
 * the resume position, so the upper bound only applies once the duration is known.
 */
internal fun clampSeekPosition(positionMs: Long, durationMs: Long): Long =
    if (durationMs > 0L) {
        positionMs.coerceIn(0L, durationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }

internal const val MIN_PLAYBACK_SPEED = 0.5f
internal const val MAX_PLAYBACK_SPEED = 2.0f

/** Keeps playback speed within the range the speed controls offer. */
internal fun clampPlaybackSpeed(speed: Float): Float =
    speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)

/**
 * App-side handle on [PlaybackService], via a MediaController.
 *
 * Lives for the whole process, but the service does not: it is stopped when the task is
 * swiped away while paused, and can be killed by the system. Its session going away
 * disconnects the controller for good, so the controller is dropped on disconnect and a new
 * one is connected on the next command. Commands issued while connecting are queued and
 * replayed in order once the connection exists. Must be used from the main thread.
 */
class AudioPlayerService(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private companion object {
        const val TAG = "AudioPlayerService"
        // 4 Hz is smooth enough for a progress bar and a seconds readout.
        const val POSITION_UPDATE_INTERVAL_MS = 250L
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // The user's chosen speed: restored from settings at start-up, or set via
    // setPlaybackSpeed. Null until either happens, so we never push a default over a
    // saved value. Applied on connect and on every load so it survives app restarts and
    // PlaybackService re-creation.
    private var preferredSpeed: Float? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // The playhead on its own, so UI that only draws progress can subscribe to it without
    // recomposing on every other playback state change. playbackState.currentPositionMs
    // carries the same value at the same cadence for existing collectors.
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _currentAudioFileId = MutableStateFlow<String?>(null)
    val currentAudioFileId: StateFlow<String?> = _currentAudioFileId.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var currentUri: Uri? = null

    // At most one connection attempt is in flight at a time; non-null while connecting.
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val pendingCommands = PendingCommandQueue<MediaController>()
    private var released = false

    init {
        restorePlaybackSpeed()
        connectToService()
    }

    private fun connectedController(): MediaController? =
        mediaController?.takeIf { it.isConnected }

    /** Runs [command] now if connected, otherwise once the (re)connection completes. */
    private fun withController(command: (MediaController) -> Unit) {
        if (released) return
        val controller = connectedController()
        if (controller != null) {
            command(controller)
        } else {
            pendingCommands.enqueue(command)
            connectToService()
        }
    }

    /** Starts connecting unless already connected or connecting. */
    private fun connectToService() {
        if (released) return
        val action = connectAction(
            controllerConnected = connectedController() != null,
            connectInFlight = controllerFuture != null
        )
        if (action != ConnectAction.QUEUE_AND_CONNECT) return

        discardController()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken)
            .setListener(controllerListener)
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()
        controllerFuture = future
        future.addListener({ onConnectFinished(future) }, ContextCompat.getMainExecutor(context))
    }

    private fun onConnectFinished(future: ListenableFuture<MediaController>) {
        // Released, or superseded by a newer attempt, while this one was in flight.
        if (future !== controllerFuture) return
        controllerFuture = null

        val controller = try {
            future.get()
        } catch (e: Exception) {
            Log.w(TAG, "Could not connect to PlaybackService", e)
            null
        }
        if (controller == null || !controller.isConnected) {
            controller?.release()
            // Only surface an error if the user was waiting on a command; a failed
            // background connect at start-up retries on the next command anyway.
            onConnectionLost(
                errorMessage = if (pendingCommands.isEmpty()) null else "Could not connect to the playback service"
            )
            return
        }

        mediaController = controller
        controller.addListener(playerListener)
        preferredSpeed?.let { controller.setPlaybackSpeed(it) }
        pendingCommands.drainTo(controller)
        updateState()
        if (controller.isPlaying) startPositionUpdates()
    }

    private val controllerListener = object : MediaController.Listener {
        // The session was released (PlaybackService destroyed) or its process died. This
        // controller can't be used again; the next command connects a new one.
        override fun onDisconnected(controller: MediaController) {
            // Also called for controllers we released ourselves, which are already detached.
            if (controller !== mediaController) return
            discardController()
            onConnectionLost(errorMessage = null)
        }
    }

    /** Detaches and releases the current controller, if any. */
    private fun discardController() {
        val controller = mediaController ?: return
        // Cleared first so the onDisconnected that release() triggers is ignored.
        mediaController = null
        controller.removeListener(playerListener)
        controller.release()
    }

    /**
     * Nothing is loaded any more: forget the media so the next load starts from scratch
     * (otherwise "already loaded" checks would skip loadAudio and the UI would wait forever).
     */
    private fun onConnectionLost(errorMessage: String?) {
        pendingCommands.clear()
        stopPositionUpdates()
        currentUri = null
        _currentAudioFileId.value = null
        val idle = disconnectedPlaybackState(_playbackState.value)
        _playbackState.value = if (errorMessage != null) {
            idle.copy(playerState = PlayerState.ERROR, errorMessage = errorMessage)
        } else {
            idle
        }
        _position.value = 0L
    }

    private fun restorePlaybackSpeed() {
        scope.launch {
            val savedSpeed = clampPlaybackSpeed(settingsDataStore.playbackSpeed.first())
            // A speed the user picked before this read finished wins.
            if (preferredSpeed == null) {
                preferredSpeed = savedSpeed
                applyPlaybackSpeed(savedSpeed)
            }
        }
    }

    private fun applyPlaybackSpeed(speed: Float) {
        connectedController()?.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState()
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.value = _playbackState.value.copy(
                playerState = PlayerState.ERROR,
                errorMessage = error.message
            )
        }
    }

    fun getCurrentUri(): Uri? = currentUri

    fun isLoadedUri(uri: Uri): Boolean = currentUri?.toString() == uri.toString()

    private fun updateState() {
        val controller = connectedController() ?: return

        val state = when {
            controller.playerError != null -> PlayerState.ERROR
            controller.isPlaying -> PlayerState.PLAYING
            controller.playbackState == Player.STATE_BUFFERING -> PlayerState.LOADING
            controller.playbackState == Player.STATE_READY -> {
                if (controller.playWhenReady) PlayerState.PLAYING else PlayerState.PAUSED
            }
            controller.playbackState == Player.STATE_ENDED -> PlayerState.PAUSED
            controller.playbackState == Player.STATE_IDLE -> PlayerState.IDLE
            else -> PlayerState.IDLE
        }

        val positionMs = controller.currentPosition
        _position.value = positionMs
        _playbackState.value = _playbackState.value.copy(
            playerState = state,
            currentPositionMs = positionMs,
            durationMs = controller.duration.coerceAtLeast(0L),
            playbackSpeed = controller.playbackParameters.speed
        )
    }

    private fun publishPosition(positionMs: Long, durationMs: Long = _playbackState.value.durationMs) {
        _position.value = positionMs
        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = positionMs,
            durationMs = durationMs
        )
    }

    // Runs only while playing: started and stopped from onIsPlayingChanged.
    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                connectedController()?.let { controller ->
                    publishPosition(controller.currentPosition, controller.duration.coerceAtLeast(0L))
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun loadAudio(
        uri: Uri,
        audioFileId: String? = null,
        mimeType: String? = null,
        title: String? = null,
        artist: String? = null,
        startPositionMs: Long = 0L
    ) {
        currentUri = uri
        _currentAudioFileId.value = audioFileId
        _playbackState.value = _playbackState.value.copy(
            playerState = PlayerState.LOADING,
            currentPositionMs = startPositionMs,
            errorMessage = null
        )
        _position.value = startPositionMs

        withController { controller ->
            val metadata = MediaMetadata.Builder()
                .setTitle(title ?: "PodCapture")
                .setArtist(artist ?: "PodCapture")
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .apply {
                    if (mimeType != null) setMimeType(mimeType)
                }
                .build()

            // setMediaItem(item, startPositionMs) sets the initial playhead atomically
            // with the media load — critical because a separate seekTo() before
            // prepare completes would be clamped by an unknown duration.
            if (startPositionMs > 0L) {
                controller.setMediaItem(mediaItem, startPositionMs)
            } else {
                controller.setMediaItem(mediaItem)
            }
            preferredSpeed?.let { controller.setPlaybackSpeed(it) }
            controller.prepare()
        }
    }

    fun play() {
        withController { controller ->
            controller.playWhenReady = true
            controller.play()
        }
    }

    // Nothing can be playing without a connected controller, so there is nothing to pause
    // and no reason to start the service just to do so.
    fun pause() {
        connectedController()?.pause()
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.playWhenReady = true
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { controller ->
            val clampedPosition = clampSeekPosition(positionMs, controller.duration)
            controller.seekTo(clampedPosition)
            publishPosition(clampedPosition)
        }
    }

    fun seekRelative(deltaMs: Long) {
        withController { controller ->
            // seekTo() applies the duration-aware clamp; clamping here against
            // duration.coerceAtLeast(0) turned every skip during preparation into seekTo(0).
            seekTo(controller.currentPosition + deltaMs)
        }
    }

    fun rewind(ms: Long = 10_000L) {
        seekRelative(-ms)
    }

    fun fastForward(ms: Long = 10_000L) {
        seekRelative(ms)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = clampPlaybackSpeed(speed)
        preferredSpeed = clampedSpeed
        applyPlaybackSpeed(clampedSpeed)
        scope.launch {
            settingsDataStore.setPlaybackSpeed(clampedSpeed)
        }
    }

    fun stop() {
        connectedController()?.stop()
        stopPositionUpdates()
    }

    /**
     * Stops playback and drops the connection for good. The controller listener is set on
     * the builder and can't be removed, but releasing the controller ends its callbacks and
     * the identity check in onDisconnected ignores the one release() itself triggers.
     */
    fun release() {
        stop()
        released = true
        pendingCommands.clear()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        discardController()
    }

    fun getCurrentPosition(): Long = connectedController()?.currentPosition ?: 0L

    fun getDuration(): Long = connectedController()?.duration?.coerceAtLeast(0L) ?: 0L
}
