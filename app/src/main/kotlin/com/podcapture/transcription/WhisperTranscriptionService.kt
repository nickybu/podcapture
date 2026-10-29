package com.podcapture.transcription

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

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

            // Mono 16 kHz floats in [-1, 1], consumed by the recognizer without further conversion.
            val segment = audioExtractor.extractSegment(audioUri, startMs, endMs)
            val samples = segment.samples
            val sampleCount = segment.sampleCount

            if (sampleCount == 0) {
                return@withContext "[No audio data to transcribe]"
            }

            Log.d(TAG, "Transcribing $sampleCount samples (${sampleCount / segment.sampleRate}s) at ${segment.sampleRate}Hz")

            // Process audio in chunks to handle Whisper's ~30 second context window.
            // Sherpa's acceptWaveform only takes a whole FloatArray (and copies it into native
            // memory), so full-length chunks share one scratch array and only the final partial
            // chunk gets its own exact-size copy.
            val transcriptionParts = mutableListOf<String>()
            var scratch: FloatArray? = null
            var offset = 0
            var chunkIndex = 0

            while (offset < sampleCount) {
                val chunkEnd = minOf(offset + CHUNK_DURATION_SAMPLES, sampleCount)
                val chunkLength = chunkEnd - offset
                val chunk = when {
                    offset == 0 && chunkLength == samples.size -> samples
                    chunkLength == CHUNK_DURATION_SAMPLES -> {
                        val buffer = scratch ?: FloatArray(CHUNK_DURATION_SAMPLES).also { scratch = it }
                        System.arraycopy(samples, offset, buffer, 0, chunkLength)
                        buffer
                    }
                    else -> samples.copyOfRange(offset, chunkEnd)
                }

                Log.d(TAG, "Processing chunk ${chunkIndex + 1}: samples $offset-$chunkEnd (${chunkLength / 16000}s)")

                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(chunk, segment.sampleRate)
                    recognizer.decode(stream)
                    val chunkText = recognizer.getResult(stream).text.trim()
                    if (chunkText.isNotEmpty()) {
                        transcriptionParts.add(chunkText)
                        Log.d(TAG, "Chunk ${chunkIndex + 1} result: $chunkText")
                    }
                } finally {
                    stream.release()
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
}
