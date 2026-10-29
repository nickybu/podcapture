package com.podcapture.ui.search

import androidx.compose.runtime.Immutable
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
import com.podcapture.download.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the episode list. Only changes when the episode list or playback history
 * changes; download state is looked up separately (see [PodcastDetailUiState.downloadInfo])
 * so download progress ticks don't rebuild the list.
 *
 * @property searchText lowercase title plus HTML-stripped description, computed once per
 *   episode so filtering doesn't re-lowercase every description on each keystroke.
 */
@Immutable
data class EpisodeUiItem(
    val episode: CachedEpisode,
    val isFinished: Boolean = false,
    val searchText: String = ""
)

/** Download state of one episode as shown on its card. */
@Immutable
data class EpisodeDownloadInfo(
    val state: EpisodeDownloadState,
    val percent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    companion object {
        val NotDownloaded = EpisodeDownloadInfo(EpisodeDownloadState.NotDownloaded)
        val Downloaded = EpisodeDownloadInfo(EpisodeDownloadState.Downloaded, percent = 100)
    }
}

data class PodcastDetailUiState(
    val podcast: Podcast? = null,
    val episodes: List<EpisodeUiItem> = emptyList(),
    /**
     * Episodes matching [searchQuery], or null when the query is blank. Derived from
     * [episodes] whenever either changes; holds the same item instances, not copies.
     */
    val filteredEpisodes: List<EpisodeUiItem>? = null,
    val searchQuery: String = "",
    /** Active and recent downloads from [DownloadManager], by episode ID. */
    val downloads: Map<Long, DownloadProgress> = emptyMap(),
    /** Episodes with an audio file on disk. */
    val downloadedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
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

    fun downloadInfo(episodeId: Long): EpisodeDownloadInfo =
        resolveDownloadInfo(downloads[episodeId], episodeId in downloadedIds)
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

    // The one collector feeding the episode list. Replaced, never stacked, when the source
    // switches between the API result, the local cache and an in-memory snapshot.
    private var episodesJob: Job? = null

    private val finishedEpisodeIds: Flow<Set<Long>> =
        podcastRepository.getPlaybackHistoryForPodcast(podcastId)
            .map { history -> history.filter { it.isFinished }.mapTo(HashSet()) { it.episodeId } }
            .distinctUntilChanged()

    init {
        loadPodcast()
        observeBookmarkState()
        observeTags()
    }

    private fun loadPodcast() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check if bookmarked first — bookmarked podcasts (including custom-RSS imports
            // with synthetic negative IDs) live only in the local DB, not the Podcast Index.
            val isBookmarked = podcastRepository.isPodcastBookmarked(podcastId).first()

            if (isBookmarked) {
                val cached = podcastRepository.getBookmarkedPodcastAsPodcast(podcastId)
                if (cached != null) {
                    _uiState.update { it.copy(podcast = cached) }
                }
                observeCachedEpisodes()
                refreshEpisodesInBackground()
                return@launch
            }

            // Not bookmarked — load podcast details from Podcast Index API
            searchRepository.getPodcastById(podcastId).fold(
                onSuccess = { podcast ->
                    _uiState.update { it.copy(podcast = podcast) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load podcast"
                        )
                    }
                    return@launch
                }
            )

            loadEpisodesFromApi()
        }
    }

    private fun observeCachedEpisodes() {
        observeEpisodes(podcastRepository.getEpisodesForPodcast(podcastId))
    }

    /**
     * Makes [source] the episode list's source, cancelling the previous collector.
     *
     * Finished flags and download state are both derived here, so a new episode-list
     * emission (e.g. from the background refresh) can't drop them. The list items are only
     * rebuilt when the episodes or the finished set change; download ticks just replace the
     * [PodcastDetailUiState.downloads] map.
     */
    private fun observeEpisodes(source: Flow<List<CachedEpisode>>) {
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            val searchable = source
                .map { episodes -> episodes.map { it to buildEpisodeSearchText(it.title, it.description) } }
                .flowOn(Dispatchers.Default)
            val items = combine(searchable, finishedEpisodeIds, ::toEpisodeUiItems)
                .flowOn(Dispatchers.Default)

            combine(
                items,
                downloadManager.downloadStates,
                podcastRepository.downloadedEpisodeIds,
                ::Triple
            ).collect { (episodeItems, downloads, downloadedIds) ->
                _uiState.update { state ->
                    state.copy(
                        episodes = episodeItems,
                        filteredEpisodes = if (episodeItems === state.episodes) {
                            state.filteredEpisodes
                        } else {
                            filterEpisodes(episodeItems, state.searchQuery)
                        },
                        downloads = downloads,
                        downloadedIds = downloadedIds,
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun loadEpisodesFromApi() {
        // Fetch all episodes (up to 1000)
        searchRepository.getEpisodesByPodcastId(podcastId, maxResults = 1000).fold(
            onSuccess = { episodes ->
                observeEpisodes(flowOf(episodes.map { it.toCachedEpisode() }))
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load episodes"
                    )
                }
            }
        )
    }

    private fun observeBookmarkState() {
        viewModelScope.launch {
            podcastRepository.isPodcastBookmarked(podcastId).collect { isBookmarked ->
                _uiState.update { it.copy(isBookmarked = isBookmarked) }
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
                _uiState.update {
                    it.copy(
                        tags = podcastTags,
                        allTags = allTags
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(searchQuery = query, filteredEpisodes = filterEpisodes(it.episodes, query))
        }
    }

    fun onClearSearch() {
        _uiState.update { it.copy(searchQuery = "", filteredEpisodes = null) }
    }

    fun onToggleBookmark() {
        val podcast = _uiState.value.podcast ?: return
        if (_uiState.value.isBookmarkLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBookmarkLoading = true) }

            if (_uiState.value.isBookmarked) {
                // Unbookmarking cascades to the cached episodes, so the cache would empty the
                // list. Keep showing what's on screen instead; it's still the podcast's feed.
                observeEpisodes(flowOf(_uiState.value.episodes.map { it.episode }))
                podcastRepository.unbookmarkPodcast(podcastId).fold(
                    onSuccess = {
                        _uiState.update { it.copy(isBookmarkLoading = false) }
                    },
                    onFailure = { error ->
                        observeCachedEpisodes()
                        _uiState.update {
                            it.copy(
                                isBookmarkLoading = false,
                                error = error.message ?: "Failed to remove bookmark"
                            )
                        }
                    }
                )
            } else {
                podcastRepository.bookmarkPodcast(podcast).fold(
                    onSuccess = {
                        _uiState.update { it.copy(isBookmarkLoading = false) }
                        // Switch to cached episodes
                        observeCachedEpisodes()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isBookmarkLoading = false,
                                error = error.message ?: "Failed to bookmark"
                            )
                        }
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
                        _uiState.update {
                            it.copy(error = error.message ?: "Failed to bookmark podcast for download")
                        }
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
                _uiState.update { it.copy(isBookmarkLoading = true) }
                podcastRepository.bookmarkPodcast(podcast).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isBookmarkLoading = false,
                                showTagDialog = true
                            )
                        }
                        // Switch to cached episodes
                        observeCachedEpisodes()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isBookmarkLoading = false,
                                error = error.message ?: "Failed to bookmark podcast for tagging"
                            )
                        }
                    }
                )
            } else {
                _uiState.update { it.copy(showTagDialog = true) }
            }
        }
    }

    fun onCloseTagDialog() {
        _uiState.update { it.copy(showTagDialog = false, newTagName = "") }
    }

    fun onNewTagNameChanged(name: String) {
        _uiState.update { it.copy(newTagName = name) }
    }

    fun onCreateTag() {
        val name = _uiState.value.newTagName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val tag = tagRepository.createTag(name)
            podcastRepository.addTagToPodcast(podcastId, tag.id)
            _uiState.update { it.copy(newTagName = "") }
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
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Re-lists the downloads directory (one listing, off the main thread) to catch files
     * added or removed outside this screen. Call when the screen becomes visible.
     */
    fun refreshDownloadStates() {
        viewModelScope.launch {
            podcastRepository.refreshDownloadedIds()
        }
    }
}

/**
 * Download state for a card: an active download or failure reported by [DownloadManager]
 * wins; otherwise the file on disk decides. A stale "Downloaded" entry in the manager (the
 * file was since deleted) therefore shows as not downloaded.
 */
internal fun resolveDownloadInfo(progress: DownloadProgress?, isOnDisk: Boolean): EpisodeDownloadInfo =
    when {
        progress?.state == EpisodeDownloadState.Downloading -> EpisodeDownloadInfo(
            state = EpisodeDownloadState.Downloading,
            percent = progress.percent,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes
        )
        isOnDisk -> EpisodeDownloadInfo.Downloaded
        progress?.state == EpisodeDownloadState.Error -> EpisodeDownloadInfo(EpisodeDownloadState.Error)
        else -> EpisodeDownloadInfo.NotDownloaded
    }

internal fun toEpisodeUiItems(
    episodes: List<Pair<CachedEpisode, String>>,
    finishedIds: Set<Long>
): List<EpisodeUiItem> = episodes.map { (episode, searchText) ->
    EpisodeUiItem(episode = episode, isFinished = episode.id in finishedIds, searchText = searchText)
}

/** Episodes whose [EpisodeUiItem.searchText] contains [query], or null for a blank query. */
internal fun filterEpisodes(items: List<EpisodeUiItem>, query: String): List<EpisodeUiItem>? {
    if (query.isBlank()) return null
    val lowerQuery = query.lowercase()
    return items.filter { lowerQuery in it.searchText }
}

/** Lowercase searchable text for an episode: its title and its description without markup. */
internal fun buildEpisodeSearchText(title: String, description: String): String =
    (title + "\n" + htmlToPlainText(description)).lowercase()

private val HTML_TAG = Regex("<[^>]*>")
private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)
// "&amp;" goes last so "&amp;lt;" decodes to the text "&lt;", not "<".
private val NAMED_ENTITIES = listOf(
    "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&nbsp;" to " ",
    "&amp;" to "&"
)
private val WHITESPACE = Regex("\\s+")

/**
 * Removes HTML tags and decodes common entities, so searching for e.g. "href" or "amp"
 * doesn't match every episode with a link. Pure Kotlin (no Android HTML parser) so it can
 * run off the main thread and in unit tests.
 */
internal fun htmlToPlainText(html: String): String {
    if ('<' !in html && '&' !in html) return html
    var text = HTML_TAG.replace(html, " ")
    if ('&' in text) {
        text = NUMERIC_ENTITY.replace(text) { match ->
            val radix = if (match.groupValues[1].isEmpty()) 10 else 16
            match.groupValues[2].toIntOrNull(radix)
                ?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        for ((entity, replacement) in NAMED_ENTITIES) {
            text = text.replace(entity, replacement, ignoreCase = true)
        }
    }
    return WHITESPACE.replace(text, " ").trim()
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
