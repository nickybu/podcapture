package com.podcapture.ui.home

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.EpisodePlaybackHistory
import org.junit.Test

class HomeListLogicTest {

    private fun audioFile(
        id: String,
        lastPlayedAt: Long? = null,
        durationMs: Long = 65_000,
        bookmarkedAt: Long? = null
    ) = AudioFile(
        id = id,
        name = "$id.mp3",
        filePath = "content://$id",
        durationMs = durationMs,
        format = "mp3",
        lastPlayedAt = lastPlayedAt,
        addedAt = 0L,
        isBookmarked = bookmarkedAt != null,
        bookmarkedAt = bookmarkedAt
    )

    private fun history(
        episodeId: Long,
        lastPlayedAt: Long,
        positionMs: Long = 0,
        title: String = "Episode $episodeId",
        podcastTitle: String = "Podcast"
    ) = EpisodePlaybackHistory(
        episodeId = episodeId,
        podcastId = 7L,
        podcastTitle = podcastTitle,
        podcastArtworkUrl = "https://example.com/art.png",
        episodeTitle = title,
        duration = 120,
        positionMs = positionMs,
        lastPlayedAt = lastPlayedAt
    )

    private fun podcast(id: Long, bookmarkedAt: Long) = BookmarkedPodcast(
        id = id,
        title = "Podcast $id",
        author = "",
        description = "",
        artworkUrl = "",
        feedUrl = "",
        bookmarkedAt = bookmarkedAt
    )

    private fun fileRecent(id: String, lastPlayedAt: Long, title: String = id) =
        RecentItem.AudioFileRecent(
            id = "file_$id", title = title, subtitle = "1:00", durationMs = 60_000,
            lastPlayedAt = lastPlayedAt, captureCount = 0
        )

    private fun episodeRecent(
        episodeId: Long,
        lastPlayedAt: Long,
        isDownloaded: Boolean = false,
        title: String = "Episode $episodeId",
        podcastTitle: String = "Podcast"
    ) = RecentItem.EpisodeRecent(
        id = "episode_$episodeId", title = title, subtitle = podcastTitle, durationMs = 60_000,
        lastPlayedAt = lastPlayedAt, captureCount = 0, episodeId = episodeId, podcastId = 1L,
        artworkUrl = "", positionMs = 0, isDownloaded = isDownloaded
    )

    // ==================== buildRecentAudioFiles ====================

    @Test
    fun `recent audio files skip unplayed files and episode stand-ins`() {
        val files = listOf(
            audioFile("a", lastPlayedAt = 10),
            audioFile("never-played"),
            audioFile("episode_42", lastPlayedAt = 20)
        )

        val result = buildRecentAudioFiles(files, captureCounts = emptyMap())

        assertThat(result.map { it.id }).containsExactly("file_a")
    }

    @Test
    fun `recent audio files take capture counts from the map and default to zero`() {
        val files = listOf(audioFile("a", lastPlayedAt = 10), audioFile("b", lastPlayedAt = 20))

        val result = buildRecentAudioFiles(files, captureCounts = mapOf("a" to 3))

        assertThat(result.associate { it.id to it.captureCount })
            .containsExactly("file_a", 3, "file_b", 0)
        assertThat(result.first().subtitle).isEqualTo("1:05")
    }

    // ==================== buildRecentEpisodes ====================

    @Test
    fun `recent episodes map counts by virtual id and download state by episode id`() {
        val result = buildRecentEpisodes(
            history = listOf(history(1, lastPlayedAt = 5, positionMs = 30_000), history(2, lastPlayedAt = 6)),
            captureCounts = mapOf("episode_1" to 2, "1" to 99),
            downloadedEpisodeIds = setOf(2L)
        )

        val first = result.single { it.episodeId == 1L }
        assertThat(first.id).isEqualTo("episode_1")
        assertThat(first.captureCount).isEqualTo(2)
        assertThat(first.isDownloaded).isFalse()
        assertThat(first.durationMs).isEqualTo(120_000)
        assertThat(first.positionMs).isEqualTo(30_000)

        val second = result.single { it.episodeId == 2L }
        assertThat(second.captureCount).isEqualTo(0)
        assertThat(second.isDownloaded).isTrue()
    }

    @Test
    fun `identical inputs build equal lists so distinctUntilChanged drops the emission`() {
        val history = listOf(history(1, lastPlayedAt = 5))
        val counts = mapOf("episode_1" to 1)

        val first = buildRecentEpisodes(history, counts, setOf(1L))
        val second = buildRecentEpisodes(history.map { it.copy(localFilePath = "/x", isFinished = true) }, counts, setOf(1L))

        assertThat(second).isEqualTo(first)
    }

    // ==================== mergeRecentItems / filterRecentItems ====================

    @Test
    fun `merge orders files and episodes by last played, newest first`() {
        val merged = mergeRecentItems(
            files = listOf(fileRecent("a", 10), fileRecent("b", 30)),
            episodes = listOf(episodeRecent(1, 20))
        )

        assertThat(merged.map { it.id }).containsExactly("file_b", "episode_1", "file_a").inOrder()
    }

    private val sample: List<RecentItem> = listOf(
        fileRecent("talk", 10, title = "Morning Talk"),
        episodeRecent(1, 30, isDownloaded = true, title = "Deep Dive", podcastTitle = "Science Hour"),
        episodeRecent(2, 20, isDownloaded = false, title = "News", podcastTitle = "Daily")
    )

    @Test
    fun `filter by type`() {
        assertThat(filterRecentItems(sample, RecentFilterType.FILES, "", SortOrder.NEWEST).map { it.id })
            .containsExactly("file_talk")
        assertThat(filterRecentItems(sample, RecentFilterType.EPISODES, "", SortOrder.NEWEST).map { it.id })
            .containsExactly("episode_1", "episode_2").inOrder()
        assertThat(filterRecentItems(sample, RecentFilterType.DOWNLOADED, "", SortOrder.NEWEST).map { it.id })
            .containsExactly("episode_1")
    }

    @Test
    fun `search matches title or subtitle ignoring case`() {
        assertThat(filterRecentItems(sample, RecentFilterType.ALL, "science", SortOrder.NEWEST).map { it.id })
            .containsExactly("episode_1")
        assertThat(filterRecentItems(sample, RecentFilterType.ALL, "MORNING", SortOrder.NEWEST).map { it.id })
            .containsExactly("file_talk")
        assertThat(filterRecentItems(sample, RecentFilterType.ALL, "   ", SortOrder.NEWEST)).hasSize(3)
    }

    @Test
    fun `sort order flips direction`() {
        assertThat(filterRecentItems(sample, RecentFilterType.ALL, "", SortOrder.NEWEST).map { it.lastPlayedAt })
            .containsExactly(30L, 20L, 10L).inOrder()
        assertThat(filterRecentItems(sample, RecentFilterType.ALL, "", SortOrder.OLDEST).map { it.lastPlayedAt })
            .containsExactly(10L, 20L, 30L).inOrder()
    }

    @Test
    fun `counts files, episodes and downloaded episodes`() {
        assertThat(countRecentItems(sample)).isEqualTo(RecentCounts(files = 1, episodes = 2, downloaded = 1))
        assertThat(countRecentItems(emptyList())).isEqualTo(RecentCounts())
    }

    // ==================== bookmarks ====================

    @Test
    fun `bookmarks combine podcasts and files, most recently bookmarked first`() {
        val combined = combineBookmarks(
            podcasts = listOf(podcast(1, bookmarkedAt = 100), podcast(2, bookmarkedAt = 300)),
            files = listOf(audioFile("f", bookmarkedAt = 200))
        )

        assertThat(combined.map { it.bookmarkedAt }).containsExactly(300L, 200L, 100L).inOrder()
        assertThat(filterBookmarkItems(combined, BookmarkFilterType.ALL)).hasSize(3)
        assertThat(filterBookmarkItems(combined, BookmarkFilterType.PODCASTS)).hasSize(2)
        assertThat(filterBookmarkItems(combined, BookmarkFilterType.FILES).single())
            .isInstanceOf(BookmarkItem.AudioFileBookmark::class.java)
    }

    // ==================== deriveHomeState ====================

    @Test
    fun `derived fields follow the recent items and filters`() {
        val initial = HomeUiState()
        val withItems = deriveHomeState(initial, initial.copy(recentItems = sample))

        assertThat(withItems.filteredRecentItems.map { it.id })
            .containsExactly("episode_1", "episode_2", "file_talk").inOrder()
        assertThat(withItems.recentFileCount).isEqualTo(1)
        assertThat(withItems.recentEpisodeCount).isEqualTo(2)
        assertThat(withItems.downloadedEpisodeCount).isEqualTo(1)

        val filtered = deriveHomeState(withItems, withItems.copy(recentFilterType = RecentFilterType.DOWNLOADED))
        assertThat(filtered.filteredRecentItems.map { it.id }).containsExactly("episode_1")
        assertThat(filtered.recentEpisodeCount).isEqualTo(2)

        val searched = deriveHomeState(filtered, filtered.copy(recentFilterType = RecentFilterType.ALL, historySearchQuery = "news"))
        assertThat(searched.filteredRecentItems.map { it.id }).containsExactly("episode_2")
    }

    @Test
    fun `unrelated changes keep the derived lists without recomputing them`() {
        val withItems = deriveHomeState(HomeUiState(), HomeUiState(recentItems = sample))

        val navigated = deriveHomeState(withItems, withItems.copy(navigateToPlayer = "a"))

        assertThat(navigated.filteredRecentItems).isSameInstanceAs(withItems.filteredRecentItems)
        assertThat(navigated.filteredBookmarkItems).isSameInstanceAs(withItems.filteredBookmarkItems)
    }

    @Test
    fun `bookmark filter is derived from items and filter type`() {
        val items = combineBookmarks(listOf(podcast(1, 100)), listOf(audioFile("f", bookmarkedAt = 200)))
        val base = HomeUiState(bookmarkFilterType = BookmarkFilterType.ALL)

        val all = deriveHomeState(base, base.copy(bookmarkItems = items))
        assertThat(all.filteredBookmarkItems).hasSize(2)

        val podcastsOnly = deriveHomeState(all, all.copy(bookmarkFilterType = BookmarkFilterType.PODCASTS))
        assertThat(podcastsOnly.filteredBookmarkItems.single())
            .isInstanceOf(BookmarkItem.PodcastBookmark::class.java)
    }

    @Test
    fun `formats durations with and without hours`() {
        assertThat(formatDuration(65_000)).isEqualTo("1:05")
        assertThat(formatDuration(3_725_000)).isEqualTo("1:02:05")
        assertThat(formatDuration(0)).isEqualTo("0:00")
    }
}
