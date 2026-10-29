package com.podcapture.ui.home

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        override val captureCount: Int
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
    val isLoading: Boolean = false,  // Initial load, before History has been built once
    val isImporting: Boolean = false,  // Reading metadata of a newly picked audio file
    val error: String? = null,
    val navigateToPlayer: String? = null,  // audioFileId to navigate to
    val navigateToPodcast: Long? = null,  // podcastId to navigate to
    val navigateToEpisode: Pair<Long, Long>? = null,  // episodeId, podcastId to navigate to
    val showDeleteConfirmDialog: Boolean = false,
    val episodeToDelete: RecentItem.EpisodeRecent? = null,
    // Derived from the fields above by deriveHomeState() whenever their inputs change.
    // Never set these directly.
    val filteredBookmarkItems: List<BookmarkItem> = emptyList(),
    val filteredRecentItems: List<RecentItem> = emptyList(),
    val recentFileCount: Int = 0,
    val recentEpisodeCount: Int = 0,
    val downloadedEpisodeCount: Int = 0
) {
    // Counts for filter chips
    val podcastBookmarkCount: Int get() = bookmarkedPodcasts.size
    val fileBookmarkCount: Int get() = bookmarkedAudioFiles.size
}

class HomeViewModel(
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    // Unused since the Home tag filter and tag dialog were removed; still injected because
    // the Koin module (di/AppModule.kt) passes it. Drop it together with that argument.
    @Suppress("unused") private val tagRepository: TagRepository,
    private val podcastRepository: PodcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Bumped to force a fresh download-state lookup (e.g. after deleting a download).
    private val downloadRefresh = MutableStateFlow(0)

    init {
        observeBookmarks()
        observeRecentItems()
    }

    /** Applies [transform] and recomputes whichever derived fields it invalidated. */
    private inline fun setState(transform: (HomeUiState) -> HomeUiState) {
        _uiState.update { current -> deriveHomeState(current, transform(current)) }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            combine(
                podcastRepository.getAllBookmarkedPodcasts(),
                audioFileRepository.bookmarkedFiles
            ) { podcasts, files -> podcasts to files }
                .distinctUntilChanged()
                .collect { (podcasts, files) ->
                    setState {
                        it.copy(
                            bookmarkedPodcasts = podcasts,
                            bookmarkedAudioFiles = files,
                            bookmarkItems = combineBookmarks(podcasts, files)
                        )
                    }
                }
        }
    }

    /**
     * Builds the History list from three Room flows. Room invalidates whole tables, so the
     * playback-history flow re-emits every time the episode player saves its position
     * (every few seconds). Everything here is therefore in memory: capture counts come from
     * a single GROUP BY flow, download state is one batched lookup off the main thread, and
     * an unchanged result is dropped by distinctUntilChanged before it reaches the UI state.
     */
    private fun observeRecentItems() {
        val episodesWithDownloadState = combine(
            podcastRepository.getRecentPlaybackHistory(RECENT_EPISODE_LIMIT),
            downloadRefresh
        ) { history, _ -> history }
            .map { history -> history to findDownloadedEpisodeIds(history.map { it.episodeId }) }

        viewModelScope.launch {
            combine(
                audioFileRepository.allFiles,
                episodesWithDownloadState,
                captureRepository.getCaptureCountsByFile()
            ) { files, (history, downloadedIds), captureCounts ->
                mergeRecentItems(
                    files = buildRecentAudioFiles(files, captureCounts),
                    episodes = buildRecentEpisodes(history, captureCounts, downloadedIds)
                )
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collect { items ->
                    setState { it.copy(recentItems = items, isLoading = false) }
                }
        }
    }

    /**
     * Returns which of [episodeIds] have a downloaded file, in one pass on the IO dispatcher.
     * TODO: switch to PodcastRepository's downloadedEpisodeIds flow once it lands.
     */
    private suspend fun findDownloadedEpisodeIds(episodeIds: List<Long>): Set<Long> =
        withContext(Dispatchers.IO) {
            episodeIds.filterTo(HashSet()) { podcastRepository.getLocalEpisodePath(it) != null }
        }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                setState { it.copy(isImporting = true, error = null) }

                // Get file metadata
                val fileName = getFileName(context, uri) ?: "Unknown"
                val format = getFileFormat(fileName)
                val duration = getAudioDuration(context, uri)

                if (format == null) {
                    setState {
                        it.copy(
                            isImporting = false,
                            error = "Unsupported file format. Supported: MP3, WAV, M4A, AAC, FLAC, OGG, OPUS."
                        )
                    }
                    return@launch
                }

                // Add or update file in repository
                val audioFile = audioFileRepository.addOrUpdateFile(
                    name = fileName,
                    filePath = uri.toString(),
                    durationMs = duration,
                    format = format
                )

                setState {
                    it.copy(
                        isImporting = false,
                        navigateToPlayer = audioFile.id
                    )
                }

            } catch (e: Exception) {
                setState {
                    it.copy(
                        isImporting = false,
                        error = "Failed to load file: ${e.message}"
                    )
                }
            }
        }
    }

    fun onFileClicked(audioFileId: String) {
        setState { it.copy(navigateToPlayer = audioFileId) }
    }

    fun onPodcastClicked(podcastId: Long) {
        setState { it.copy(navigateToPodcast = podcastId) }
    }

    fun onNavigationHandled() {
        setState { it.copy(navigateToPlayer = null) }
    }

    fun onPodcastNavigationHandled() {
        setState { it.copy(navigateToPodcast = null) }
    }

    fun onErrorDismissed() {
        setState { it.copy(error = null) }
    }

    // View mode toggle
    fun onToggleBookmarkViewMode() {
        setState {
            it.copy(
                bookmarkViewMode = when (it.bookmarkViewMode) {
                    BookmarkViewMode.GRID -> BookmarkViewMode.LIST
                    BookmarkViewMode.LIST -> BookmarkViewMode.GRID
                }
            )
        }
    }

    // Bookmark filter type
    fun onBookmarkFilterChanged(filterType: BookmarkFilterType) {
        setState { it.copy(bookmarkFilterType = filterType) }
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
        setState { it.copy(navigateToEpisode = Pair(episodeId, podcastId)) }
    }

    fun onEpisodeNavigationHandled() {
        setState { it.copy(navigateToEpisode = null) }
    }

    // Recent filter type
    fun onRecentFilterChanged(filterType: RecentFilterType) {
        setState { it.copy(recentFilterType = filterType) }
    }

    // History search
    fun onHistorySearchQueryChanged(query: String) {
        setState { it.copy(historySearchQuery = query) }
    }

    // Sort order toggle
    fun onToggleSortOrder() {
        setState {
            it.copy(
                recentSortOrder = when (it.recentSortOrder) {
                    SortOrder.NEWEST -> SortOrder.OLDEST
                    SortOrder.OLDEST -> SortOrder.NEWEST
                }
            )
        }
    }

    // Delete downloaded episode
    fun onRequestDeleteDownload(episode: RecentItem.EpisodeRecent) {
        setState {
            it.copy(
                showDeleteConfirmDialog = true,
                episodeToDelete = episode
            )
        }
    }

    fun onConfirmDeleteDownload() {
        val episode = _uiState.value.episodeToDelete ?: return
        viewModelScope.launch {
            podcastRepository.deleteDownloadedEpisode(episode.episodeId)
            // The history row is rewritten by the delete, but re-check explicitly so the
            // card's download state never depends on that side effect.
            downloadRefresh.update { it + 1 }
            setState {
                it.copy(
                    showDeleteConfirmDialog = false,
                    episodeToDelete = null
                )
            }
        }
    }

    fun onDismissDeleteDialog() {
        setState {
            it.copy(
                showDeleteConfirmDialog = false,
                episodeToDelete = null
            )
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

    private companion object {
        const val RECENT_EPISODE_LIMIT = 50
    }
}
