package com.podcapture.data.rss

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.rss.RssFeedParser.Companion.UNKNOWN_PUBLISHED_DATE
import com.podcapture.data.rss.RssFeedParser.Companion.parsePubDateSeconds
import org.junit.Test

class RssFeedParserPubDateTest {

    @Test
    fun `parses RFC 822 pubDate with zone name`() {
        assertThat(parsePubDateSeconds("Tue, 03 Jun 2025 10:00:00 GMT")).isEqualTo(1748944800L)
    }

    @Test
    fun `parses RFC 822 pubDate with numeric offset`() {
        assertThat(parsePubDateSeconds("Tue, 03 Jun 2025 12:00:00 +0200")).isEqualTo(1748944800L)
    }

    @Test
    fun `parses ISO 8601 dc date`() {
        assertThat(parsePubDateSeconds("2025-06-03T10:00:00Z")).isEqualTo(1748944800L)
        assertThat(parsePubDateSeconds("2025-06-03T11:00:00+01:00")).isEqualTo(1748944800L)
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertThat(parsePubDateSeconds("  Tue, 03 Jun 2025 10:00:00 GMT\n")).isEqualTo(1748944800L)
    }

    @Test
    fun `missing pubDate is unknown, not now`() {
        assertThat(parsePubDateSeconds("")).isEqualTo(UNKNOWN_PUBLISHED_DATE)
        assertThat(parsePubDateSeconds("   ")).isEqualTo(UNKNOWN_PUBLISHED_DATE)
    }

    @Test
    fun `unparseable pubDate is unknown, not now`() {
        assertThat(parsePubDateSeconds("sometime last week")).isEqualTo(UNKNOWN_PUBLISHED_DATE)
    }

    @Test
    fun `unknown date sorts after every real date when ordered newest first`() {
        val dates = listOf(
            parsePubDateSeconds("garbage"),
            parsePubDateSeconds("Tue, 03 Jun 2025 10:00:00 GMT"),
            parsePubDateSeconds(""),
            parsePubDateSeconds("Mon, 01 Jan 2001 00:00:00 GMT")
        )
        // Mirrors ORDER BY publishedDate DESC in PodcastDao.
        val sorted = dates.sortedDescending()
        assertThat(sorted.take(2)).containsExactly(1748944800L, 978307200L).inOrder()
        assertThat(sorted.drop(2)).containsExactly(UNKNOWN_PUBLISHED_DATE, UNKNOWN_PUBLISHED_DATE)
    }
}
