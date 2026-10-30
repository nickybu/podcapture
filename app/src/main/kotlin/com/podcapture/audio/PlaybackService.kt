package com.podcapture.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.podcapture.MainActivity
import com.podcapture.capture.CaptureManager
import com.podcapture.data.settings.SettingsDataStore
import org.koin.android.ext.android.inject

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

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

        // Create pending intent for the notification tap action
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                // Media button events from headsets arrive here before Media3's default
                // handling, so a double press/tap ("next track") can be turned into a capture.
                // The caller isn't checked: Media3 attributes some headset events to the
                // notification controller, and the notification has no "next" button anyway.
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
