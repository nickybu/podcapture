package com.podcapture.ui.home

import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.EpisodePlaybackHistory

/*
 * Pure mapping, filtering and sorting for the Home screen. Kept free of Android and
 * repository types so it can be unit tested and so the ViewModel runs it only when its
 * inputs change instead of on every recomposition.
 */

/** Virtual audioFileId under which captures for a podcast episode are stored. */
internal fun episodeAudioFileId(episodeId: Long): String = "episode_$episodeId"

/** Local audio files that have been played, as History rows. Episode stand-in rows are skipped. */
internal fun buildRecentAudioFiles(
    files: List<AudioFile>,
    captureCounts: Map<String, Int>
): List<RecentItem.AudioFileRecent> =
    files
        .filter { it.lastPlayedAt != null && !it.id.startsWith("episode_") }
        .map { file ->
            RecentItem.AudioFileRecent(
                id = "file_${file.id}",
                title = file.name,
                subtitle = formatDuration(file.durationMs),
                durationMs = file.durationMs,
                lastPlayedAt = file.lastPlayedAt ?: 0L,
                captureCount = captureCounts[file.id] ?: 0
            )
        }

/** Played podcast episodes, as History rows. */
internal fun buildRecentEpisodes(
    history: List<EpisodePlaybackHistory>,
    captureCounts: Map<String, Int>,
    downloadedEpisodeIds: Set<Long>
): List<RecentItem.EpisodeRecent> =
    history.map { entry ->
        val id = episodeAudioFileId(entry.episodeId)
        RecentItem.EpisodeRecent(
            id = id,
            title = entry.episodeTitle,
            subtitle = entry.podcastTitle,
            durationMs = entry.duration * 1000L,
            lastPlayedAt = entry.lastPlayedAt,
            captureCount = captureCounts[id] ?: 0,
            episodeId = entry.episodeId,
            podcastId = entry.podcastId,
            artworkUrl = entry.podcastArtworkUrl,
            positionMs = entry.positionMs,
            isDownloaded = entry.episodeId in downloadedEpisodeIds
        )
    }

/** Files and episodes merged into one History list, most recently played first. */
internal fun mergeRecentItems(
    files: List<RecentItem.AudioFileRecent>,
    episodes: List<RecentItem.EpisodeRecent>
): List<RecentItem> = (files + episodes).sortedByDescending { it.lastPlayedAt }

/** History rows after applying the type chip, the search query and the sort direction. */
internal fun filterRecentItems(
    items: List<RecentItem>,
    filter: RecentFilterType,
    query: String,
    sortOrder: SortOrder
): List<RecentItem> {
    val typeFiltered = when (filter) {
        RecentFilterType.ALL -> items
        RecentFilterType.FILES -> items.filterIsInstance<RecentItem.AudioFileRecent>()
        RecentFilterType.EPISODES -> items.filterIsInstance<RecentItem.EpisodeRecent>()
        RecentFilterType.DOWNLOADED -> items.filterIsInstance<RecentItem.EpisodeRecent>()
            .filter { it.isDownloaded }
    }

    val searchFiltered = if (query.isBlank()) {
        typeFiltered
    } else {
        typeFiltered.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
        }
    }

    return when (sortOrder) {
        SortOrder.NEWEST -> searchFiltered.sortedByDescending { it.lastPlayedAt }
        SortOrder.OLDEST -> searchFiltered.sortedBy { it.lastPlayedAt }
    }
}

/** Counts shown on the History filter chips. */
internal data class RecentCounts(
    val files: Int = 0,
    val episodes: Int = 0,
    val downloaded: Int = 0
)

internal fun countRecentItems(items: List<RecentItem>): RecentCounts {
    var files = 0
    var episodes = 0
    var downloaded = 0
    for (item in items) {
        when (item) {
            is RecentItem.AudioFileRecent -> files++
            is RecentItem.EpisodeRecent -> {
                episodes++
                if (item.isDownloaded) downloaded++
            }
        }
    }
    return RecentCounts(files, episodes, downloaded)
}

/** Bookmarked podcasts and files in one list, most recently bookmarked first. */
internal fun combineBookmarks(
    podcasts: List<BookmarkedPodcast>,
    files: List<AudioFile>
): List<BookmarkItem> =
    (podcasts.map { BookmarkItem.PodcastBookmark(it) } + files.map { BookmarkItem.AudioFileBookmark(it) })
        .sortedByDescending { it.bookmarkedAt }

internal fun filterBookmarkItems(
    items: List<BookmarkItem>,
    filter: BookmarkFilterType
): List<BookmarkItem> = when (filter) {
    BookmarkFilterType.ALL -> items
    BookmarkFilterType.PODCASTS -> items.filterIsInstance<BookmarkItem.PodcastBookmark>()
    BookmarkFilterType.FILES -> items.filterIsInstance<BookmarkItem.AudioFileBookmark>()
}

/**
 * Returns [next] with its derived fields (filtered lists and chip counts) brought up to date.
 * Each derived field is recomputed only when one of its inputs differs from [previous];
 * otherwise the value carried over by `copy` is kept.
 */
internal fun deriveHomeState(previous: HomeUiState, next: HomeUiState): HomeUiState {
    val recentChanged = next.recentItems !== previous.recentItems
    val recentViewChanged = recentChanged ||
        next.recentFilterType != previous.recentFilterType ||
        next.historySearchQuery != previous.historySearchQuery ||
        next.recentSortOrder != previous.recentSortOrder
    val bookmarksChanged = next.bookmarkItems !== previous.bookmarkItems ||
        next.bookmarkFilterType != previous.bookmarkFilterType

    if (!recentViewChanged && !bookmarksChanged) return next

    val counts = if (recentChanged) countRecentItems(next.recentItems) else null
    return next.copy(
        filteredRecentItems = if (recentViewChanged) {
            filterRecentItems(next.recentItems, next.recentFilterType, next.historySearchQuery, next.recentSortOrder)
        } else {
            next.filteredRecentItems
        },
        recentFileCount = counts?.files ?: next.recentFileCount,
        recentEpisodeCount = counts?.episodes ?: next.recentEpisodeCount,
        downloadedEpisodeCount = counts?.downloaded ?: next.downloadedEpisodeCount,
        filteredBookmarkItems = if (bookmarksChanged) {
            filterBookmarkItems(next.bookmarkItems, next.bookmarkFilterType)
        } else {
            next.filteredBookmarkItems
        }
    )
}

internal fun formatDuration(ms: Long): String {
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
