package com.podcapture.transcription

/**
 * Represents the state of a transcription model download/initialization.
 */
sealed class ModelState {
    /** Model has not been downloaded yet. */
    data object NotDownloaded : ModelState()

    /** Model is currently being downloaded. */
    data class Downloading(val progress: Int) : ModelState()

    /** Model is downloaded and ready to use. */
    data object Ready : ModelState()

    /** An error occurred during download or initialization. */
    data class Error(val message: String) : ModelState()
}
