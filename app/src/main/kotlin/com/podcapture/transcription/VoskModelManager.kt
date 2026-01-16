package com.podcapture.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

sealed class ModelState {
    data object NotDownloaded : ModelState()
    data class Downloading(val progress: Int) : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

class VoskModelManager(private val context: Context) {

    companion object {
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val MODEL_DIR_NAME = "vosk-model"
    }

    private var model: Model? = null
    private val modelDir = File(context.filesDir, MODEL_DIR_NAME)

    private val _modelState = MutableStateFlow<ModelState>(
        if (isModelReady()) ModelState.Ready else ModelState.NotDownloaded
    )
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    fun isModelReady(): Boolean {
        return modelDir.exists() &&
                modelDir.listFiles()?.isNotEmpty() == true &&
                File(modelDir, "am").exists()
    }

    suspend fun ensureModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _modelState.value = ModelState.Ready
            return@withContext true
        }

        try {
            _modelState.value = ModelState.Downloading(0)
            downloadAndExtractModel()
            _modelState.value = ModelState.Ready
            true
        } catch (e: Exception) {
            _modelState.value = ModelState.Error(e.message ?: "Download failed")
            false
        }
    }

    suspend fun getModel(): Model = withContext(Dispatchers.IO) {
        model?.let { return@withContext it }

        if (!isModelReady()) {
            throw IllegalStateException("Model not downloaded. Call ensureModelDownloaded() first.")
        }

        Model(modelDir.absolutePath).also { model = it }
    }

    private suspend fun downloadAndExtractModel() = withContext(Dispatchers.IO) {
        // Clean up any partial downloads
        if (modelDir.exists()) {
            modelDir.deleteRecursively()
        }
        modelDir.mkdirs()

        val tempZipFile = File(context.cacheDir, "vosk-model.zip")

        try {
            // Download the zip file
            URL(MODEL_URL).openStream().use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    val contentLength = 41_000_000L // Approximate size

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 99)
                        _modelState.value = ModelState.Downloading(progress)
                    }
                }
            }

            _modelState.value = ModelState.Downloading(99)

            // Extract the zip file
            ZipInputStream(tempZipFile.inputStream()).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    // Remove the top-level directory from the path
                    val relativePath = entry.name.substringAfter("/", "")
                    if (relativePath.isNotEmpty()) {
                        val entryFile = File(modelDir, relativePath)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            FileOutputStream(entryFile).use { output ->
                                zipStream.copyTo(output)
                            }
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }

        } finally {
            tempZipFile.delete()
        }
    }

    fun release() {
        model?.close()
        model = null
    }
}
