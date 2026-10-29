package com.podcapture.transcription

import android.media.AudioFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class PcmConversionTest {

    // ==================== helpers ====================

    /** Interleaved 16-bit PCM where channel c of frame i is [sample](i, c). */
    private fun interleaved(frames: Int, channels: Int, sample: (Int, Int) -> Int): ShortArray =
        ShortArray(frames * channels) { k -> sample(k / channels, k % channels).toShort() }

    private fun sine(i: Int, rate: Int, hz: Double = 440.0, amplitude: Double = 12_000.0): Int =
        (amplitude * sin(2 * PI * hz * i / rate)).roundToInt()

    /** Runs the whole buffer through a fresh resampler in one write. */
    private fun convert(pcm: ShortArray, rate: Int, channels: Int): FloatArray {
        val resampler = Mono16kResampler(16)
        resampler.setInputRate(rate)
        resampler.write(ShortBuffer.wrap(pcm), channels, 0, pcm.size / channels)
        val size = resampler.finish()
        return resampler.samples.copyOf(size)
    }

    /**
     * The previous whole-buffer implementation (integer stereo average, linear-interpolation
     * resample over shorts, then / Short.MAX_VALUE), kept as a reference for the streaming one.
     */
    private fun legacyConvert(pcm: ShortArray, rate: Int, channels: Int): FloatArray {
        val mono = if (channels == 1) pcm else ShortArray(pcm.size / channels) { i ->
            var sum = 0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            (sum / channels).toShort()
        }
        val resampled = if (rate == 16_000 || mono.isEmpty()) mono else {
            val ratio = rate.toDouble() / 16_000
            val length = (mono.size / ratio).toInt()
            ShortArray(length) { i ->
                val pos = i * ratio
                val idx = pos.toInt()
                val frac = pos - idx
                if (idx >= mono.size - 1) mono[mono.size - 1]
                else (mono[idx] + (mono[idx + 1] - mono[idx]) * frac).toInt().toShort()
            }
        }
        return FloatArray(resampled.size) { resampled[it].toFloat() / Short.MAX_VALUE }
    }

    private fun assertClose(actual: FloatArray, expected: FloatArray, tolerance: Float) {
        assertThat(actual.size).isEqualTo(expected.size)
        for (i in actual.indices) {
            if (abs(actual[i] - expected[i]) > tolerance) {
                throw AssertionError("sample $i: expected ${expected[i]} but was ${actual[i]}")
            }
        }
    }

    // Integer truncation in the legacy path can differ from the float path by ~2 LSB.
    private val lsbTolerance = 3f / Short.MAX_VALUE

    // ==================== down-mix + resample ====================

    @Test
    fun `44_1 kHz stereo is down-mixed and resampled to 16 kHz`() {
        val frames = 44_100 // 1 second
        val pcm = interleaved(frames, 2) { i, c -> sine(i, 44_100) + if (c == 0) 100 else -100 }

        val out = convert(pcm, 44_100, 2)

        assertThat(out.size).isEqualTo(16_000)
        assertClose(out, legacyConvert(pcm, 44_100, 2), lsbTolerance)
        // Spot-check against the analytic signal at 16 kHz (the +/-100 offsets cancel).
        for (i in listOf(0, 1, 100, 7_919, 15_999)) {
            assertThat(out[i].toDouble())
                .isWithin(0.01)
                .of(12_000.0 * sin(2 * PI * 440.0 * i / 16_000) / Short.MAX_VALUE)
        }
    }

    @Test
    fun `22_05 kHz mono is resampled to 16 kHz`() {
        val frames = 22_050 * 2 // 2 seconds
        val pcm = interleaved(frames, 1) { i, _ -> sine(i, 22_050, hz = 300.0) }

        val out = convert(pcm, 22_050, 1)

        assertThat(out.size).isEqualTo(32_000)
        assertClose(out, legacyConvert(pcm, 22_050, 1), lsbTolerance)
    }

    @Test
    fun `16 kHz mono passes through unchanged`() {
        val pcm = interleaved(1_000, 1) { i, _ -> (i * 37) % 20_000 - 10_000 }

        val out = convert(pcm, 16_000, 1)

        assertThat(out.size).isEqualTo(1_000)
        for (i in out.indices) {
            assertThat(out[i]).isWithin(1e-7f).of(pcm[i].toFloat() / Short.MAX_VALUE)
        }
    }

    @Test
    fun `opposite stereo channels cancel to silence`() {
        val pcm = interleaved(4_800, 2) { i, c -> if (c == 0) sine(i, 48_000) else -sine(i, 48_000) }

        val out = convert(pcm, 48_000, 2)

        assertThat(out.size).isEqualTo(1_600)
        assertThat(out.maxOf { abs(it) }).isLessThan(1e-6f)
    }

    @Test
    fun `more than two channels are averaged instead of treated as mono`() {
        // 5.1 at 16 kHz: channel c carries 1000 * (c + 1), so the mean is 3500.
        val pcm = interleaved(160, 6) { _, c -> 1000 * (c + 1) }

        val out = convert(pcm, 16_000, 6)

        assertThat(out.size).isEqualTo(160) // not 960, which treating it as mono would give
        out.forEach { assertThat(it).isWithin(1e-6f).of(3500f / Short.MAX_VALUE) }
    }

    @Test
    fun `float PCM is down-mixed without rescaling`() {
        val resampler = Mono16kResampler(16)
        resampler.setInputRate(16_000)
        val pcm = floatArrayOf(0.5f, 0.25f, -1f, 1f, 0.2f, 0.2f)
        resampler.write(FloatBuffer.wrap(pcm), 2, 0, 3)
        val size = resampler.finish()

        assertThat(resampler.samples.copyOf(size).toList())
            .containsExactly(0.375f, 0f, 0.2f).inOrder()
    }

    @Test
    fun `streaming in uneven buffers matches converting all at once`() {
        val rate = 44_100
        val frames = 30_011
        val pcm = interleaved(frames, 2) { i, c -> sine(i, rate, hz = 523.0 + c) }

        val resampler = Mono16kResampler(16) // forces growth too
        resampler.setInputRate(rate)
        val buffer = ShortBuffer.wrap(pcm)
        var frame = 0
        val sizes = intArrayOf(1, 2, 1024, 7, 2048, 333)
        var k = 0
        while (frame < frames) {
            val end = minOf(frame + sizes[k++ % sizes.size], frames)
            resampler.write(buffer, 2, frame, end)
            frame = end
        }
        val size = resampler.finish()

        assertThat(resampler.samples.copyOf(size)).isEqualTo(convert(pcm, rate, 2))
        assertThat(size).isEqualTo((frames * 16_000L / rate).toInt())
    }

    @Test
    fun `only the requested frame range of a buffer is consumed`() {
        val pcm = interleaved(100, 2) { i, _ -> i * 10 }
        val resampler = Mono16kResampler(16)
        resampler.setInputRate(16_000)
        resampler.write(ShortBuffer.wrap(pcm), 2, 10, 20)
        val size = resampler.finish()

        assertThat(size).isEqualTo(10)
        assertThat(resampler.samples[0]).isWithin(1e-7f).of(100f / Short.MAX_VALUE)
        assertThat(resampler.samples[9]).isWithin(1e-7f).of(190f / Short.MAX_VALUE)
    }

    @Test
    fun `sample rate change mid-stream starts a new segment at the new rate`() {
        val resampler = Mono16kResampler(16)
        resampler.setInputRate(22_050)
        resampler.write(ShortBuffer.wrap(ShortArray(22_050)), 1, 0, 22_050)
        resampler.setInputRate(44_100)
        resampler.write(ShortBuffer.wrap(ShortArray(44_100 * 2)), 2, 0, 44_100)

        // One second at each rate.
        assertThat(resampler.finish()).isEqualTo(32_000)
    }

    @Test
    fun `HE-AAC output resampled at the decoder rate keeps real-time length`() {
        // Container declares 22.05 kHz mono; the SBR + parametric-stereo decoder actually emits
        // 44.1 kHz stereo. One second of decoder output must give one second at 16 kHz.
        val container = PcmFormat(22_050, 1)
        val decoder = PcmFormat.resolve(44_100, 2, null, previous = container)
        val pcm = interleaved(44_100, 2) { i, _ -> sine(i, 44_100) }

        assertThat(convert(pcm, decoder.sampleRate, decoder.channelCount).size).isEqualTo(16_000)
        // Using the container format (the old behavior) yields 4x too many samples:
        // twice for the rate, twice for treating stereo frames as mono samples.
        assertThat(convert(pcm, container.sampleRate, container.channelCount).size).isEqualTo(64_000)
    }

    @Test
    fun `empty input yields no samples`() {
        val resampler = Mono16kResampler(16)
        resampler.setInputRate(44_100)
        assertThat(resampler.finish()).isEqualTo(0)
        assertThat(Mono16kResampler(16).finish()).isEqualTo(0)
    }

    // ==================== decoder output format ====================

    @Test
    fun `format change values override the previous format`() {
        val previous = PcmFormat(22_050, 1)
        val changed = PcmFormat.resolve(44_100, 2, AudioFormat.ENCODING_PCM_16BIT, previous)

        assertThat(changed).isEqualTo(PcmFormat(44_100, 2, AudioFormat.ENCODING_PCM_16BIT))
        assertThat(changed.bytesPerFrame).isEqualTo(4)
    }

    @Test
    fun `missing keys fall back to the previous format and 16-bit encoding`() {
        val previous = PcmFormat(48_000, 2, AudioFormat.ENCODING_PCM_FLOAT)
        val resolved = PcmFormat.resolve(null, null, null, previous)

        assertThat(resolved).isEqualTo(PcmFormat(48_000, 2, AudioFormat.ENCODING_PCM_16BIT))
    }

    @Test
    fun `float PCM is accepted`() {
        val format = PcmFormat.resolve(48_000, 6, AudioFormat.ENCODING_PCM_FLOAT, null)

        assertThat(format.isFloat).isTrue()
        assertThat(format.bytesPerFrame).isEqualTo(24)
    }

    @Test
    fun `non 16-bit integer encodings are rejected`() {
        for (encoding in listOf(
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                PcmFormat.resolve(44_100, 2, encoding, null)
            }
        }
    }

    @Test
    fun `missing rate without a previous format is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PcmFormat.resolve(null, 2, null, null)
        }
    }

    // ==================== window trimming ====================

    @Test
    fun `buffer inside the window is kept whole`() {
        val range = PcmWindow.framesInWindow(1024, 44_100, 5_000_000, 1_000_000, 9_000_000)
        assertThat(range).isEqualTo(FrameRange(0, 1024))
    }

    @Test
    fun `buffer outside the window is dropped`() {
        assertThat(PcmWindow.framesInWindow(1024, 44_100, 0, 1_000_000, 2_000_000)).isNull()
        assertThat(PcmWindow.framesInWindow(1024, 44_100, 2_000_001, 1_000_000, 2_000_000)).isNull()
    }

    @Test
    fun `buffer spanning the window start is floored`() {
        // 1000 frames at 10 kHz = 100 ms starting at 0.95 s; window starts at 1.0 s.
        val range = PcmWindow.framesInWindow(1000, 10_000, 950_000, 1_000_000, 5_000_000)
        assertThat(range).isEqualTo(FrameRange(500, 1000))
    }

    @Test
    fun `buffer spanning the window end is ceiled`() {
        // 1000 frames at 10 kHz = 100 ms starting at 4.95 s; window ends at 5.0 s plus a bit.
        val range = PcmWindow.framesInWindow(1000, 10_000, 4_950_000, 1_000_000, 5_000_050)
        assertThat(range).isEqualTo(FrameRange(0, 501))
    }

    @Test
    fun `trimming uses the decoder rate to time the buffer`() {
        // 2048 frames: 46 ms at the real 44.1 kHz, but 92 ms at a declared 22.05 kHz.
        val real = PcmWindow.framesInWindow(2048, 44_100, 1_000_000, 1_060_000, 9_000_000)
        val declared = PcmWindow.framesInWindow(2048, 22_050, 1_000_000, 1_060_000, 9_000_000)

        assertThat(real).isNull() // ends at ~1.046 s, before the window
        assertThat(declared).isNotNull()
    }
}
