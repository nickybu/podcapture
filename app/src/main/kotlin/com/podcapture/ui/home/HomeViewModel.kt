package com.podcapture.ui.home

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.Tag
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.repository.TagRepository
import com.podcapture.youtube.YouTubeDownloadManager
import com.podcapture.youtube.YouTubeDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AudioFileWithCaptureCount(
    val audioFile: AudioFile,
    val captureCount: Int,
    val tags: List<Tag> = emptyList()
)

// Sealed class to represent items in the bookmarks section
sealed class BookmarkItem {
    abstract val bookmarkedAt: Long

    data class PodcastBookmark(val podcast: BookmarkedPodcast) : BookmarkItem() {
        override val bookmarkedAt: Long = podcast.bookmarkedAt
    }

    data class AudioFileBookmark(val audioFile: AudioFile) : BookmarkItem() {
        override val bookmarkedAt: Long = audioFile.bookmarkedAt ?: 0L
    }
}

// Sealed class to represent items in the recent/history section
sealed class RecentItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val durationMs: Long
    abstract val lastPlayedAt: Long
    abstract val captureCount: Int

    data class AudioFileRecent(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val durationMs: Long,
        override val lastPlayedAt: Long,
        override val captureCount: Int,
        val tags: List<Tag>
    ) : RecentItem()

    data class EpisodeRecent(
        override val id: String,
        override val title: String,
        override val subtitle: String,  // Podcast title
        override val durationMs: Long,
        override val lastPlayedAt: Long,
        override val captureCount: Int,
        val episodeId: Long,
        val podcastId: Long,
        val artworkUrl: String,
        val positionMs: Long,
        val isDownloaded: Boolean
    ) : RecentItem()
}

enum class BookmarkViewMode {
    GRID,  // 5-per-row grid of cover photos
    LIST   // Large list with bookmark toggle
}

enum class BookmarkFilterType {
    ALL,       // Show all bookmarks
    PODCASTS,  // Show only podcast bookmarks
    FILES      // Show only audio file bookmarks
}

enum class RecentFilterType {
    ALL,         // Show all recent items
    FILES,       // Show only audio files
    EPISODES,    // Show only podcast episodes
    DOWNLOADED   // Show only downloaded episodes (for management)
}

enum class SortOrder {
    NEWEST,  // Most recently played first
    OLDEST   // Oldest played first
}

data class HomeUiState(
    val audioFiles: List<AudioFileWithCaptureCount> = emptyList(),
    val bookmarkedPodcasts: List<BookmarkedPodcast> = emptyList(),
    val bookmarkedAudioFiles: List<AudioFile> = emptyList(),
    val bookmarkItems: List<BookmarkItem> = emptyList(),  // Combined and sorted
    val bookmarkViewMode: BookmarkViewMode = BookmarkViewMode.GRID,
    val bookmarkFilterType: BookmarkFilterType = BookmarkFilterType.PODCASTS,
    // History items (unified recent + all)
    val recentItems: List<RecentItem> = emptyList(),
    val recentFilterType: RecentFilterType = RecentFilterType.ALL,
    val recentSortOrder: SortOrder = SortOrder.NEWEST,
    val historySearchQuery: String = "",
    val allTags: List<Tag> = emptyList(),
    val selectedTagId: String? = null,  // For filtering
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToPlayer: String? = null,  // audioFileId to navigate to
    val navigateToPodcast: Long? = null,  // podcastId to navigate to
    val navigateToEpisode: Pair<Long, Long>? = null,  // episodeId, podcastId to navigate to
    val showTagDialog: Boolean = false,
    val editingAudioFileId: String? = null,  // AudioFile being tagged
    val newTagName: String = "",
    val showDeleteConfirmDialog: Boolean = false,
    val episodeToDelete: RecentItem.EpisodeRecent? = null,
    // YouTube download state
    val showAddSourceSheet: Boolean = false,
    val showYouTubeUrlDialog: Boolean = false,
    val youTubeDownloadState: YouTubeDownloadState = YouTubeDownloadState.Idle
) {
    // Filtered bookmark items based on filter type
    val filteredBookmarkItems: List<BookmarkItem>
        get() = when (bookmarkFilterType) {
            BookmarkFilterType.ALL -> bookmarkItems
            BookmarkFilterType.PODCASTS -> bookmarkItems.filterIsInstance<BookmarkItem.PodcastBookmark>()
            BookmarkFilterType.FILES -> bookmarkItems.filterIsInstance<BookmarkItem.AudioFileBookmark>()
        }

    // Filtered and sorted recent/history items
    val filteredRecentItems: List<RecentItem>
        get() {
            // First, filter by type
            val typeFiltered = when (recentFilterType) {
                RecentFilterType.ALL -> recentItems
                RecentFilterType.FILES -> recentItems.filterIsInstance<RecentItem.AudioFileRecent>()
                RecentFilterType.EPISODES -> recentItems.filterIsInstance<RecentItem.EpisodeRecent>()
                RecentFilterType.DOWNLOADED -> recentItems.filterIsInstance<RecentItem.EpisodeRecent>()
                    .filter { it.isDownloaded }
            }

            // Then, apply search filter
            val searchFiltered = if (historySearchQuery.isBlank()) {
                typeFiltered
            } else {
                val query = historySearchQuery.lowercase()
                typeFiltered.filter {
                    it.title.lowercase().contains(query) ||
                    it.subtitle.lowercase().contains(query)
                }
            }

            // Finally, apply sort order
            return when (recentSortOrder) {
                SortOrder.NEWEST -> searchFiltered.sortedByDescending { it.lastPlayedAt }
                SortOrder.OLDEST -> searchFiltered.sortedBy { it.lastPlayedAt }
            }
        }

    // Counts for filter chips
    val podcastBookmarkCount: Int get() = bookmarkedPodcasts.size
    val fileBookmarkCount: Int get() = bookmarkedAudioFiles.size

    // Recent counts
    val recentFileCount: Int get() = recentItems.count { it is RecentItem.AudioFileRecent }
    val recentEpisodeCount: Int get() = recentItems.count { it is RecentItem.EpisodeRecent }
    val downloadedEpisodeCount: Int get() = recentItems
        .filterIsInstance<RecentItem.EpisodeRecent>()
        .count { it.isDownloaded }
}

class HomeViewModel(
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    private val tagRepository: TagRepository,
    private val podcastRepository: PodcastRepository,
    private val youTubeDownloadManager: YouTubeDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allFilesWithData: List<AudioFileWithCaptureCount> = emptyList()
    private var recentAudioFiles: List<RecentItem.AudioFileRecent> = emptyList()
    private var recentEpisodes: List<RecentItem.EpisodeRecent> = emptyList()

    init {
        observeAudioFiles()
        observeBookmarkedPodcasts()
        observeBookmarkedAudioFiles()
        observeEpisodeHistory()
        observeTags()
        observeYouTubeDownloadState()
    }

    private fun observeAudioFiles() {
        viewModelScope.launch {
            audioFileRepository.allFiles.collect { files ->
                val filesWithData = files.map { file ->
                    val captures = captureRepository.getCapturesForFileOnce(file.id)
                    val tags = tagRepository.getTagsForAudioFileOnce(file.id)
                    AudioFileWithCaptureCount(file, captures.size, tags)
                }
                allFilesWithData = filesWithData

                // Build recent audio files list (only files that have been played)
                recentAudioFiles = filesWithData
                    .filter { it.audioFile.lastPlayedAt != null && !it.audioFile.id.startsWith("episode_") }
                    .map { item ->
                        RecentItem.AudioFileRecent(
                            id = "file_${item.audioFile.id}",
                            title = item.audioFile.name,
                            subtitle = formatDuration(item.audioFile.durationMs),
                            durationMs = item.audioFile.durationMs,
                            lastPlayedAt = item.audioFile.lastPlayedAt ?: 0L,
                            captureCount = item.captureCount,
                            tags = item.tags
                        )
                    }

                applyFilter()
                updateRecentItems()
            }
        }
    }

    private fun observeBookmarkedPodcasts() {
        viewModelScope.launch {
            podcastRepository.getAllBookmarkedPodcasts().collect { podcasts ->
                _uiState.value = _uiState.value.copy(bookmarkedPodcasts = podcasts)
                updateCombinedBookmarks()
            }
        }
    }

    private fun observeBookmarkedAudioFiles() {
        viewModelScope.launch {
            audioFileRepository.bookmarkedFiles.collect { files ->
                _uiState.value = _uiState.value.copy(bookmarkedAudioFiles = files)
                updateCombinedBookmarks()
            }
        }
    }

    private fun updateCombinedBookmarks() {
        val podcasts = _uiState.value.bookmarkedPodcasts.map { BookmarkItem.PodcastBookmark(it) }
        val files = _uiState.value.bookmarkedAudioFiles.map { BookmarkItem.AudioFileBookmark(it) }
        val combined = (podcasts + files).sortedByDescending { it.bookmarkedAt }
        _uiState.value = _uiState.value.copy(bookmarkItems = combined)
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.value = _uiState.value.copy(allTags = tags)
            }
        }
    }

    private fun observeEpisodeHistory() {
        viewModelScope.launch {
            podcastRepository.getRecentPlaybackHistory(50).collect { historyList ->
                recentEpisodes = historyList.map { history ->
                    val captureCount = captureRepository.getCapturesForFileOnce("episode_${history.episodeId}").size
                    val isDownloaded = podcastRepository.getLocalEpisodePath(history.episodeId) != null

                    RecentItem.EpisodeRecent(
                        id = "episode_${history.episodeId}",
                        title = history.episodeTitle,
                        subtitle = history.podcastTitle,
                        durationMs = history.duration * 1000L,
                        lastPlayedAt = history.lastPlayedAt,
                        captureCount = captureCount,
                        episodeId = history.episodeId,
                        podcastId = history.podcastId,
                        artworkUrl = history.podcastArtworkUrl,
                        positionMs = history.positionMs,
                        isDownloaded = isDownloaded
                    )
                }
                updateRecentItems()
            }
        }
    }

    private fun updateRecentItems() {
        // Merge and sort by lastPlayedAt
        val combined = (recentAudioFiles + recentEpisodes)
            .sortedByDescending { it.lastPlayedAt }
        _uiState.value = _uiState.value.copy(recentItems = combined)
    }

    private fun applyFilter() {
        val selectedTagId = _uiState.value.selectedTagId
        val filteredFiles = if (selectedTagId == null) {
            allFilesWithData
        } else {
            allFilesWithData.filter { item -> item.tags.any { it.id == selectedTagId } }
        }
        _uiState.value = _uiState.value.copy(
            audioFiles = filteredFiles,
            isLoading = false
        )
    }

    fun onTagFilterSelected(tagId: String?) {
        _uiState.value = _uiState.value.copy(selectedTagId = tagId)
        applyFilter()
    }

    fun onOpenTagDialog(audioFileId: String) {
        _uiState.value = _uiState.value.copy(
            showTagDialog = true,
            editingAudioFileId = audioFileId
        )
    }

    fun onCloseTagDialog() {
        _uiState.value = _uiState.value.copy(
            showTagDialog = false,
            editingAudioFileId = null,
            newTagName = ""
        )
    }

    fun onNewTagNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newTagName = name)
    }

    fun onCreateTag() {
        val name = _uiState.value.newTagName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val tag = tagRepository.createTag(name)
            // If editing an audio file, add the new tag to it
            _uiState.value.editingAudioFileId?.let { audioFileId ->
                tagRepository.addTagToAudioFile(audioFileId, tag.id)
            }
            _uiState.value = _uiState.value.copy(newTagName = "")
            refreshAudioFiles()
        }
    }

    fun onToggleTagForAudioFile(tagId: String) {
        val audioFileId = _uiState.value.editingAudioFileId ?: return
        val currentFile = allFilesWithData.find { it.audioFile.id == audioFileId } ?: return
        val hasTag = currentFile.tags.any { it.id == tagId }

        viewModelScope.launch {
            if (hasTag) {
                tagRepository.removeTagFromAudioFile(audioFileId, tagId)
            } else {
                tagRepository.addTagToAudioFile(audioFileId, tagId)
            }
            refreshAudioFiles()
        }
    }

    fun onDeleteTag(tag: Tag) {
        viewModelScope.launch {
            tagRepository.deleteTag(tag)
            refreshAudioFiles()
        }
    }

    private suspend fun refreshAudioFiles() {
        val files = audioFileRepository.getAllFilesOnce()
        val filesWithData = files.map { file ->
            val captures = captureRepository.getCapturesForFileOnce(file.id)
            val tags = tagRepository.getTagsForAudioFileOnce(file.id)
            AudioFileWithCaptureCount(file, captures.size, tags)
        }
        allFilesWithData = filesWithData
        applyFilter()
    }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Get file metadata
                val fileName = getFileName(context, uri) ?: "Unknown"
                val format = getFileFormat(fileName)
                val duration = getAudioDuration(context, uri)

                if (format == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Unsupported file format. Supported: MP3, WAV, M4A, AAC, FLAC, OGG, OPUS."
                    )
                    return@launch
                }

                // Add or update file in repository
                val audioFile = audioFileRepository.addOrUpdateFile(
                    name = fileName,
                    filePath = uri.toString(),
                    durationMs = duration,
                    format = format
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    navigateToPlayer = audioFile.id
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load file: ${e.message}"
                )
            }
        }
    }

    fun onFileClicked(audioFileId: String) {
        _uiState.value = _uiState.value.copy(navigateToPlayer = audioFileId)
    }

    fun onPodcastClicked(podcastId: Long) {
        _uiState.value = _uiState.value.copy(navigateToPodcast = podcastId)
    }

    fun onNavigationHandled() {
        _uiState.value = _uiState.value.copy(navigateToPlayer = null)
    }

    fun onPodcastNavigationHandled() {
        _uiState.value = _uiState.value.copy(navigateToPodcast = null)
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // View mode toggle
    fun onToggleBookmarkViewMode() {
        val newMode = when (_uiState.value.bookmarkViewMode) {
            BookmarkViewMode.GRID -> BookmarkViewMode.LIST
            BookmarkViewMode.LIST -> BookmarkViewMode.GRID
        }
        _uiState.value = _uiState.value.copy(bookmarkViewMode = newMode)
    }

    // Bookmark filter type
    fun onBookmarkFilterChanged(filterType: BookmarkFilterType) {
        _uiState.value = _uiState.value.copy(bookmarkFilterType = filterType)
    }

    // Bookmark toggle for audio files
    fun onToggleAudioFileBookmark(audioFileId: String) {
        viewModelScope.launch {
            audioFileRepository.toggleBookmark(audioFileId)
        }
    }

    // Unbookmark podcast
    fun onUnbookmarkPodcast(podcastId: Long) {
        viewModelScope.launch {
            podcastRepository.unbookmarkPodcast(podcastId)
        }
    }

    // Episode navigation
    fun onEpisodeClicked(episodeId: Long, podcastId: Long) {
        _uiState.value = _uiState.value.copy(navigateToEpisode = Pair(episodeId, podcastId))
    }

    fun onEpisodeNavigationHandled() {
        _uiState.value = _uiState.value.copy(navigateToEpisode = null)
    }

    // Recent filter type
    fun onRecentFilterChanged(filterType: RecentFilterType) {
        _uiState.value = _uiState.value.copy(recentFilterType = filterType)
    }

    // History search
    fun onHistorySearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(historySearchQuery = query)
    }

    // Sort order toggle
    fun onToggleSortOrder() {
        val newOrder = when (_uiState.value.recentSortOrder) {
            SortOrder.NEWEST -> SortOrder.OLDEST
            SortOrder.OLDEST -> SortOrder.NEWEST
        }
        _uiState.value = _uiState.value.copy(recentSortOrder = newOrder)
    }

    // Delete downloaded episode
    fun onRequestDeleteDownload(episode: RecentItem.EpisodeRecent) {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmDialog = true,
            episodeToDelete = episode
        )
    }

    fun onConfirmDeleteDownload() {
        val episode = _uiState.value.episodeToDelete ?: return
        viewModelScope.launch {
            podcastRepository.deleteDownloadedEpisode(episode.episodeId)
            // Update the local state to reflect deletion
            recentEpisodes = recentEpisodes.map { ep ->
                if (ep.episodeId == episode.episodeId) {
                    ep.copy(isDownloaded = false)
                } else ep
            }
            updateRecentItems()
            _uiState.value = _uiState.value.copy(
                showDeleteConfirmDialog = false,
                episodeToDelete = null
            )
        }
    }

    fun onDismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmDialog = false,
            episodeToDelete = null
        )
    }

    // YouTube download methods
    private fun observeYouTubeDownloadState() {
        viewModelScope.launch {
            youTubeDownloadManager.downloadState.collect { state ->
                _uiState.value = _uiState.value.copy(youTubeDownloadState = state)

                // Auto-navigate to player on completion
                if (state is YouTubeDownloadState.Completed) {
                    _uiState.value = _uiState.value.copy(navigateToPlayer = state.audioFileId)
                }
            }
        }
    }

    fun onShowAddSourceSheet() {
        _uiState.value = _uiState.value.copy(showAddSourceSheet = true)
    }

    fun onDismissAddSourceSheet() {
        _uiState.value = _uiState.value.copy(showAddSourceSheet = false)
    }

    fun onShowYouTubeUrlDialog() {
        _uiState.value = _uiState.value.copy(showYouTubeUrlDialog = true)
    }

    fun onDismissYouTubeUrlDialog() {
        _uiState.value = _uiState.value.copy(showYouTubeUrlDialog = false)
    }

    fun onYouTubeImport(url: String) {
        _uiState.value = _uiState.value.copy(showYouTubeUrlDialog = false)

        val result = youTubeDownloadManager.startDownload(url)
        if (result.isFailure) {
            _uiState.value = _uiState.value.copy(
                error = result.exceptionOrNull()?.message ?: "Failed to start download"
            )
        }
    }

    fun onCancelYouTubeDownload() {
        youTubeDownloadManager.cancelDownload()
    }

    fun onYouTubeDownloadStateHandled() {
        youTubeDownloadManager.resetState()
    }

    fun onCaptchaSolved() {
        youTubeDownloadManager.onCaptchaSolved()
    }

    fun onCaptchaCancelled() {
        youTubeDownloadManager.onCaptchaCancelled()
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun getFileFormat(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp3" -> "mp3"
            "wav" -> "wav"
            "m4a" -> "m4a"
            "aac" -> "aac"
            "flac" -> "flac"
            "ogg" -> "ogg"
            "opus" -> "opus"
            "wma" -> "wma"
            "webm" -> "webm"
            else -> null
        }
    }

    private fun getAudioDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}
