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
     * Cheap enough to call from the main thread.
     */
    fun isModelReady(): Boolean

    /**
     * Ensures the model is downloaded and ready.
     * Returns true if successful, false otherwise. Concurrent callers share one download.
     */
    suspend fun ensureModelReady(): Boolean

    /**
     * Transcribes an audio segment from the given URI.
     *
     * @param audioUri URI of the audio file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @return The transcribed text (possibly a "[No speech detected]" marker), or a failure
     *   carrying a [TranscriptionException] describing why nothing could be transcribed.
     */
    suspend fun transcribeResult(audioUri: Uri, startMs: Long, endMs: Long): Result<String>

    /**
     * Transcribes an audio segment from the given URI.
     *
     * @param audioUri URI of the audio file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @return Transcribed text, or error message in brackets
     */
    @Deprecated(
        "Failures come back as bracketed text that callers then save as a normal capture. " +
            "Use transcribeResult. Kept for PlayerViewModel and EpisodePlayerViewModel until a " +
            "follow-up routes their captures through CaptureManager.",
        ReplaceWith("transcribeResult(audioUri, startMs, endMs)")
    )
    suspend fun transcribe(audioUri: Uri, startMs: Long, endMs: Long): String

    /**
     * Release resources when no longer needed.
     */
    fun release()
}

/** Why a transcription produced no text. [message] is suitable for showing to the user. */
sealed class TranscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotReady : TranscriptionException("Speech model not ready - download required")
    class NoAudio : TranscriptionException("No audio data to transcribe")
    class Failed(cause: Throwable) : TranscriptionException("Transcription error: ${cause.message}", cause)
}
