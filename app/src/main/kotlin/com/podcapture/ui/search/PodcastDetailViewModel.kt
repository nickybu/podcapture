package com.podcapture.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.Episode
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.data.model.Podcast
import com.podcapture.data.model.Tag
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.repository.PodcastSearchRepository
import com.podcapture.data.repository.TagRepository
import com.podcapture.download.DownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EpisodeUiItem(
    val episode: CachedEpisode,
    val downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

data class PodcastDetailUiState(
    val podcast: Podcast? = null,
    val episodes: List<EpisodeUiItem> = emptyList(),
    val filteredEpisodes: List<EpisodeUiItem>? = null,  // null = show all, non-null = filtered
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreEpisodes: Boolean = true,
    val isBookmarked: Boolean = false,
    val isBookmarkLoading: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val showTagDialog: Boolean = false,
    val newTagName: String = "",
    val error: String? = null
) {
    val displayedEpisodes: List<EpisodeUiItem>
        get() = filteredEpisodes ?: episodes
}

class PodcastDetailViewModel(
    private val podcastId: Long,
    private val searchRepository: PodcastSearchRepository,
    private val podcastRepository: PodcastRepository,
    private val tagRepository: TagRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    init {
        loadPodcast()
        observeBookmarkState()
        observeTags()
        observeDownloadStates()
    }

    private fun loadPodcast() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load podcast details from API
            searchRepository.getPodcastById(podcastId).fold(
                onSuccess = { podcast ->
                    _uiState.value = _uiState.value.copy(podcast = podcast)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load podcast"
                    )
                    return@launch
                }
            )

            // Check if bookmarked and load cached episodes
            val isBookmarked = podcastRepository.isPodcastBookmarked(podcastId).first()

            if (isBookmarked) {
                // Load from cache and refresh for new episodes in background
                observeCachedEpisodes()
                refreshEpisodesInBackground()
            } else {
                // Load from API directly
                loadEpisodesFromApi()
            }
        }
    }

    private fun observeCachedEpisodes() {
        viewModelScope.launch {
            podcastRepository.getEpisodesForPodcast(podcastId).collect { cachedEpisodes ->
                val downloadStates = downloadManager.downloadStates.value
                val episodeItems = cachedEpisodes.map { episode ->
                    val downloadProgress = downloadStates[episode.id]

                    // Determine state from DownloadManager or check file system
                    val (state, progress, downloadedBytes, totalBytes) = if (downloadProgress != null) {
                        Quadruple(
                            downloadProgress.state,
                            downloadProgress.percent,
                            downloadProgress.downloadedBytes,
                            downloadProgress.totalBytes
                        )
                    } else {
                        // Check if file exists on disk
                        val localPath = podcastRepository.getLocalEpisodePath(episode.id)
                        if (localPath != null) {
                            Quadruple(EpisodeDownloadState.Downloaded, 100, 0L, 0L)
                        } else {
                            Quadruple(EpisodeDownloadState.NotDownloaded, 0, 0L, 0L)
                        }
                    }

                    EpisodeUiItem(episode, state, progress, downloadedBytes, totalBytes)
                }

                _uiState.value = _uiState.value.copy(
                    episodes = episodeItems,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Observe download progress from DownloadManager to update UI.
     * Also re-checks file system state for items without active downloads.
     */
    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadManager.downloadStates.collect { downloadStates ->
                // Update episode states based on download progress or file system state
                val updatedEpisodes = _uiState.value.episodes.map { item ->
                    val progress = downloadStates[item.episode.id]
                    if (progress != null) {
                        item.copy(
                            downloadState = progress.state,
                            downloadProgress = progress.percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes
                        )
                    } else {
                        // Re-check file system state (handles deleted files)
                        val localPath = podcastRepository.getLocalEpisodePath(item.episode.id)
                        if (localPath != null) {
                            item.copy(downloadState = EpisodeDownloadState.Downloaded)
                        } else {
                            item.copy(downloadState = EpisodeDownloadState.NotDownloaded)
                        }
                    }
                }

                // Also update filtered episodes
                val updatedFiltered = _uiState.value.filteredEpisodes?.map { item ->
                    val progress = downloadStates[item.episode.id]
                    if (progress != null) {
                        item.copy(
                            downloadState = progress.state,
                            downloadProgress = progress.percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes
                        )
                    } else {
                        // Re-check file system state (handles deleted files)
                        val localPath = podcastRepository.getLocalEpisodePath(item.episode.id)
                        if (localPath != null) {
                            item.copy(downloadState = EpisodeDownloadState.Downloaded)
                        } else {
                            item.copy(downloadState = EpisodeDownloadState.NotDownloaded)
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    episodes = updatedEpisodes,
                    filteredEpisodes = updatedFiltered
                )
            }
        }
    }

    // Simple helper class for destructuring
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private suspend fun loadEpisodesFromApi() {
        // Fetch all episodes (up to 1000)
        searchRepository.getEpisodesByPodcastId(podcastId, maxResults = 1000).fold(
            onSuccess = { episodes ->
                val currentDownloadStates = downloadManager.downloadStates.value
                val episodeItems = episodes.map { episode ->
                    val cachedEpisode = episode.toCachedEpisode()
                    val downloadProgress = currentDownloadStates[cachedEpisode.id]

                    // Check DownloadManager first, then file system
                    val (state, progress, downloadedBytes, totalBytes) = if (downloadProgress != null) {
                        Quadruple(
                            downloadProgress.state,
                            downloadProgress.percent,
                            downloadProgress.downloadedBytes,
                            downloadProgress.totalBytes
                        )
                    } else {
                        val localPath = podcastRepository.getLocalEpisodePath(cachedEpisode.id)
                        if (localPath != null) {
                            Quadruple(EpisodeDownloadState.Downloaded, 100, 0L, 0L)
                        } else {
                            Quadruple(EpisodeDownloadState.NotDownloaded, 0, 0L, 0L)
                        }
                    }
                    EpisodeUiItem(cachedEpisode, state, progress, downloadedBytes, totalBytes)
                }
                _uiState.value = _uiState.value.copy(
                    episodes = episodeItems,
                    isLoading = false,
                    hasMoreEpisodes = false  // We load all at once
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load episodes"
                )
            }
        )
    }

    private fun observeBookmarkState() {
        viewModelScope.launch {
            podcastRepository.isPodcastBookmarked(podcastId).collect { isBookmarked ->
                _uiState.value = _uiState.value.copy(isBookmarked = isBookmarked)
            }
        }
    }

    private fun observeTags() {
        viewModelScope.launch {
            combine(
                podcastRepository.getTagsForPodcast(podcastId),
                tagRepository.getAllTags()
            ) { podcastTags, allTags ->
                podcastTags to allTags
            }.collect { (podcastTags, allTags) ->
                _uiState.value = _uiState.value.copy(
                    tags = podcastTags,
                    allTags = allTags
                )
            }
        }
    }


    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearchFilter(query)
    }

    private fun applySearchFilter(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(filteredEpisodes = null)
        } else {
            val lowerQuery = query.lowercase()
            val filtered = _uiState.value.episodes.filter { item ->
                item.episode.title.lowercase().contains(lowerQuery) ||
                item.episode.description.lowercase().contains(lowerQuery)
            }
            _uiState.value = _uiState.value.copy(filteredEpisodes = filtered)
        }
    }

    fun onClearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "", filteredEpisodes = null)
    }

    fun onToggleBookmark() {
        val podcast = _uiState.value.podcast ?: return
        if (_uiState.value.isBookmarkLoading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBookmarkLoading = true)

            if (_uiState.value.isBookmarked) {
                podcastRepository.unbookmarkPodcast(podcastId).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(isBookmarkLoading = false)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isBookmarkLoading = false,
                            error = error.message ?: "Failed to remove bookmark"
                        )
                    }
                )
            } else {
                podcastRepository.bookmarkPodcast(podcast).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(isBookmarkLoading = false)
                        // Switch to cached episodes
                        observeCachedEpisodes()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isBookmarkLoading = false,
                            error = error.message ?: "Failed to bookmark"
                        )
                    }
                )
            }
        }
    }

    /**
     * Start downloading an episode using the background DownloadManager.
     * Downloads continue even if the user leaves this screen.
     * Auto-bookmarks the podcast if not already bookmarked (required for caching episodes).
     */
    fun onDownloadEpisode(episodeId: Long) {
        val episode = _uiState.value.episodes.find { it.episode.id == episodeId }?.episode ?: return
        val podcast = _uiState.value.podcast ?: return
        val podcastTitle = podcast.title

        viewModelScope.launch {
            // Auto-bookmark if not already bookmarked (episodes require podcast to be bookmarked)
            if (!_uiState.value.isBookmarked) {
                podcastRepository.bookmarkPodcast(podcast).fold(
                    onSuccess = {
                        // Now download the episode
                        downloadManager.downloadEpisode(episode, podcastTitle)
                        // Switch to cached episodes
                        observeCachedEpisodes()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message ?: "Failed to bookmark podcast for download"
                        )
                    }
                )
            } else {
                // Already bookmarked, just download
                downloadManager.downloadEpisode(episode, podcastTitle)
            }
        }
    }

    // Tag management
    fun onOpenTagDialog() {
        val podcast = _uiState.value.podcast ?: return

        viewModelScope.launch {
            // Auto-bookmark if not already bookmarked (tags require bookmark)
            if (!_uiState.value.isBookmarked) {
                _uiState.value = _uiState.value.copy(isBookmarkLoading = true)
                podcastRepository.bookmarkPodcast(podcast).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isBookmarkLoading = false,
                            showTagDialog = true
                        )
                        // Switch to cached episodes
                        observeCachedEpisodes()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isBookmarkLoading = false,
                            error = error.message ?: "Failed to bookmark podcast for tagging"
                        )
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(showTagDialog = true)
            }
        }
    }

    fun onCloseTagDialog() {
        _uiState.value = _uiState.value.copy(showTagDialog = false, newTagName = "")
    }

    fun onNewTagNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newTagName = name)
    }

    fun onCreateTag() {
        val name = _uiState.value.newTagName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val tag = tagRepository.createTag(name)
            podcastRepository.addTagToPodcast(podcastId, tag.id)
            _uiState.value = _uiState.value.copy(newTagName = "")
        }
    }

    fun onToggleTag(tagId: String) {
        viewModelScope.launch {
            val currentTags = _uiState.value.tags.map { it.id }
            if (tagId in currentTags) {
                podcastRepository.removeTagFromPodcast(podcastId, tagId)
            } else {
                podcastRepository.addTagToPodcast(podcastId, tagId)
            }
        }
    }

    /**
     * Refreshes episodes in the background when opening a bookmarked podcast.
     * Only fetches episodes newer than the most recent cached episode.
     */
    private fun refreshEpisodesInBackground() {
        viewModelScope.launch {
            // Silent refresh - don't show loading indicators
            podcastRepository.refreshEpisodes(podcastId)
            // Episodes will update automatically via observeCachedEpisodes flow
        }
    }

    fun onRetry() {
        loadPodcast()
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Refreshes download states by checking file system.
     * Call this when the screen becomes visible to catch any external changes.
     */
    fun refreshDownloadStates() {
        viewModelScope.launch {
            val downloadStates = downloadManager.downloadStates.value
            val updatedEpisodes = _uiState.value.episodes.map { item ->
                val progress = downloadStates[item.episode.id]
                if (progress != null) {
                    item.copy(
                        downloadState = progress.state,
                        downloadProgress = progress.percent,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes
                    )
                } else {
                    val localPath = podcastRepository.getLocalEpisodePath(item.episode.id)
                    if (localPath != null) {
                        item.copy(downloadState = EpisodeDownloadState.Downloaded)
                    } else {
                        item.copy(downloadState = EpisodeDownloadState.NotDownloaded)
                    }
                }
            }

            val updatedFiltered = _uiState.value.filteredEpisodes?.map { item ->
                val progress = downloadStates[item.episode.id]
                if (progress != null) {
                    item.copy(
                        downloadState = progress.state,
                        downloadProgress = progress.percent,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes
                    )
                } else {
                    val localPath = podcastRepository.getLocalEpisodePath(item.episode.id)
                    if (localPath != null) {
                        item.copy(downloadState = EpisodeDownloadState.Downloaded)
                    } else {
                        item.copy(downloadState = EpisodeDownloadState.NotDownloaded)
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                episodes = updatedEpisodes,
                filteredEpisodes = updatedFiltered
            )
        }
    }
}

/**
 * Extension function to convert Episode to CachedEpisode for display.
 */
private fun Episode.toCachedEpisode() = CachedEpisode(
    id = id,
    podcastId = podcastId,
    title = title,
    description = description,
    link = link,
    publishedDate = publishedDate,
    duration = duration,
    audioUrl = audioUrl,
    audioType = audioType,
    audioSize = audioSize,
    imageUrl = imageUrl,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl
)
