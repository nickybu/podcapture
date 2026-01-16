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

            // Seek to start position
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

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

                        if (sampleSize < 0 || extractor.sampleTime / 1000 > endMs) {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                extractor.sampleTime, 0
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
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputStream.write(chunk)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            // Convert to mono 16kHz if needed for Vosk
            val rawPcm = outputStream.toByteArray()
            val convertedPcm = convertToMono16kHz(rawPcm, sampleRate, channelCount)

            AudioSegment(convertedPcm, 16000)

        } finally {
            decoder?.stop()
            decoder?.release()
            extractor.release()
        }
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

        // Resample to 16kHz if needed
        val outputSamples = if (inputSampleRate != 16000) {
            val ratio = inputSampleRate.toFloat() / 16000f
            val outputLength = (monoSamples.size / ratio).toInt()
            ShortArray(outputLength) { i ->
                val srcIndex = (i * ratio).toInt().coerceIn(0, monoSamples.size - 1)
                monoSamples[srcIndex]
            }
        } else {
            monoSamples
        }

        // Convert back to bytes
        val outputBuffer = ByteBuffer.allocate(outputSamples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        outputSamples.forEach { outputBuffer.putShort(it) }

        return outputBuffer.array()
    }
}
