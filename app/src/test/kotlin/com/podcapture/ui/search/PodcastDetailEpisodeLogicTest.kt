package com.podcapture.ui.search

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.download.DownloadProgress
import org.junit.Test

class PodcastDetailEpisodeLogicTest {

    private fun episode(id: Long, title: String = "Episode $id", description: String = "") =
        CachedEpisode(
            id = id,
            podcastId = 1L,
            title = title,
            description = description,
            publishedDate = 0L,
            duration = 0,
            audioUrl = "https://example.com/$id.mp3",
            cachedAt = 0L
        )

    private fun item(id: Long, title: String, description: String = "") =
        EpisodeUiItem(episode(id, title, description), searchText = buildEpisodeSearchText(title, description))

    // ---- search text ----

    @Test
    fun `search text is lowercase title and description`() {
        assertThat(buildEpisodeSearchText("The BIG Show", "All About Kotlin"))
            .isEqualTo("the big show\nall about kotlin")
    }

    @Test
    fun `html tags are stripped and entities decoded`() {
        val html = """<p>Guest: <a href="https://x.y">Ada &amp; Grace</a></p><br/>Notes&nbsp;&#8212;&#x41;"""
        assertThat(htmlToPlainText(html)).isEqualTo("Guest: Ada & Grace Notes —A")
    }

    @Test
    fun `double-escaped entities decode once`() {
        assertThat(htmlToPlainText("a &amp;lt; b")).isEqualTo("a &lt; b")
    }

    @Test
    fun `plain text is returned unchanged`() {
        val text = "No markup here"
        assertThat(htmlToPlainText(text)).isSameInstanceAs(text)
    }

    // ---- filter ----

    @Test
    fun `blank query means no filter`() {
        val items = listOf(item(1, "One"))
        assertThat(filterEpisodes(items, "")).isNull()
        assertThat(filterEpisodes(items, "   ")).isNull()
    }

    @Test
    fun `filter matches title or description case-insensitively`() {
        val a = item(1, "Kotlin Coroutines")
        val b = item(2, "Compose", "<p>We talk about <b>coroutines</b> too</p>")
        val c = item(3, "Gradle")

        assertThat(filterEpisodes(listOf(a, b, c), "COROUTINES")).containsExactly(a, b).inOrder()
    }

    @Test
    fun `filter does not match markup`() {
        val a = item(1, "Links", """<a href="https://example.com">site</a>""")

        assertThat(filterEpisodes(listOf(a), "href")).isEmpty()
        assertThat(filterEpisodes(listOf(a), "site")).containsExactly(a)
    }

    @Test
    fun `filter returns the same item instances`() {
        val a = item(1, "Alpha")
        assertThat(filterEpisodes(listOf(a), "alp")!!.single()).isSameInstanceAs(a)
    }

    // ---- list items ----

    @Test
    fun `finished flags come from the finished id set`() {
        val items = toEpisodeUiItems(
            listOf(episode(1) to "one", episode(2) to "two"),
            finishedIds = setOf(2L)
        )

        assertThat(items.map { it.episode.id to it.isFinished })
            .containsExactly(1L to false, 2L to true).inOrder()
        assertThat(items.map { it.searchText }).containsExactly("one", "two").inOrder()
    }

    // ---- download state ----

    private fun progress(state: EpisodeDownloadState, percent: Int = 0) =
        DownloadProgress(episodeId = 1L, state = state, percent = percent, downloadedBytes = 10, totalBytes = 100)

    @Test
    fun `active download shows progress`() {
        val info = resolveDownloadInfo(progress(EpisodeDownloadState.Downloading, 42), isOnDisk = false)
        assertThat(info).isEqualTo(
            EpisodeDownloadInfo(EpisodeDownloadState.Downloading, percent = 42, downloadedBytes = 10, totalBytes = 100)
        )
    }

    @Test
    fun `file on disk without an active download is downloaded`() {
        assertThat(resolveDownloadInfo(null, isOnDisk = true)).isEqualTo(EpisodeDownloadInfo.Downloaded)
    }

    @Test
    fun `stale downloaded entry for a deleted file shows not downloaded`() {
        assertThat(resolveDownloadInfo(progress(EpisodeDownloadState.Downloaded, 100), isOnDisk = false))
            .isEqualTo(EpisodeDownloadInfo.NotDownloaded)
    }

    @Test
    fun `failed download shows error unless a file is on disk`() {
        assertThat(resolveDownloadInfo(progress(EpisodeDownloadState.Error), isOnDisk = false).state)
            .isEqualTo(EpisodeDownloadState.Error)
        assertThat(resolveDownloadInfo(progress(EpisodeDownloadState.Error), isOnDisk = true))
            .isEqualTo(EpisodeDownloadInfo.Downloaded)
    }

    @Test
    fun `nothing known means not downloaded`() {
        assertThat(resolveDownloadInfo(null, isOnDisk = false)).isEqualTo(EpisodeDownloadInfo.NotDownloaded)
    }
}
