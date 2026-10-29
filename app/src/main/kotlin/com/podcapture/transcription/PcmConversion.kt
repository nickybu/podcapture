package com.podcapture.transcription

import android.media.AudioFormat
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.ceil

/**
 * Layout of the PCM a decoder is currently producing.
 *
 * This is the decoder's *output* format, which can differ from what the container declares:
 * HE-AAC (SBR) streams usually declare half the real sample rate, and parametric-stereo streams
 * declare mono but decode to stereo.
 */
internal data class PcmFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    init {
        require(sampleRate > 0) { "Invalid sample rate: $sampleRate" }
        require(channelCount > 0) { "Invalid channel count: $channelCount" }
        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            "Unsupported PCM encoding: $encoding (only 16-bit and float PCM are supported)"
        }
    }

    val isFloat: Boolean get() = encoding == AudioFormat.ENCODING_PCM_FLOAT

    val bytesPerFrame: Int get() = channelCount * (if (isFloat) 4 else 2)

    companion object {
        /**
         * Builds the format from the values a decoder reports, falling back to [previous] for a
         * missing rate or channel count. A missing encoding means 16-bit PCM (the documented
         * default for MediaFormat.KEY_PCM_ENCODING).
         *
         * @throws IllegalArgumentException for an unsupported encoding or invalid values.
         */
        fun resolve(
            sampleRate: Int?,
            channelCount: Int?,
            encoding: Int?,
            previous: PcmFormat?
        ): PcmFormat = PcmFormat(
            sampleRate = sampleRate ?: previous?.sampleRate
                ?: throw IllegalArgumentException("Decoder did not report a sample rate"),
            channelCount = channelCount ?: previous?.channelCount
                ?: throw IllegalArgumentException("Decoder did not report a channel count"),
            encoding = encoding ?: AudioFormat.ENCODING_PCM_16BIT
        )
    }
}

/** Half-open range of frames `[start, end)` within one decoded buffer. */
internal data class FrameRange(val start: Int, val end: Int) {
    val isEmpty: Boolean get() = end <= start
}

internal object PcmWindow {
    /**
     * Returns the frames of a decoded buffer that fall inside the target window, or null if the
     * buffer lies entirely outside it.
     *
     * Buffer timing is derived from [sampleRate], which must be the decoder's actual output rate.
     * Uses floating-point fractions so boundary frames are not lost: the start index is floored
     * and the end index is ceiled.
     */
    fun framesInWindow(
        totalFrames: Int,
        sampleRate: Int,
        bufferStartUs: Long,
        windowStartUs: Long,
        windowEndUs: Long
    ): FrameRange? {
        val bufferDurationUs = (totalFrames * 1_000_000L) / sampleRate
        val bufferEndUs = bufferStartUs + bufferDurationUs

        if (bufferEndUs < windowStartUs || bufferStartUs > windowEndUs) return null
        if (bufferDurationUs <= 0 || totalFrames == 0) return FrameRange(0, totalFrames)

        val startFrame = if (bufferStartUs < windowStartUs) {
            val fraction = (windowStartUs - bufferStartUs).toDouble() / bufferDurationUs.toDouble()
            (fraction * totalFrames).toInt().coerceIn(0, totalFrames)
        } else 0

        val endFrame = if (bufferEndUs > windowEndUs) {
            val fraction = (windowEndUs - bufferStartUs).toDouble() / bufferDurationUs.toDouble()
            ceil(fraction * totalFrames).toInt().coerceIn(0, totalFrames)
        } else totalFrames

        return FrameRange(startFrame, endFrame)
    }
}

/**
 * Streams interleaved PCM in, down-mixes every frame to mono and resamples to 16 kHz with linear
 * interpolation, writing normalised floats ([-1, 1]) straight into one growable output array.
 *
 * The output matches resampling the whole concatenated mono signal at once: output sample `i` is
 * interpolated at source position `i * inputRate / 16000`, and a segment yields
 * `floor(inputFrames * 16000 / inputRate)` samples. If the input rate changes mid-stream the
 * current segment is closed and a new one starts at the new rate.
 *
 * Not thread-safe; one instance per extraction.
 */
internal class Mono16kResampler(initialCapacity: Int) {

    companion object {
        const val OUTPUT_SAMPLE_RATE = 16_000
        private const val SHORT_SCALE = 1f / Short.MAX_VALUE
    }

    private var output = FloatArray(initialCapacity.coerceAtLeast(16))

    /** Number of valid samples in [samples]. */
    var size: Int = 0
        private set

    /** Backing array; only the first [size] samples are valid. */
    val samples: FloatArray get() = output

    // State for the current input segment (a run of input at one sample rate).
    private var inputRate = 0
    private var step = 1.0 // input frames per output sample
    private var segmentInputFrames = 0L
    private var segmentOutputSamples = 0L
    private var previous = 0f

    /** Sets the sample rate of the frames passed to the next write calls. */
    fun setInputRate(rate: Int) {
        require(rate > 0) { "Invalid sample rate: $rate" }
        if (rate == inputRate) return
        if (inputRate != 0) closeSegment()
        inputRate = rate
        step = rate.toDouble() / OUTPUT_SAMPLE_RATE
        segmentInputFrames = 0
        segmentOutputSamples = 0
    }

    /** Writes frames `[fromFrame, toFrame)` of interleaved 16-bit PCM; index 0 is the buffer start. */
    fun write(pcm: ShortBuffer, channelCount: Int, fromFrame: Int, toFrame: Int) {
        check(inputRate != 0) { "setInputRate must be called before writing" }
        val scale = SHORT_SCALE / channelCount
        var index = fromFrame * channelCount
        for (frame in fromFrame until toFrame) {
            var sum = 0
            for (c in 0 until channelCount) sum += pcm.get(index++)
            push(sum * scale)
        }
    }

    /** Writes frames `[fromFrame, toFrame)` of interleaved float PCM; index 0 is the buffer start. */
    fun write(pcm: FloatBuffer, channelCount: Int, fromFrame: Int, toFrame: Int) {
        check(inputRate != 0) { "setInputRate must be called before writing" }
        val scale = 1f / channelCount
        var index = fromFrame * channelCount
        for (frame in fromFrame until toFrame) {
            var sum = 0f
            for (c in 0 until channelCount) sum += pcm.get(index++)
            push(sum * scale)
        }
    }

    /** Flushes the tail of the current segment and returns the final [size]. */
    fun finish(): Int {
        if (inputRate != 0) closeSegment()
        inputRate = 0
        return size
    }

    private fun push(sample: Float) {
        val n = segmentInputFrames
        if (n > 0) {
            // Emit every output whose source position lies in [n - 1, n): interpolate between
            // the previous input frame and this one.
            while (true) {
                val position = segmentOutputSamples * step
                val index = position.toLong()
                if (index >= n) break
                val fraction = (position - index).toFloat()
                append(previous + (sample - previous) * fraction)
                segmentOutputSamples++
            }
        }
        previous = sample
        segmentInputFrames = n + 1
    }

    private fun closeSegment() {
        val expected = (segmentInputFrames / step).toLong()
        // Positions past the last input frame repeat it.
        while (segmentOutputSamples < expected) {
            append(previous)
            segmentOutputSamples++
        }
        // Streaming can emit one interpolated sample more than floor(inputFrames / step).
        if (segmentOutputSamples > expected) {
            size -= (segmentOutputSamples - expected).toInt()
            segmentOutputSamples = expected
        }
        segmentInputFrames = 0
        segmentOutputSamples = 0
    }

    private fun append(value: Float) {
        if (size == output.size) {
            output = output.copyOf(output.size * 2)
        }
        output[size++] = value
    }
}
