package com.podcapture.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioExtractor(private val context: Context) {

    data class AudioSegment(
        val pcmData: ByteArray,
        val sampleRate: Int
    )

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
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Create decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            // Seek to BEFORE the start position to ensure we don't miss content
            // SEEK_TO_PREVIOUS_SYNC finds the sync frame at or before the target
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val outputStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = extractor.sampleTime

                        if (sampleSize < 0 || sampleTimeUs > endUs) {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                sampleTimeUs, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Get output
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    } else {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!

                        // Use the presentation timestamp from the decoder
                        val bufferStartTimeUs = bufferInfo.presentationTimeUs
                        val samplesInBuffer = bufferInfo.size / (2 * channelCount)
                        val bufferDurationUs = (samplesInBuffer * 1_000_000L) / sampleRate
                        val bufferEndTimeUs = bufferStartTimeUs + bufferDurationUs

                        // Only include samples that fall within our target window
                        if (bufferEndTimeUs >= startUs && bufferStartTimeUs <= endUs) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)

                            // If this buffer spans the start or end boundary, trim it
                            val trimmedChunk = trimChunkToWindow(
                                chunk, channelCount, sampleRate,
                                bufferStartTimeUs, bufferEndTimeUs,
                                startUs, endUs
                            )
                            outputStream.write(trimmedChunk)
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            // Convert to mono 16kHz if needed for Whisper
            val rawPcm = outputStream.toByteArray()
            val convertedPcm = convertToMono16kHz(rawPcm, sampleRate, channelCount)

            AudioSegment(convertedPcm, 16000)

        } finally {
            decoder?.stop()
            decoder?.release()
            extractor.release()
        }
    }

    /**
     * Trims a PCM chunk to only include samples within the target time window.
     * Uses floating-point arithmetic to avoid precision loss at window boundaries.
     */
    private fun trimChunkToWindow(
        chunk: ByteArray,
        channelCount: Int,
        sampleRate: Int,
        bufferStartUs: Long,
        bufferEndUs: Long,
        windowStartUs: Long,
        windowEndUs: Long
    ): ByteArray {
        val bytesPerSample = 2 * channelCount  // 16-bit per channel
        val totalSamples = chunk.size / bytesPerSample

        // Calculate which samples to keep
        val bufferDurationUs = bufferEndUs - bufferStartUs
        if (bufferDurationUs <= 0 || totalSamples == 0) return chunk

        // Use floating-point arithmetic to avoid precision loss
        // Start sample index within this buffer
        val startSampleIndex = if (bufferStartUs < windowStartUs) {
            val skipUs = (windowStartUs - bufferStartUs).toDouble()
            val fraction = skipUs / bufferDurationUs.toDouble()
            (fraction * totalSamples).toInt().coerceIn(0, totalSamples)
        } else 0

        // End sample index within this buffer
        val endSampleIndex = if (bufferEndUs > windowEndUs) {
            val keepUs = (windowEndUs - bufferStartUs).toDouble()
            val fraction = keepUs / bufferDurationUs.toDouble()
            // Use ceiling to ensure we don't cut off the last sample
            kotlin.math.ceil(fraction * totalSamples).toInt().coerceIn(0, totalSamples)
        } else totalSamples

        // If we need the whole buffer, return as-is
        if (startSampleIndex == 0 && endSampleIndex == totalSamples) {
            return chunk
        }

        // Extract the trimmed portion
        val startByte = startSampleIndex * bytesPerSample
        val endByte = endSampleIndex * bytesPerSample
        return chunk.copyOfRange(startByte.coerceAtLeast(0), endByte.coerceAtMost(chunk.size))
    }

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

    private fun convertToMono16kHz(
        input: ByteArray,
        inputSampleRate: Int,
        inputChannels: Int
    ): ByteArray {
        // Input is 16-bit PCM
        val shortBuffer = ByteBuffer.wrap(input)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val inputSamples = ShortArray(shortBuffer.remaining())
        shortBuffer.get(inputSamples)

        // Convert to mono if stereo
        val monoSamples = if (inputChannels == 2) {
            ShortArray(inputSamples.size / 2) { i ->
                ((inputSamples[i * 2].toInt() + inputSamples[i * 2 + 1].toInt()) / 2).toShort()
            }
        } else {
            inputSamples
        }

        // Resample to 16kHz if needed using linear interpolation
        val outputSamples = if (inputSampleRate != 16000) {
            resampleWithInterpolation(monoSamples, inputSampleRate, 16000)
        } else {
            monoSamples
        }

        // Convert back to bytes
        val outputBuffer = ByteBuffer.allocate(outputSamples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        outputSamples.forEach { outputBuffer.putShort(it) }

        return outputBuffer.array()
    }

    /**
     * Resamples audio using linear interpolation for better quality.
     * This prevents audio loss that can occur with nearest-neighbor resampling.
     */
    private fun resampleWithInterpolation(
        input: ShortArray,
        inputRate: Int,
        outputRate: Int
    ): ShortArray {
        if (input.isEmpty()) return input

        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outputLength = (input.size / ratio).toInt()

        if (outputLength <= 0) return ShortArray(0)

        return ShortArray(outputLength) { i ->
            val srcPosition = i * ratio
            val srcIndex = srcPosition.toInt()
            val fraction = srcPosition - srcIndex

            if (srcIndex >= input.size - 1) {
                // Last sample - no interpolation needed
                input[input.size - 1]
            } else {
                // Linear interpolation between two samples
                val sample1 = input[srcIndex].toInt()
                val sample2 = input[srcIndex + 1].toInt()
                val interpolated = sample1 + (sample2 - sample1) * fraction
                interpolated.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
