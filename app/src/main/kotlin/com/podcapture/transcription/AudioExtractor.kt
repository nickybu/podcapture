package com.podcapture.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.podcapture.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

class AudioExtractor(private val context: Context) {

    /**
     * Mono 16 kHz audio as normalised floats ([-1, 1]), ready for the recognizer.
     *
     * [samples] may be longer than the audio; only the first [sampleCount] values are valid.
     */
    class AudioSegment(
        val samples: FloatArray,
        val sampleCount: Int,
        val sampleRate: Int = Mono16kResampler.OUTPUT_SAMPLE_RATE
    )

    companion object {
        private const val TAG = "AudioExtractor"
        // Buffer margin to account for seek imprecision and decoder delays
        // MP3/AAC frames can be ~26ms each, and decoders may buffer several frames
        private const val SEEK_MARGIN_MS = 2000L  // 2 seconds before start
        private const val END_MARGIN_MS = 2000L   // 2 seconds after end
        private const val CODEC_TIMEOUT_US = 10_000L
        // Slack on top of the expected 16 kHz sample count so the output normally never grows.
        private const val OUTPUT_SLACK_SAMPLES = 4096
    }

    suspend fun extractSegment(
        uri: Uri,
        startMs: Long,
        endMs: Long
    ): AudioSegment = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)

            // Find audio track
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                throw IllegalStateException("No audio track found")
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            // The container's view of the stream is only a fallback: the decoder's output format
            // is authoritative (HE-AAC decodes at twice the declared rate, and parametric stereo
            // decodes mono-declared streams to stereo).
            val containerFormat = format.toPcmFormat(previous = null)

            Log.d(TAG, "Container format: $mime, sampleRate=${containerFormat.sampleRate}, channels=${containerFormat.channelCount}")

            // Create decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            var pcmFormat = try {
                decoder.outputFormat.toPcmFormat(previous = containerFormat)
            } catch (e: IllegalStateException) {
                containerFormat
            }
            Log.d(TAG, "Initial decoder output format: $pcmFormat")

            // Target window in microseconds
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            // Add margins for seeking and decoder buffering
            // We'll extract more than needed, then trim precisely to the target window
            val seekTargetUs = ((startMs - SEEK_MARGIN_MS) * 1000L).coerceAtLeast(0L)
            val inputEndUs = (endMs + END_MARGIN_MS) * 1000L

            Log.d(TAG, "Target window: ${startMs}ms - ${endMs}ms")
            Log.d(TAG, "Extraction window with margins: ${seekTargetUs/1000}ms - ${inputEndUs/1000}ms")

            extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            Log.d(TAG, "Seeked to: ${extractor.sampleTime / 1000}ms")

            // Preallocate the output for the window length at 16 kHz.
            val expectedSamples = (endMs - startMs).coerceAtLeast(0L) *
                Mono16kResampler.OUTPUT_SAMPLE_RATE / 1000L
            val resampler = Mono16kResampler(
                (expectedSamples + OUTPUT_SLACK_SAMPLES).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var inputSamplesCount = 0
            var outputBuffersCount = 0

            while (!outputDone) {
                // Feed input - continue until well past the end time to ensure decoder outputs everything
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime

                        if (sampleSize < 0 || sampleTimeUs > inputEndUs) {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                            Log.d(TAG, "Input done after $inputSamplesCount samples, last time: ${sampleTimeUs/1000}ms")
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                sampleTimeUs, 0
                            )
                            extractor.advance()
                            inputSamplesCount++
                        }
                    }
                }

                // Get output
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        pcmFormat = decoder.outputFormat.toPcmFormat(previous = pcmFormat)
                        Log.d(TAG, "Decoder output format changed: $pcmFormat")
                    }

                    outputBufferIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            outputBuffersCount++
                            val logDetails = BuildConfig.DEBUG &&
                                (outputBuffersCount <= 3 || (inputDone && outputBuffersCount % 10 == 0))
                            consumeOutputBuffer(
                                decoder, outputBufferIndex, bufferInfo, pcmFormat,
                                startUs, endUs, resampler,
                                logLabel = if (logDetails) "#$outputBuffersCount" else null
                            )
                        }
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                            Log.d(TAG, "Output done after $outputBuffersCount buffers")
                        }
                    }

                    // INFO_TRY_AGAIN_LATER, or the deprecated INFO_OUTPUT_BUFFERS_CHANGED
                    // (irrelevant since buffers are fetched with getOutputBuffer).
                    else -> Unit
                }
            }

            val sampleCount = resampler.finish()
            val finalDurationMs = sampleCount * 1000L / Mono16kResampler.OUTPUT_SAMPLE_RATE
            Log.d(TAG, "Final audio: $sampleCount samples = ${finalDurationMs}ms (target: ${endMs - startMs}ms)")

            AudioSegment(resampler.samples, sampleCount)

        } finally {
            decoder?.stop()
            decoder?.release()
            extractor.release()
        }
    }

    /**
     * Trims one decoded buffer to the target window and streams the kept frames straight into
     * [resampler] (down-mix and resample in one pass, no intermediate copy).
     */
    private fun consumeOutputBuffer(
        decoder: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        format: PcmFormat,
        windowStartUs: Long,
        windowEndUs: Long,
        resampler: Mono16kResampler,
        logLabel: String?
    ) {
        val totalFrames = info.size / format.bytesPerFrame
        val range = PcmWindow.framesInWindow(
            totalFrames, format.sampleRate, info.presentationTimeUs, windowStartUs, windowEndUs
        )

        if (logLabel != null) {
            val endUs = info.presentationTimeUs + totalFrames * 1_000_000L / format.sampleRate
            Log.d(TAG, "Output buffer $logLabel: ${info.presentationTimeUs / 1000}ms - ${endUs / 1000}ms (${info.size} bytes, keep=$range)")
        }

        if (range == null || range.isEmpty) return

        val buffer = decoder.getOutputBuffer(index) ?: return
        buffer.limit(info.offset + info.size)
        buffer.position(info.offset)
        // MediaCodec PCM is in native byte order; slice() resets the order, so set it afterwards.
        val pcm = buffer.slice().order(ByteOrder.nativeOrder())

        resampler.setInputRate(format.sampleRate)
        if (format.isFloat) {
            resampler.write(pcm.asFloatBuffer(), format.channelCount, range.start, range.end)
        } else {
            resampler.write(pcm.asShortBuffer(), format.channelCount, range.start, range.end)
        }
    }

    private fun MediaFormat.toPcmFormat(previous: PcmFormat?): PcmFormat = PcmFormat.resolve(
        sampleRate = intOrNull(MediaFormat.KEY_SAMPLE_RATE),
        channelCount = intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
        encoding = intOrNull(MediaFormat.KEY_PCM_ENCODING),
        previous = previous
    )

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }
}
