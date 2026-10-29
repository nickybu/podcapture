package com.podcapture.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class AudioPlayerService(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentAudioFileId = MutableStateFlow<String?>(null)
    val currentAudioFileId: StateFlow<String?> = _currentAudioFileId.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var currentUri: Uri? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.addListener(playerListener)
            updateState()
        }, MoreExecutors.directExecutor())
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

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playbackState.value = _playbackState.value.copy(
                playerState = PlayerState.ERROR,
                errorMessage = error.message
            )
        }
    }

    fun getCurrentUri(): Uri? = currentUri

    fun isLoadedUri(uri: Uri): Boolean = currentUri?.toString() == uri.toString()

    private fun updateState() {
        val controller = mediaController ?: return

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

        _playbackState.value = _playbackState.value.copy(
            playerState = state,
            currentPositionMs = controller.currentPosition,
            durationMs = controller.duration.coerceAtLeast(0L),
            playbackSpeed = controller.playbackParameters.speed
        )
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = controller.currentPosition,
                        durationMs = controller.duration.coerceAtLeast(0L)
                    )
                }
                delay(100) // Update every 100ms
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
            currentPositionMs = startPositionMs
        )

        mediaController?.let { controller ->
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
            controller.prepare()
        }
    }

    fun play() {
        mediaController?.let { controller ->
            controller.playWhenReady = true
            controller.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.let { controller ->
            val clampedPosition = clampSeekPosition(positionMs, controller.duration)
            controller.seekTo(clampedPosition)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = clampedPosition)
        }
    }

    fun seekRelative(deltaMs: Long) {
        mediaController?.let { controller ->
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
        mediaController?.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun stop() {
        mediaController?.stop()
        stopPositionUpdates()
    }

    fun release() {
        stop()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L

    fun getDuration(): Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
}
