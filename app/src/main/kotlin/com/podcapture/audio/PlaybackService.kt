package com.podcapture.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.podcapture.MainActivity
import com.podcapture.R
import com.podcapture.capture.CaptureManager
import com.podcapture.data.settings.SettingsDataStore
import org.koin.android.ext.android.inject

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private companion object {
        const val ACTION_CAPTURE = "com.podcapture.session.CAPTURE"
        val CAPTURE_COMMAND = SessionCommand(ACTION_CAPTURE, Bundle.EMPTY)
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    // Created on first use, not in onCreate: resolving CaptureManager pulls in the
    // repositories, TranscriptionService and WhisperModelManager, and the handler decodes
    // its cue sounds. None of that is needed until something is played.
    private var headsetCaptureHandler: HeadsetCaptureHandler? = null

    // Koin's inject() is lazy: neither is resolved until captureHandler() runs.
    private val captureManager: CaptureManager by inject()
    private val settingsDataStore: SettingsDataStore by inject()

    private fun captureHandler(): HeadsetCaptureHandler =
        headsetCaptureHandler
            ?: HeadsetCaptureHandler(this, captureManager, settingsDataStore)
                .also { headsetCaptureHandler = it }

    // Builds the capture handler as soon as media starts loading, so its settings are read
    // and its cue sounds decoded before the first headset gesture or notification tap.
    private val warmUpListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE) return
            captureHandler()
            exoPlayer?.removeListener(this)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Create ExoPlayer with audio focus handling
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true // Handle audio focus
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .build()
        player.addListener(warmUpListener)
        exoPlayer = player

        // Notification tap: bring the existing activity forward (SINGLE_TOP + CLEAR_TOP
        // deliver this to onNewIntent rather than stacking a second MainActivity) and ask it
        // to open the player for what's playing.
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // A Capture button in the media notification (and lock screen / system media
        // controls). SLOT_FORWARD puts it where "next" would be: PodCapture plays a single
        // item, so the player never offers "next" and the slot is free.
        val captureButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_notification_capture)
            .setDisplayName("Capture")
            .setSessionCommand(CAPTURE_COMMAND)
            .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setMediaButtonPreferences(listOf(captureButton))
            .setCallback(object : MediaSession.Callback {
                // Every controller may send CAPTURE: the notification and the system media
                // controls (the platform session) are the ones that show the button.
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(CAPTURE_COMMAND)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction != ACTION_CAPTURE) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    // Same path and cues as the headset gesture; plays the failure cue when
                    // nothing is loaded or a capture is already running.
                    captureHandler().captureFromNotification()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // Media button events from headsets arrive here before Media3's default
                // handling, so a double press/tap ("next track") can be turned into a capture.
                // The caller isn't checked: Media3 attributes some headset events to the
                // notification controller, and the notification has no "next" button anyway
                // (its Capture button is a custom command and arrives in onCustomCommand).
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    // Nothing has been loaded in this service yet, so there is nothing to
                    // capture: build the handler for next time and let Media3 handle the
                    // event as usual (rather than consuming it before the handler has read
                    // whether headset capture is enabled).
                    val handler = headsetCaptureHandler ?: run {
                        captureHandler()
                        return false
                    }
                    return handler.onMediaButtonEvent(intent)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            // Stop service if not playing when app is removed
            stopSelf()
        }
    }

    override fun onDestroy() {
        headsetCaptureHandler?.release()
        headsetCaptureHandler = null
        exoPlayer?.removeListener(warmUpListener)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }

    fun getPlayer(): ExoPlayer? = exoPlayer
}
