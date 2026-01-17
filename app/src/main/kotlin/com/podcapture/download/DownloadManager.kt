package com.podcapture.download

import android.content.Context
import android.content.Intent
import android.os.Build
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.data.repository.PodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Download progress information for an episode.
 */
data class DownloadProgress(
    val episodeId: Long,
    val state: EpisodeDownloadState,
    val percent: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val episodeTitle: String = "",
    val podcastTitle: String = ""
)

/**
 * Centralized download manager that handles episode downloads in the background.
 * Uses a foreground service to ensure downloads continue when the user leaves the screen.
 */
class DownloadManager(
    private val context: Context,
    private val podcastRepository: PodcastRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Map of episodeId to download progress
    private val _downloadStates = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, DownloadProgress>> = _downloadStates.asStateFlow()

    // Queue of pending downloads
    private val downloadQueue = mutableListOf<DownloadRequest>()
    private var isServiceRunning = false

    data class DownloadRequest(
        val episode: CachedEpisode,
        val podcastTitle: String
    )

    /**
     * Start downloading an episode. The download will continue in the background
     * even if the user leaves the screen.
     */
    fun downloadEpisode(episode: CachedEpisode, podcastTitle: String) {
        // Check if already downloading or downloaded
        val currentState = _downloadStates.value[episode.id]
        if (currentState?.state == EpisodeDownloadState.Downloading) {
            return // Already downloading
        }

        // Check if already downloaded
        val localPath = podcastRepository.getLocalEpisodePath(episode.id)
        if (localPath != null) {
            _downloadStates.update { states ->
                states + (episode.id to DownloadProgress(
                    episodeId = episode.id,
                    state = EpisodeDownloadState.Downloaded,
                    percent = 100,
                    episodeTitle = episode.title,
                    podcastTitle = podcastTitle
                ))
            }
            return
        }

        // Add to queue
        val request = DownloadRequest(episode, podcastTitle)
        downloadQueue.add(request)

        // Update state to queued/downloading
        _downloadStates.update { states ->
            states + (episode.id to DownloadProgress(
                episodeId = episode.id,
                state = EpisodeDownloadState.Downloading,
                percent = 0,
                totalBytes = episode.audioSize,
                episodeTitle = episode.title,
                podcastTitle = podcastTitle
            ))
        }

        // Start service if not running
        startServiceIfNeeded()

        // Process queue
        processNextDownload()
    }

    /**
     * Cancel a download in progress.
     */
    fun cancelDownload(episodeId: Long) {
        // Remove from queue if pending
        downloadQueue.removeAll { it.episode.id == episodeId }

        // Update state
        _downloadStates.update { states ->
            states - episodeId
        }

        // Stop service if no more downloads
        if (downloadQueue.isEmpty() && _downloadStates.value.none { it.value.state == EpisodeDownloadState.Downloading }) {
            stopService()
        }
    }

    /**
     * Get the current download state for an episode.
     */
    fun getDownloadState(episodeId: Long): DownloadProgress? {
        return _downloadStates.value[episodeId]
    }

    /**
     * Check if an episode is downloaded.
     */
    fun isDownloaded(episodeId: Long): Boolean {
        val localPath = podcastRepository.getLocalEpisodePath(episodeId)
        return localPath != null
    }

    private fun startServiceIfNeeded() {
        if (!isServiceRunning) {
            val intent = Intent(context, EpisodeDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isServiceRunning = true
        }
    }

    private fun stopService() {
        val intent = Intent(context, EpisodeDownloadService::class.java)
        context.stopService(intent)
        isServiceRunning = false
    }

    private fun processNextDownload() {
        val request = downloadQueue.firstOrNull() ?: run {
            stopService()
            return
        }

        scope.launch {
            try {
                // Cache episode in database first
                podcastRepository.cacheEpisode(request.episode)

                // Download with progress updates
                podcastRepository.downloadEpisode(request.episode) { progress ->
                    _downloadStates.update { states ->
                        states + (request.episode.id to DownloadProgress(
                            episodeId = request.episode.id,
                            state = EpisodeDownloadState.Downloading,
                            percent = progress.percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            episodeTitle = request.episode.title,
                            podcastTitle = request.podcastTitle
                        ))
                    }

                    // Update notification
                    updateServiceNotification()
                }.fold(
                    onSuccess = { _ ->
                        _downloadStates.update { states ->
                            states + (request.episode.id to DownloadProgress(
                                episodeId = request.episode.id,
                                state = EpisodeDownloadState.Downloaded,
                                percent = 100,
                                episodeTitle = request.episode.title,
                                podcastTitle = request.podcastTitle
                            ))
                        }
                    },
                    onFailure = { _ ->
                        _downloadStates.update { states ->
                            states + (request.episode.id to DownloadProgress(
                                episodeId = request.episode.id,
                                state = EpisodeDownloadState.Error,
                                percent = 0,
                                episodeTitle = request.episode.title,
                                podcastTitle = request.podcastTitle
                            ))
                        }
                    }
                )
            } finally {
                // Remove from queue
                downloadQueue.removeAll { it.episode.id == request.episode.id }

                // Process next download or stop service
                if (downloadQueue.isNotEmpty()) {
                    processNextDownload()
                } else {
                    stopService()
                }
            }
        }
    }

    private fun updateServiceNotification() {
        // Send broadcast to update service notification
        val intent = Intent(EpisodeDownloadService.ACTION_UPDATE_NOTIFICATION)
        context.sendBroadcast(intent)
    }

    /**
     * Get currently downloading episode info for notification.
     */
    fun getCurrentDownloadInfo(): DownloadProgress? {
        return _downloadStates.value.values.firstOrNull {
            it.state == EpisodeDownloadState.Downloading
        }
    }

    /**
     * Get count of pending downloads.
     */
    fun getPendingCount(): Int {
        return downloadQueue.size
    }
}
