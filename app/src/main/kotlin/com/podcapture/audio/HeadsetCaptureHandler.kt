package com.podcapture.audio

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import com.podcapture.capture.CaptureManager
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Turns a headset "next track" gesture into a capture.
 *
 * Bluetooth headsets resolve multi-press gestures in their own firmware and send a single
 * AVRCP passthrough command, which Android delivers to the active media session as a
 * KEYCODE_MEDIA_NEXT key event:
 *  - Bose QC45: double press of the multi-function (play/pause) button
 *  - Sony WF-1000XM5: double tap on the earbud assigned to playback control
 *
 * PodCapture only ever plays one item at a time, so "next track" has no other meaning and
 * is free to repurpose.
 */
class HeadsetCaptureHandler(
    private val captureManager: CaptureManager,
    settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "HeadsetCapture"
        // Guards against a headset re-sending the command (or a stray triple-press)
        // starting a second capture for the same moment.
        private const val DEBOUNCE_MS = 1_000L
        private const val TONE_VOLUME = 60
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var enabled = true
    private var toneEnabled = true
    private var lastTriggerAtMs = 0L
    private var toneGenerator: ToneGenerator? = null

    init {
        scope.launch { settingsDataStore.headsetCaptureEnabled.collect { enabled = it } }
        scope.launch { settingsDataStore.headsetCaptureToneEnabled.collect { toneEnabled = it } }
    }

    /**
     * Returns true if the media button intent was consumed as a capture gesture, false to
     * let Media3 handle it normally. Must be called on the main thread.
     */
    fun onMediaButtonEvent(intent: Intent): Boolean {
        if (!enabled) return false
        val keyEvent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return false
        if (keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_NEXT) return false
        // Media3 only forwards ACTION_DOWN; swallow auto-repeats from a held button.
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
        if (keyEvent.repeatCount > 0) return true

        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerAtMs < DEBOUNCE_MS) return true
        lastTriggerAtMs = now

        val started = captureManager.capture { success ->
            if (!success) playTone(ToneGenerator.TONE_PROP_NACK)
        }
        Log.d(TAG, "Headset capture gesture received, started=$started")
        // Immediate feedback so the gesture is confirmed even with the screen off;
        // transcription itself can take several seconds.
        playTone(if (started) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK)
        return true
    }

    private fun playTone(toneType: Int) {
        if (!toneEnabled) return
        try {
            val generator = toneGenerator
                ?: ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME).also { toneGenerator = it }
            generator.startTone(toneType)
        } catch (e: RuntimeException) {
            // ToneGenerator can fail to initialise if the audio system is busy — feedback is
            // best-effort and must never break the capture itself.
            Log.w(TAG, "Unable to play capture tone", e)
        }
    }

    fun release() {
        scope.cancel()
        toneGenerator?.release()
        toneGenerator = null
    }
}
