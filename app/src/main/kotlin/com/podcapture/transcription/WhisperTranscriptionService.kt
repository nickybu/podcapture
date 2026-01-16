package com.podcapture.transcription

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Whisper-based transcription service using whisper.cpp.
 *
 * To enable Whisper transcription:
 * 1. Build whisper.cpp for Android (see https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android)
 * 2. Copy the .so files to app/src/main/jniLibs/{abi}/libwhisper.so
 * 3. Uncomment the native method calls in this class and WhisperModelManager
 * 4. Update AppModule to use WhisperTranscriptionService
 *
 * The tiny.en model provides:
 * - 5-8% Word Error Rate (vs Vosk's ~15-20%)
 * - ~75MB model size
 * - Good performance on modern phones
 */
class WhisperTranscriptionService(
    private val context: Context,
    private val modelManager: WhisperModelManager,
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
        // Check if native library is available
        val modelHandle = modelManager.loadModel()
        if (modelHandle == 0L) {
            return@withContext "[Whisper native library not available. Please build whisper.cpp for Android.]"
        }

        try {
            // Extract audio segment - Whisper also needs 16kHz mono PCM
            val segment = audioExtractor.extractSegment(audioUri, startMs, endMs)

            // Call native transcription
            // Uncomment when native library is available:
            // val result = nativeTranscribe(modelHandle, segment.pcmData, segment.sampleRate)
            // return@withContext result.ifEmpty { "[No speech detected]" }

            // Placeholder until native library is available
            "[Whisper transcription not yet available. Build whisper.cpp for Android to enable.]"

        } catch (e: Exception) {
            "[Transcription error: ${e.message}]"
        }
    }

    // Native method declarations - uncomment when native library is available
    // private external fun nativeTranscribe(modelHandle: Long, audioData: ByteArray, sampleRate: Int): String
}
