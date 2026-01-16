package com.podcapture.transcription

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer

class VoskTranscriptionService(
    private val context: Context,
    private val modelManager: VoskModelManager,
    private val audioExtractor: AudioExtractor
) : TranscriptionService {

    override val modelState: StateFlow<ModelState>
        get() = modelManager.modelState

    override fun isModelReady(): Boolean = modelManager.isModelReady()

    override suspend fun ensureModelReady(): Boolean = modelManager.ensureModelDownloaded()

    override fun release() = modelManager.release()

    override suspend fun transcribe(
        audioUri: Uri,
        startMs: Long,
        endMs: Long
    ): String = withContext(Dispatchers.Default) {
        // Get the model (downloads if needed)
        val model = modelManager.getModel()

        // Extract audio segment
        val segment = audioExtractor.extractSegment(audioUri, startMs, endMs)

        // Create recognizer
        val recognizer = Recognizer(model, segment.sampleRate.toFloat())

        try {
            // Process audio in larger chunks for better recognition
            val chunkSize = 8192 // bytes - larger chunks for better context
            var offset = 0
            val partialResults = StringBuilder()

            while (offset < segment.pcmData.size) {
                val end = minOf(offset + chunkSize, segment.pcmData.size)
                val chunk = segment.pcmData.copyOfRange(offset, end)

                // acceptWaveForm returns true if there's a result ready
                if (recognizer.acceptWaveForm(chunk, chunk.size)) {
                    // Get intermediate result and accumulate
                    val partialResult = recognizer.result
                    val partialText = parseTranscriptionResult(partialResult)
                    if (partialText.isNotEmpty() && !partialText.startsWith("[")) {
                        if (partialResults.isNotEmpty()) partialResults.append(" ")
                        partialResults.append(partialText)
                    }
                }
                offset = end
            }

            // Get final result
            val finalResult = recognizer.finalResult
            val finalText = parseTranscriptionResult(finalResult)

            // Combine partial and final results
            val combinedResult = if (partialResults.isNotEmpty() && finalText.isNotEmpty() && !finalText.startsWith("[")) {
                "$partialResults $finalText".trim()
            } else if (partialResults.isNotEmpty()) {
                partialResults.toString()
            } else {
                finalText
            }

            combinedResult.ifEmpty { "[No speech detected in this segment]" }

        } finally {
            recognizer.close()
        }
    }

    private fun parseTranscriptionResult(jsonResult: String): String {
        return try {
            val json = JSONObject(jsonResult)
            json.optString("text", "").trim().ifEmpty {
                "[No speech detected in this segment]"
            }
        } catch (e: Exception) {
            "[Transcription error: ${e.message}]"
        }
    }
}
