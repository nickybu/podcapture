package com.podcapture.transcription

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the Whisper model download and initialization.
 *
 * Uses Sherpa-ONNX for offline speech recognition with the Whisper tiny.en model.
 */
class WhisperModelManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperModelManager"

        // Whisper tiny.en ONNX model files from Hugging Face
        private const val BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/"
        private const val MODEL_NAME = "sherpa-onnx-whisper-tiny.en"

        // Model files needed
        private const val ENCODER_FILE = "tiny.en-encoder.int8.onnx"
        private const val DECODER_FILE = "tiny.en-decoder.int8.onnx"
        private const val TOKENS_FILE = "tiny.en-tokens.txt"

        // Approximate total size of all model files (~120MB for int8 versions)
        private const val TOTAL_MODEL_SIZE = 120_000_000L
    }

    private val modelDir = File(context.filesDir, MODEL_NAME)
    private val recognizerMutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    // Owns the model download so it outlives (and is shared between) the callers awaiting it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadFlight = SingleFlight<Boolean>(scope)

    // Cached result of the model-file check so isModelReady() is cheap on the main thread.
    // null = not checked yet. Updated after every download attempt.
    private val modelReadyCache = AtomicReference<Boolean?>(null)

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    init {
        // The constructor runs wherever Koin first resolves this singleton (often the main
        // thread), so check the model files in the background instead of blocking it.
        scope.launch {
            if (isModelReady()) {
                _modelState.compareAndSet(ModelState.NotDownloaded, ModelState.Ready)
            }
        }
    }

    /**
     * Returns whether the model files are present. Hits the disk only on the first call; after
     * that (and after each download attempt) the cached answer is returned.
     */
    fun isModelReady(): Boolean {
        modelReadyCache.get()?.let { return it }
        val ready = modelFilesExist()
        // Don't overwrite a value a concurrent download stored while we were checking.
        modelReadyCache.compareAndSet(null, ready)
        return modelReadyCache.get() ?: ready
    }

    private fun modelFilesExist(): Boolean {
        return modelDir.exists() &&
                File(modelDir, ENCODER_FILE).exists() &&
                File(modelDir, DECODER_FILE).exists() &&
                File(modelDir, TOKENS_FILE).exists()
    }

    /**
     * Downloads the model if needed and initialises the recognizer. Concurrent callers (e.g. a
     * headset capture and an on-screen capture) share one in-flight attempt and its result.
     */
    suspend fun ensureModelDownloaded(): Boolean = downloadFlight.run { downloadAndInitialize() }

    private suspend fun downloadAndInitialize(): Boolean {
        // Runs on Dispatchers.IO via [scope]; checks the disk rather than the cache.
        if (modelFilesExist()) {
            modelReadyCache.set(true)
            initializeRecognizer()
            _modelState.value = ModelState.Ready
            return true
        }

        return try {
            _modelState.value = ModelState.Downloading(0)
            downloadModelFiles()
            initializeRecognizer()
            modelReadyCache.set(true)
            _modelState.value = ModelState.Ready
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download or initialize model", e)
            _modelState.value = ModelState.Error(e.message ?: "Download failed")
            // Clean up the partial download. Safe: this is the only attempt in flight, so no
            // other caller can be writing to (or have just completed) the model directory.
            modelDir.deleteRecursively()
            modelReadyCache.set(false)
            false
        }
    }

    /**
     * Gets the recognizer instance, initializing it if needed.
     * Thread-safe access with mutex.
     */
    suspend fun getRecognizer(): OfflineRecognizer? = recognizerMutex.withLock {
        if (recognizer == null && isModelReady()) {
            initializeRecognizerInternal()
        }
        recognizer
    }

    private suspend fun initializeRecognizer() = recognizerMutex.withLock {
        initializeRecognizerInternal()
    }

    private fun initializeRecognizerInternal() {
        if (recognizer != null) return

        try {
            Log.d(TAG, "Initializing Sherpa-ONNX Whisper recognizer...")

            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = getEncoderPath(),
                        decoder = getDecoderPath(),
                        language = "en",
                        task = "transcribe",
                        tailPaddings = 1000
                    ),
                    tokens = getTokensPath(),
                    modelType = "whisper",
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                    debug = false,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )

            recognizer = OfflineRecognizer(config = config)
            Log.d(TAG, "Sherpa-ONNX Whisper recognizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recognizer", e)
            throw e
        }
    }

    fun getEncoderPath(): String = File(modelDir, ENCODER_FILE).absolutePath
    fun getDecoderPath(): String = File(modelDir, DECODER_FILE).absolutePath
    fun getTokensPath(): String = File(modelDir, TOKENS_FILE).absolutePath

    private suspend fun downloadModelFiles() = withContext(Dispatchers.IO) {
        // Create model directory
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val files = listOf(
            ENCODER_FILE to 12_000_000L,   // ~12MB
            DECODER_FILE to 105_000_000L,  // ~105MB
            TOKENS_FILE to 100_000L        // ~100KB
        )

        var totalDownloaded = 0L

        for ((fileName, estimatedSize) in files) {
            val targetFile = File(modelDir, fileName)
            if (targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "File already exists: $fileName")
                totalDownloaded += estimatedSize
                continue
            }

            // Hugging Face URL format: BASE_URL + filename
            val url = "$BASE_URL$fileName"
            downloadFile(url, targetFile) { bytesDownloaded ->
                val progress = ((totalDownloaded + bytesDownloaded) * 100 / TOTAL_MODEL_SIZE)
                    .toInt().coerceIn(0, 99)
                _modelState.value = ModelState.Downloading(progress)
            }
            totalDownloaded += estimatedSize
        }
    }

    private suspend fun downloadFile(
        urlString: String,
        targetFile: File,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        try {
            Log.d(TAG, "Downloading: $urlString")

            // Follow redirects manually since GitHub releases redirect
            var connection = URL(urlString).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            // Handle redirects manually for cross-protocol redirects
            var redirectCount = 0
            while (connection.responseCode in 300..399 && redirectCount < 5) {
                val redirectUrl = connection.getHeaderField("Location")
                Log.d(TAG, "Following redirect to: $redirectUrl")
                connection.disconnect()
                connection = URL(redirectUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                redirectCount++
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        onProgress(totalBytesRead)
                    }
                }
            }
            connection.disconnect()

            // Move temp file to final location
            if (!tempFile.renameTo(targetFile)) {
                // If rename fails, try copy and delete
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            Log.d(TAG, "Downloaded successfully: ${targetFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            tempFile.delete()
            throw e
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        Log.d(TAG, "Whisper recognizer released")
    }
}
