package com.podcapture.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.podcapture.MainActivity
import com.podcapture.capture.CaptureManager
import com.podcapture.data.settings.SettingsDataStore
import org.koin.android.ext.android.inject

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var headsetCaptureHandler: HeadsetCaptureHandler? = null

    private val captureManager: CaptureManager by inject()
    private val settingsDataStore: SettingsDataStore by inject()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Create ExoPlayer with audio focus handling
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true // Handle audio focus
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .build()

        // Create pending intent for the notification tap action
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val captureHandler = HeadsetCaptureHandler(this, captureManager, settingsDataStore)
        headsetCaptureHandler = captureHandler

        // Create media session
        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                // Media button events from headsets arrive here before Media3's default
                // handling, so a double press/tap ("next track") can be turned into a capture.
                // The caller isn't checked: Media3 attributes some headset events to the
                // notification controller, and the notification has no "next" button anyway.
                @OptIn(UnstableApi::class)
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean = captureHandler.onMediaButtonEvent(intent)
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
