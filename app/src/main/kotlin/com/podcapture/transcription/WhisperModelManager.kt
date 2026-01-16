package com.podcapture.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Manages the Whisper model download and initialization.
 *
 * To use Whisper transcription, you need to:
 * 1. Build whisper.cpp for Android using NDK (see https://github.com/ggml-org/whisper.cpp)
 * 2. Place the native libraries in jniLibs folder
 * 3. Uncomment the native library loading in this class
 *
 * Model download URL for tiny.en model (~75MB):
 * https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
 */
class WhisperModelManager(private val context: Context) {

    companion object {
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
        private const val MODEL_FILE_NAME = "ggml-tiny.en.bin"
        private const val MODEL_SIZE_BYTES = 75_000_000L // Approximately 75MB

        // Native library loading - uncomment when native libs are available
        // init {
        //     System.loadLibrary("whisper")
        // }
    }

    private val modelFile = File(context.filesDir, MODEL_FILE_NAME)

    private val _modelState = MutableStateFlow<ModelState>(
        if (isModelReady()) ModelState.Ready else ModelState.NotDownloaded
    )
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    // Native handle for the loaded model
    private var nativeModelHandle: Long = 0L

    fun isModelReady(): Boolean {
        // Check both model file exists and native library is available
        return modelFile.exists() &&
                modelFile.length() > MODEL_SIZE_BYTES * 0.9 // Allow 10% variance
        // && isNativeLibraryLoaded() // Uncomment when native libs are available
    }

    suspend fun ensureModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _modelState.value = ModelState.Ready
            return@withContext true
        }

        // Check if native library is available
        // if (!isNativeLibraryLoaded()) {
        //     _modelState.value = ModelState.Error("Whisper native library not found. Please add whisper.cpp to jniLibs.")
        //     return@withContext false
        // }

        try {
            _modelState.value = ModelState.Downloading(0)
            downloadModel()
            _modelState.value = ModelState.Ready
            true
        } catch (e: Exception) {
            _modelState.value = ModelState.Error(e.message ?: "Download failed")
            false
        }
    }

    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        // Delete partial downloads
        if (modelFile.exists()) {
            modelFile.delete()
        }

        URL(MODEL_URL).openStream().use { input ->
            FileOutputStream(modelFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = ((totalRead * 100) / MODEL_SIZE_BYTES).toInt().coerceIn(0, 99)
                    _modelState.value = ModelState.Downloading(progress)
                }
            }
        }
    }

    fun getModelPath(): String = modelFile.absolutePath

    /**
     * Load the model into native memory. Call before transcription.
     * Returns the native handle, or 0 on failure.
     */
    fun loadModel(): Long {
        if (nativeModelHandle != 0L) {
            return nativeModelHandle
        }

        // Uncomment when native library is available:
        // nativeModelHandle = nativeLoadModel(modelFile.absolutePath)
        // return nativeModelHandle

        return 0L // Return 0 when native library not available
    }

    fun release() {
        if (nativeModelHandle != 0L) {
            // Uncomment when native library is available:
            // nativeFreeModel(nativeModelHandle)
            nativeModelHandle = 0L
        }
    }

    // Native method declarations - uncomment when native library is available
    // private external fun nativeLoadModel(modelPath: String): Long
    // private external fun nativeFreeModel(handle: Long)
    // private external fun isNativeLibraryLoaded(): Boolean
}
