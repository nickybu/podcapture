package com.podcapture.youtube

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Sealed class representing the state of a YouTube download.
 */
sealed class YouTubeDownloadState {
    data object Idle : YouTubeDownloadState()
    data object Initializing : YouTubeDownloadState()
    data class Downloading(
        val percent: Int,
        val title: String,
        val eta: String?
    ) : YouTubeDownloadState()
    data class Completed(
        val audioFileId: String,
        val title: String
    ) : YouTubeDownloadState()
    data class Error(val message: String) : YouTubeDownloadState()
    data object Cancelled : YouTubeDownloadState()
    data class NeedsCaptcha(val url: String) : YouTubeDownloadState()
}

/**
 * Data class representing a download in the queue.
 */
data class YouTubeDownloadRequest(
    val url: String,
    val videoId: String,
    val requestedAt: Long = System.currentTimeMillis()
)

/**
 * Custom downloader for NewPipe Extractor using OkHttp.
 */
class OkHttpDownloader(
    private val client: OkHttpClient,
    private val cookieManager: YouTubeCookieManager? = null
) : Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        // POST requests require a body in OkHttp, even if empty
        val body = if (request.httpMethod() == "POST") {
            request.dataToSend()?.toRequestBody() ?: "".toRequestBody()
        } else {
            null
        }

        val requestBuilder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)

        // Add browser-like headers
        requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        requestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        requestBuilder.addHeader("Accept-Language", "en-US,en;q=0.9")
        requestBuilder.addHeader("Sec-Fetch-Dest", "document")
        requestBuilder.addHeader("Sec-Fetch-Mode", "navigate")
        requestBuilder.addHeader("Sec-Fetch-Site", "none")
        requestBuilder.addHeader("Sec-Fetch-User", "?1")
        requestBuilder.addHeader("Upgrade-Insecure-Requests", "1")

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Add cookies if available
        val cookies = cookieManager?.getCookies()
        if (cookies != null) {
            Log.d("OkHttpDownloader", "Adding ${cookies.length} chars of cookies to request")
            requestBuilder.addHeader("Cookie", cookies)
        } else {
            Log.d("OkHttpDownloader", "No cookies available")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("Rate limited", request.url())
        }

        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.forEach { (name, value) ->
            responseHeaders[name] = listOf(value)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            response.body?.string(),
            response.request.url.toString()
        )
    }
}

/**
 * Manages YouTube audio downloads using NewPipe Extractor.
 */
class YouTubeDownloadManager(
    private val context: Context,
    private val cookieManager: YouTubeCookieManager
) {
    private val _downloadState = MutableStateFlow<YouTubeDownloadState>(YouTubeDownloadState.Idle)
    val downloadState: StateFlow<YouTubeDownloadState> = _downloadState.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var currentRequest: YouTubeDownloadRequest? = null
    private var isCancelled = false
    private var pendingRetryUrl: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Initialize NewPipe Extractor.
     */
    suspend fun initialize(forceReinit: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (_isInitialized.value && !forceReinit) {
            return@withContext Result.success(Unit)
        }

        try {
            Log.d(TAG, "Initializing NewPipe Extractor...")
            NewPipe.init(OkHttpDownloader(httpClient, cookieManager), Localization.DEFAULT)
            _isInitialized.value = true
            Log.d(TAG, "NewPipe Extractor initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NewPipe Extractor", e)
            Result.failure(e)
        }
    }

    /**
     * Called after CAPTCHA is solved to retry the download.
     */
    fun onCaptchaSolved() {
        val url = pendingRetryUrl
        if (url != null) {
            pendingRetryUrl = null
            // Reinitialize with new cookies and retry
            _isInitialized.value = false
            startDownload(url)
        } else {
            _downloadState.value = YouTubeDownloadState.Idle
        }
    }

    /**
     * Called if CAPTCHA was cancelled.
     */
    fun onCaptchaCancelled() {
        pendingRetryUrl = null
        _downloadState.value = YouTubeDownloadState.Error("Verification cancelled")
    }

    /**
     * Start downloading audio from a YouTube URL.
     */
    fun startDownload(url: String): Result<Unit> {
        val videoId = YouTubeUrlValidator.extractVideoId(url)
            ?: return Result.failure(IllegalArgumentException("Invalid YouTube URL"))

        if (_downloadState.value !is YouTubeDownloadState.Idle &&
            _downloadState.value !is YouTubeDownloadState.Completed &&
            _downloadState.value !is YouTubeDownloadState.Error &&
            _downloadState.value !is YouTubeDownloadState.Cancelled) {
            return Result.failure(IllegalStateException("A download is already in progress"))
        }

        currentRequest = YouTubeDownloadRequest(url, videoId)
        isCancelled = false

        // Start the foreground service
        val serviceIntent = Intent(context, YouTubeDownloadService::class.java).apply {
            putExtra(YouTubeDownloadService.EXTRA_URL, url)
            putExtra(YouTubeDownloadService.EXTRA_VIDEO_ID, videoId)
        }
        context.startForegroundService(serviceIntent)

        return Result.success(Unit)
    }

    /**
     * Execute the actual download. Called by the service.
     */
    suspend fun executeDownload(
        url: String,
        videoId: String,
        onComplete: (Result<Pair<String, String>>) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Ensure extractor is initialized
            if (!_isInitialized.value) {
                _downloadState.value = YouTubeDownloadState.Initializing
                val initResult = initialize()
                if (initResult.isFailure) {
                    val error = "Failed to initialize: ${initResult.exceptionOrNull()?.message}"
                    _downloadState.value = YouTubeDownloadState.Error(error)
                    onComplete(Result.failure(Exception(error)))
                    return@withContext
                }
            }

            if (isCancelled) {
                _downloadState.value = YouTubeDownloadState.Cancelled
                onComplete(Result.failure(Exception("Download cancelled")))
                return@withContext
            }

            _downloadState.value = YouTubeDownloadState.Downloading(0, "Fetching video info...", null)

            // Get stream info from YouTube
            val normalizedUrl = YouTubeUrlValidator.normalizeUrl(url)
                ?: throw IllegalArgumentException("Invalid URL")

            Log.d(TAG, "Fetching stream info for: $normalizedUrl")
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, normalizedUrl)
            val videoTitle = streamInfo.name.take(100)

            Log.d(TAG, "Video title: $videoTitle")

            if (isCancelled) {
                _downloadState.value = YouTubeDownloadState.Cancelled
                onComplete(Result.failure(Exception("Download cancelled")))
                return@withContext
            }

            _downloadState.value = YouTubeDownloadState.Downloading(5, videoTitle, null)

            // Find the best audio stream
            val audioStreams = streamInfo.audioStreams
            if (audioStreams.isEmpty()) {
                throw Exception("No audio streams available for this video")
            }

            // Sort by bitrate and pick the best one (prefer m4a/mp4 for compatibility)
            val bestAudio = audioStreams
                .filter { it.format?.suffix in listOf("m4a", "mp4", "webm") }
                .maxByOrNull { it.averageBitrate }
                ?: audioStreams.maxByOrNull { it.averageBitrate }
                ?: throw Exception("No suitable audio stream found")

            Log.d(TAG, "Selected audio stream: ${bestAudio.format?.suffix}, ${bestAudio.averageBitrate}kbps")

            // Create output directory
            val outputDir = File(context.filesDir, "youtube")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Determine file extension
            val extension = bestAudio.format?.suffix ?: "m4a"
            val outputPath = File(outputDir, "youtube_${videoId}.$extension").absolutePath

            // Download the audio file
            _downloadState.value = YouTubeDownloadState.Downloading(10, videoTitle, null)

            downloadFile(bestAudio.content, outputPath, videoTitle)

            if (isCancelled) {
                File(outputPath).delete()
                _downloadState.value = YouTubeDownloadState.Cancelled
                onComplete(Result.failure(Exception("Download cancelled")))
                return@withContext
            }

            // Verify the file was created
            val outputFile = File(outputPath)
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw Exception("Download completed but file is empty or not found")
            }

            Log.d(TAG, "Download completed: $outputPath (${outputFile.length()} bytes)")
            onComplete(Result.success(Pair(outputPath, videoTitle)))

        } catch (e: ReCaptchaException) {
            Log.e(TAG, "ReCaptcha required", e)
            pendingRetryUrl = url
            _downloadState.value = YouTubeDownloadState.NeedsCaptcha(e.url ?: "https://www.youtube.com")
            onComplete(Result.failure(e))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)

            // Check if this is a captcha/sign-in error disguised as a regular exception
            val message = e.message ?: ""
            if (isCaptchaError(message)) {
                Log.d(TAG, "Detected captcha error in exception message")
                pendingRetryUrl = url
                _downloadState.value = YouTubeDownloadState.NeedsCaptcha("https://www.youtube.com")
                onComplete(Result.failure(e))
            } else {
                val errorMessage = parseError(e)
                _downloadState.value = YouTubeDownloadState.Error(errorMessage)
                onComplete(Result.failure(Exception(errorMessage)))
            }
        }
    }

    private suspend fun downloadFile(url: String, outputPath: String, title: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Download failed with code: ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()

            FileOutputStream(outputPath).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgressUpdate = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            return@withContext
                        }

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            // Map 10-100% for the download phase
                            val mappedProgress = 10 + (progress * 90 / 100)

                            if (mappedProgress > lastProgressUpdate) {
                                lastProgressUpdate = mappedProgress
                                _downloadState.value = YouTubeDownloadState.Downloading(
                                    percent = mappedProgress,
                                    title = title,
                                    eta = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when download completes successfully.
     */
    fun onDownloadCompleted(audioFileId: String, title: String) {
        _downloadState.value = YouTubeDownloadState.Completed(audioFileId, title)
        currentRequest = null
    }

    /**
     * Cancel the current download.
     */
    fun cancelDownload() {
        isCancelled = true
        currentRequest?.let { request ->
            // Clean up any partial files
            val outputDir = File(context.filesDir, "youtube")
            outputDir.listFiles()?.filter { it.name.startsWith("youtube_${request.videoId}") }?.forEach {
                it.delete()
            }
        }
        currentRequest = null
        _downloadState.value = YouTubeDownloadState.Cancelled

        // Stop the service
        context.stopService(Intent(context, YouTubeDownloadService::class.java))
    }

    /**
     * Reset the download state to idle.
     */
    fun resetState() {
        if (_downloadState.value is YouTubeDownloadState.Completed ||
            _downloadState.value is YouTubeDownloadState.Error ||
            _downloadState.value is YouTubeDownloadState.Cancelled) {
            _downloadState.value = YouTubeDownloadState.Idle
        }
    }

    /**
     * Get current download info for notifications.
     */
    fun getCurrentDownloadInfo(): DownloadNotificationInfo? {
        return when (val state = _downloadState.value) {
            is YouTubeDownloadState.Downloading -> DownloadNotificationInfo(
                title = state.title,
                percent = state.percent,
                eta = state.eta
            )
            is YouTubeDownloadState.Initializing -> DownloadNotificationInfo(
                title = "Initializing...",
                percent = 0,
                eta = null
            )
            else -> null
        }
    }

    private fun isCaptchaError(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("sign in") ||
               lowerMessage.contains("signin") ||
               lowerMessage.contains("login") ||
               lowerMessage.contains("captcha") ||
               lowerMessage.contains("bot") ||
               lowerMessage.contains("verify") ||
               lowerMessage.contains("confirm you're not") ||
               lowerMessage.contains("unusual traffic") ||
               lowerMessage.contains("automated")
    }

    private fun parseError(e: Exception): String {
        val message = e.message ?: return "Unknown error occurred"

        return when {
            e is ReCaptchaException ->
                "YouTube requires verification. Please try again later."

            message.contains("private", ignoreCase = true) ->
                "This video is private or unavailable"

            message.contains("unavailable", ignoreCase = true) ||
            message.contains("not available", ignoreCase = true) ->
                "This video is unavailable"

            message.contains("age", ignoreCase = true) ||
            message.contains("confirm your age", ignoreCase = true) ->
                "This video is age-restricted"

            message.contains("copyright", ignoreCase = true) ->
                "This video cannot be downloaded due to copyright restrictions"

            message.contains("network", ignoreCase = true) ||
            message.contains("connect", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "Network error. Check your connection and try again."

            message.contains("members-only", ignoreCase = true) ||
            message.contains("member", ignoreCase = true) ->
                "This video is members-only"

            message.contains("premium", ignoreCase = true) ->
                "This video requires YouTube Premium"

            message.contains("No audio streams", ignoreCase = true) ->
                "No audio available for this video"

            message.contains("live", ignoreCase = true) ->
                "Live streams cannot be downloaded"

            else -> "Download failed: ${message.take(100)}"
        }
    }

    /**
     * Data class for notification info.
     */
    data class DownloadNotificationInfo(
        val title: String,
        val percent: Int,
        val eta: String?
    )

    companion object {
        private const val TAG = "YouTubeDownloadManager"
    }
}
