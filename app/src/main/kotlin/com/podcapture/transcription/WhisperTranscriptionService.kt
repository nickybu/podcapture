package com.podcapture.transcription

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Whisper-based transcription service using Sherpa-ONNX.
 *
 * Provides offline speech-to-text transcription using the Whisper tiny.en model.
 */
class WhisperTranscriptionService(
    private val context: Context,
    private val modelManager: WhisperModelManager,
    private val audioExtractor: AudioExtractor
) : TranscriptionService {

    companion object {
        private const val TAG = "WhisperTranscription"
        // Whisper processes audio in ~30 second chunks
        // Use 28 seconds to leave some buffer and avoid edge issues
        private const val CHUNK_DURATION_SAMPLES = 28 * 16000  // 28 seconds at 16kHz
    }

    override val modelState: StateFlow<ModelState>
        get() = modelManager.modelState

    override fun isModelReady(): Boolean = modelManager.isModelReady()

    override suspend fun ensureModelReady(): Boolean = modelManager.ensureModelDownloaded()

    override fun release() {
        modelManager.release()
    }

    override suspend fun transcribe(
        audioUri: Uri,
        startMs: Long,
        endMs: Long
    ): String = withContext(Dispatchers.Default) {
        try {
            if (!isModelReady()) {
                return@withContext "[Model not ready - download required]"
            }

            val recognizer = modelManager.getRecognizer()
            if (recognizer == null) {
                Log.e(TAG, "Recognizer is null despite model being ready")
                return@withContext "[Transcription error: recognizer not initialized]"
            }

            // Extract audio segment (returns 16-bit PCM at 16kHz mono)
            val segment = audioExtractor.extractSegment(audioUri, startMs, endMs)

            // Convert 16-bit PCM bytes to float samples normalized to [-1, 1]
            val floatSamples = pcmBytesToFloatArray(segment.pcmData)

            if (floatSamples.isEmpty()) {
                return@withContext "[No audio data to transcribe]"
            }

            val totalDurationSec = floatSamples.size / segment.sampleRate
            Log.d(TAG, "Transcribing ${floatSamples.size} samples (${totalDurationSec}s) at ${segment.sampleRate}Hz")

            // Process audio in chunks to handle Whisper's ~30 second context window
            val transcriptionParts = mutableListOf<String>()
            var offset = 0
            var chunkIndex = 0

            while (offset < floatSamples.size) {
                val chunkEnd = minOf(offset + CHUNK_DURATION_SAMPLES, floatSamples.size)
                val chunk = floatSamples.copyOfRange(offset, chunkEnd)

                Log.d(TAG, "Processing chunk ${chunkIndex + 1}: samples $offset-$chunkEnd (${chunk.size / 16000}s)")

                val stream = recognizer.createStream()
                stream.acceptWaveform(chunk, segment.sampleRate)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                stream.release()

                val chunkText = result.text.trim()
                if (chunkText.isNotEmpty()) {
                    transcriptionParts.add(chunkText)
                    Log.d(TAG, "Chunk ${chunkIndex + 1} result: $chunkText")
                }

                offset = chunkEnd
                chunkIndex++
            }

            val fullText = transcriptionParts.joinToString(" ").trim()
            Log.d(TAG, "Full transcription (${transcriptionParts.size} chunks): $fullText")

            fullText.ifEmpty { "[No speech detected]" }

        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            "[Transcription error: ${e.message}]"
        }
    }

    /**
     * Converts 16-bit PCM byte array to float array normalized to [-1.0, 1.0].
     */
    private fun pcmBytesToFloatArray(pcmData: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcmData)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val samples = ShortArray(shortBuffer.remaining())
        shortBuffer.get(samples)

        // Convert to float [-1.0, 1.0]
        return FloatArray(samples.size) { i ->
            samples[i].toFloat() / Short.MAX_VALUE
        }
    }
}
