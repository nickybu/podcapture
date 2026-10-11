package com.podcapture.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import com.podcapture.R
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
    context: Context,
    private val captureManager: CaptureManager,
    settingsDataStore: SettingsDataStore
) {
    companion object {
        // Guards against a headset re-sending the command (or a stray triple-press)
        // starting a second capture for the same moment.
        private const val DEBOUNCE_MS = 1_000L
        // Relative to media volume, so the cue sits under the podcast rather than over it.
        private const val SOUND_VOLUME = 0.7f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var enabled = true
    private var soundEnabled = true
    private var lastTriggerAtMs = 0L

    // Preloaded so the cue plays the instant the gesture lands — decoding on demand would
    // add a noticeable lag, especially over Bluetooth.
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val shutterSoundId = soundPool.load(context, R.raw.capture_shutter, 1)
    private val failedSoundId = soundPool.load(context, R.raw.capture_failed, 1)

    init {
        scope.launch { settingsDataStore.headsetCaptureEnabled.collect { enabled = it } }
        scope.launch { settingsDataStore.headsetCaptureToneEnabled.collect { soundEnabled = it } }
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
            if (!success) playSound(failedSoundId)
        }
        // Immediate feedback so the gesture is confirmed even with the screen off. The shutter
        // marks the moment being captured; the "saved" message follows once transcription
        // (which can take several seconds) finishes.
        playSound(if (started) shutterSoundId else failedSoundId)
        return true
    }

    private fun playSound(soundId: Int) {
        if (!soundEnabled) return
        // Returns 0 (and plays nothing) if the sound hasn't finished loading — feedback is
        // best-effort and must never get in the way of the capture itself.
        soundPool.play(soundId, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1f)
    }

    fun release() {
        scope.cancel()
        soundPool.release()
    }
}
