package com.podcapture.transcription

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for speech-to-text transcription services.
 * Implementations can use different engines (Vosk, Whisper, etc.)
 */
interface TranscriptionService {
    /**
     * Current state of the model download/initialization.
     */
    val modelState: StateFlow<ModelState>

    /**
     * Returns true if the transcription model is ready to use.
     */
    fun isModelReady(): Boolean

    /**
     * Ensures the model is downloaded and ready.
     * Returns true if successful, false otherwise.
     */
    suspend fun ensureModelReady(): Boolean

    /**
     * Transcribes an audio segment from the given URI.
     *
     * @param audioUri URI of the audio file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @return Transcribed text, or error message in brackets
     */
    suspend fun transcribe(audioUri: Uri, startMs: Long, endMs: Long): String

    /**
     * Release resources when no longer needed.
     */
    fun release()
}

/**
 * Available transcription engines.
 */
enum class TranscriptionEngine {
    VOSK,
    WHISPER
}
