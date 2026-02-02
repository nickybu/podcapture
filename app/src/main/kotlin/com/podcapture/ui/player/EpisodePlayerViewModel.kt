package com.podcapture.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.audio.AudioPlayerService
import com.podcapture.audio.PlaybackState
import com.podcapture.audio.PlayerState
import com.podcapture.data.db.PodcastDao
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.Capture
import com.podcapture.data.model.EpisodePlaybackHistory
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.download.DownloadManager
import com.podcapture.transcription.TranscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EpisodePlayerUiState(
    val episode: CachedEpisode? = null,
    val podcast: BookmarkedPodcast? = null,
    val playbackState: PlaybackState = PlaybackState(),
    val skipIntervalSeconds: Int = 10,
    val captureWindowSeconds: Int = 30,
    val captureWindowStep: Int = 5,
    val isLoading: Boolean = true,
    val showNotes: Boolean = false,
    val error: String? = null,
    // Capture state
    val captures: List<Capture> = emptyList(),
    val isCapturing: Boolean = false,
    val captureProgress: String? = null,
    val captureSuccess: String? = null,
    val captureError: String? = null,
    val localFilePath: String? = null,  // For checking if episode is downloaded
    // Download state
    val downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    val downloadProgress: Int = 0
)

class EpisodePlayerViewModel(
    private val episodeId: Long,
    private val podcastId: Long,
    private val audioPlayerService: AudioPlayerService,
    private val podcastRepository: PodcastRepository,
    private val podcastDao: PodcastDao,
    private val settingsDataStore: SettingsDataStore,
    private val captureRepository: CaptureRepository,
    private val transcriptionService: TranscriptionService,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodePlayerUiState())
    val uiState: StateFlow<EpisodePlayerUiState> = _uiState.asStateFlow()

    init {
        loadEpisode()
        observePlaybackState()
        observeSettings()
        observeCaptures()
        observeDownloadStates()
    }

    private fun loadEpisode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val episode = podcastDao.getEpisodeById(episodeId)
                // Try to get podcast from the episode's podcastId, or use the provided podcastId
                val actualPodcastId = episode?.podcastId ?: podcastId
                val podcast = if (actualPodcastId > 0) {
                    podcastDao.getBookmarkedPodcastById(actualPodcastId)
                } else null

                if (episode == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Episode not found"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    episode = episode,
                    podcast = podcast,
                    isLoading = false
                )

                // Check if episode is downloaded
                val localPath = podcastRepository.getLocalEpisodePath(episodeId)
                val downloadState = if (localPath != null) {
                    EpisodeDownloadState.Downloaded
                } else {
                    EpisodeDownloadState.NotDownloaded
                }
                _uiState.value = _uiState.value.copy(
                    localFilePath = localPath,
                    downloadState = downloadState
                )

                if (localPath == null) {
                    // Don't show error, just allow user to download from this screen
                    return@launch
                }

                val virtualAudioFileId = "episode_$episodeId"

                // Check if this episode is already loaded - don't reload
                if (audioPlayerService.currentAudioFileId.value == virtualAudioFileId) {
                    // Audio is already loaded for this episode, just update UI state
                    return@launch
                }

                // Get saved position and podcast title from history
                val history = podcastRepository.getPlaybackHistoryForEpisode(episodeId)
                val resumePosition = history?.positionMs ?: 0L

                // Record playback history
                if (podcast != null) {
                    podcastRepository.recordEpisodePlayback(episode, podcast, localPath)
                }

                // Get podcast title: prefer bookmarked podcast, fallback to history, then episode title
                val podcastTitle = podcast?.title ?: history?.podcastTitle ?: episode.title

                // Load audio from local file only
                val audioUri = Uri.parse("file://$localPath")

                audioPlayerService.loadAudio(
                    uri = audioUri,
                    audioFileId = virtualAudioFileId,
                    mimeType = episode.audioType,
                    title = episode.title,
                    artist = podcastTitle  // Podcast series name for notification
                )

                // Resume from saved position
                if (resumePosition > 0) {
                    audioPlayerService.seekTo(resumePosition)
                }

                // Auto-play
                audioPlayerService.play()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load episode"
                )
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            audioPlayerService.playbackState.collect { state ->
                _uiState.value = _uiState.value.copy(playbackState = state)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.skipIntervalSeconds.collect { interval ->
                _uiState.value = _uiState.value.copy(skipIntervalSeconds = interval)
            }
        }
        viewModelScope.launch {
            settingsDataStore.captureWindowSeconds.collect { seconds ->
                _uiState.value = _uiState.value.copy(captureWindowSeconds = seconds)
            }
        }
        viewModelScope.launch {
            settingsDataStore.captureWindowStep.collect { step ->
                _uiState.value = _uiState.value.copy(captureWindowStep = step)
            }
        }
    }

    private fun observeCaptures() {
        viewModelScope.launch {
            // Use virtual audioFileId for episode captures
            captureRepository.getCapturesForFile("episode_$episodeId").collect { captures ->
                _uiState.value = _uiState.value.copy(captures = captures)
            }
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadManager.downloadStates.collect { downloadStates ->
                val progress = downloadStates[episodeId]
                if (progress != null) {
                    _uiState.value = _uiState.value.copy(
                        downloadState = progress.state,
                        downloadProgress = progress.percent
                    )

                    // If download just completed, reload the episode to start playback
                    if (progress.state == EpisodeDownloadState.Downloaded) {
                        val localPath = podcastRepository.getLocalEpisodePath(episodeId)
                        if (localPath != null && _uiState.value.localFilePath == null) {
                            _uiState.value = _uiState.value.copy(localFilePath = localPath)
                            // Reload episode to start playback
                            loadEpisode()
                        }
                    }
                } else {
                    // Check file system state
                    val localPath = podcastRepository.getLocalEpisodePath(episodeId)
                    val state = if (localPath != null) {
                        EpisodeDownloadState.Downloaded
                    } else {
                        EpisodeDownloadState.NotDownloaded
                    }
                    _uiState.value = _uiState.value.copy(
                        downloadState = state,
                        localFilePath = localPath
                    )
                }
            }
        }
    }

    fun onDownload() {
        val episode = _uiState.value.episode ?: return
        val podcastTitle = _uiState.value.podcast?.title ?: ""
        downloadManager.downloadEpisode(episode, podcastTitle)
    }

    fun onPlayPause() {
        audioPlayerService.togglePlayPause()
    }

    fun onSeekTo(positionMs: Long) {
        audioPlayerService.seekTo(positionMs)
    }

    fun onRewind() {
        audioPlayerService.rewind(_uiState.value.skipIntervalSeconds * 1000L)
    }

    fun onFastForward() {
        audioPlayerService.fastForward(_uiState.value.skipIntervalSeconds * 1000L)
    }

    fun onSpeedChange(speed: Float) {
        audioPlayerService.setPlaybackSpeed(speed)
    }

    fun onCaptureWindowIncrease() {
        val currentWindow = _uiState.value.captureWindowSeconds
        val step = _uiState.value.captureWindowStep
        val newWindow = (currentWindow + step).coerceAtMost(60)
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowSeconds(newWindow)
        }
    }

    fun onCaptureWindowDecrease() {
        val currentWindow = _uiState.value.captureWindowSeconds
        val step = _uiState.value.captureWindowStep
        val newWindow = (currentWindow - step).coerceAtLeast(10)
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowSeconds(newWindow)
        }
    }

    fun toggleShowNotes() {
        _uiState.value = _uiState.value.copy(showNotes = !_uiState.value.showNotes)
    }

    fun savePlaybackPosition() {
        viewModelScope.launch {
            val position = audioPlayerService.getCurrentPosition()
            if (position > 0) {
                podcastRepository.updatePlaybackPosition(episodeId, position)
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Capture functionality
    fun onCapture() {
        val episode = _uiState.value.episode ?: return
        val localPath = _uiState.value.localFilePath

        if (localPath == null) {
            _uiState.value = _uiState.value.copy(
                captureError = "Please download the episode first to use capture"
            )
            return
        }

        val currentPosition = audioPlayerService.getCurrentPosition()
        val duration = audioPlayerService.getDuration()
        val windowMs = _uiState.value.captureWindowSeconds * 1000L

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCapturing = true,
                captureError = null,
                captureProgress = "Preparing..."
            )

            try {
                val windowStart = (currentPosition - windowMs).coerceAtLeast(0L)
                val windowEnd = (currentPosition + windowMs).coerceAtMost(duration)

                // Ensure model is downloaded
                if (!transcriptionService.isModelReady()) {
                    _uiState.value = _uiState.value.copy(
                        captureProgress = "Downloading speech model..."
                    )
                    val downloaded = transcriptionService.ensureModelReady()
                    if (!downloaded) {
                        throw IllegalStateException("Failed to download speech recognition model")
                    }
                }

                // Transcribe the audio segment
                _uiState.value = _uiState.value.copy(
                    captureProgress = "Transcribing audio..."
                )

                val transcription = transcriptionService.transcribe(
                    audioUri = Uri.parse("file://$localPath"),
                    startMs = windowStart,
                    endMs = windowEnd
                )

                // Create capture
                captureRepository.createEpisodeCapture(
                    episodeId = episodeId,
                    episodeTitle = episode.title,
                    timestampMs = currentPosition,
                    windowStartMs = windowStart,
                    windowEndMs = windowEnd,
                    transcription = transcription
                )

                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    captureSuccess = "Capture saved at ${formatDuration(currentPosition)}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    captureError = "Capture failed: ${e.message}"
                )
            }
        }
    }

    fun onCaptureSuccessDismissed() {
        _uiState.value = _uiState.value.copy(captureSuccess = null)
    }

    fun onCaptureErrorDismissed() {
        _uiState.value = _uiState.value.copy(captureError = null)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
