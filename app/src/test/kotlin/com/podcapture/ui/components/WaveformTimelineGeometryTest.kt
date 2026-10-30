package com.podcapture.ui.components

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.model.Capture
import org.junit.Test

class WaveformTimelineGeometryTest {

    private fun capture(id: String, startMs: Long) = Capture(
        id = id,
        audioFileId = "file",
        timestampMs = startMs,
        windowStartMs = startMs,
        windowEndMs = startMs + 30_000,
        transcription = "",
        createdAt = 0L
    )

    // 100 s over 1000 px: 1 px per 100 ms. Dots at y = 20, 40 px touch radius.
    private val captures = listOf(capture("a", 10_000), capture("b", 50_000))

    private fun hit(x: Float, y: Float = 20f, list: List<Capture> = captures, durationMs: Long = 100_000) =
        findCaptureMarkerAt(x, y, list, durationMs, width = 1000f, dotY = 20f, touchRadius = 40f)

    @Test
    fun `tap on a dot returns its capture`() {
        assertThat(hit(100f)?.id).isEqualTo("a")
        assertThat(hit(530f, y = 50f)?.id).isEqualTo("b")
    }

    @Test
    fun `nearest dot wins when two are in range`() {
        val close = listOf(capture("a", 10_000), capture("c", 10_500))
        assertThat(hit(104f, list = close)?.id).isEqualTo("c")
    }

    @Test
    fun `taps away from dots return null`() {
        assertThat(hit(300f)).isNull()           // between dots
        assertThat(hit(100f, y = 70f)).isNull()  // below the dot row, on the waveform
        assertThat(hit(100f, list = emptyList())).isNull()
        assertThat(hit(100f, durationMs = 0)).isNull()
    }

    @Test
    fun `playback progress is clamped and safe for unknown duration`() {
        assertThat(playbackProgress(25_000, 100_000)).isEqualTo(0.25f)
        assertThat(playbackProgress(150_000, 100_000)).isEqualTo(1f)
        assertThat(playbackProgress(-5, 100_000)).isEqualTo(0f)
        assertThat(playbackProgress(5_000, 0)).isEqualTo(0f)
    }
}
