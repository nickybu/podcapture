package com.podcapture.ui.captures

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.db.CaptureSourceRow
import com.podcapture.data.db.CaptureTagRow
import com.podcapture.data.model.Capture
import com.podcapture.data.model.Tag
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CapturesLogicTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun millis(dateTime: String, zone: ZoneId = utc): Long =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun capture(
        id: String,
        audioFileId: String = "file-1",
        transcription: String = "some words",
        notes: String? = null,
        createdAt: Long = 0L
    ) = Capture(
        id = id,
        audioFileId = audioFileId,
        timestampMs = 1_000,
        windowStartMs = 0,
        windowEndMs = 2_000,
        transcription = transcription,
        notes = notes,
        createdAt = createdAt
    )

    private val tagA = Tag(id = "a", name = "alpha")
    private val tagB = Tag(id = "b", name = "Beta")
    private val tagC = Tag(id = "c", name = "gamma")

    private fun item(
        id: String,
        tags: List<Tag> = emptyList(),
        source: CaptureSource = CaptureSource(CaptureSourceKind.File, "file.mp3"),
        transcription: String = "some words",
        notes: String? = null,
        createdAt: Long = 0L
    ) = CaptureListItem(
        capture = capture(id, transcription = transcription, notes = notes, createdAt = createdAt),
        tags = tags,
        source = source
    )

    // ---- Source resolution and joining ----

    @Test
    fun `episode source prefers cached episode title and carries podcast title`() {
        val source = resolveCaptureSource(
            "episode_42",
            CaptureSourceRow("episode_42", fileName = "Old name", episodeTitle = "Ep 42", podcastTitle = "The Show")
        )
        assertThat(source).isEqualTo(CaptureSource(CaptureSourceKind.Podcast, "Ep 42", "The Show"))
    }

    @Test
    fun `episode source falls back to downloaded file name, then a placeholder`() {
        assertThat(
            resolveCaptureSource("episode_42", CaptureSourceRow("episode_42", "Ep 42 (downloaded)", null, null))
        ).isEqualTo(CaptureSource(CaptureSourceKind.Podcast, "Ep 42 (downloaded)", null))
        assertThat(resolveCaptureSource("episode_42", null))
            .isEqualTo(CaptureSource(CaptureSourceKind.Podcast, "Unknown episode", null))
    }

    @Test
    fun `file source uses the audio file name, then a placeholder`() {
        assertThat(resolveCaptureSource("f1", CaptureSourceRow("f1", "talk.mp3", null, null)))
            .isEqualTo(CaptureSource(CaptureSourceKind.File, "talk.mp3"))
        assertThat(resolveCaptureSource("f1", null))
            .isEqualTo(CaptureSource(CaptureSourceKind.File, "Unknown file"))
    }

    @Test
    fun `buildCaptureItems joins tags and sources and keeps capture order`() {
        val captures = listOf(
            capture("c2", audioFileId = "episode_7"),
            capture("c1", audioFileId = "f1"),
            capture("c3", audioFileId = "f1")
        )
        val tagRows = listOf(
            CaptureTagRow("c1", tagA),
            CaptureTagRow("c1", tagB),
            CaptureTagRow("c2", tagB)
        )
        val sources = listOf(
            CaptureSourceRow("episode_7", null, "Ep 7", "Pod"),
            CaptureSourceRow("f1", "talk.mp3", null, null)
        )

        val items = buildCaptureItems(captures, tagRows, sources)

        assertThat(items.map { it.capture.id }).containsExactly("c2", "c1", "c3").inOrder()
        assertThat(items[0].tags).containsExactly(tagB)
        assertThat(items[0].source).isEqualTo(CaptureSource(CaptureSourceKind.Podcast, "Ep 7", "Pod"))
        assertThat(items[1].tags).containsExactly(tagA, tagB).inOrder()
        assertThat(items[1].source.title).isEqualTo("talk.mp3")
        assertThat(items[2].tags).isEmpty()
    }

    @Test
    fun `tagsInUse is distinct and sorted case-insensitively`() {
        val items = listOf(item("1", listOf(tagC, tagA)), item("2", listOf(tagB, tagA)))
        assertThat(tagsInUse(items)).containsExactly(tagA, tagB, tagC).inOrder()
    }

    // ---- Query matching ----

    @Test
    fun `blank query matches everything`() {
        assertThat(matchesQuery(item("1"), "")).isTrue()
        assertThat(matchesQuery(item("1"), "   ")).isTrue()
    }

    @Test
    fun `query matches transcription, notes, source title and podcast title case-insensitively`() {
        val it = item(
            "1",
            transcription = "The Quick brown fox",
            notes = "Remember this",
            source = CaptureSource(CaptureSourceKind.Podcast, "Episode Title", "Great Podcast")
        )
        assertThat(matchesQuery(it, "quick")).isTrue()
        assertThat(matchesQuery(it, "REMEMBER")).isTrue()
        assertThat(matchesQuery(it, "episode tit")).isTrue()
        assertThat(matchesQuery(it, "great")).isTrue()
        assertThat(matchesQuery(it, "slow")).isFalse()
    }

    @Test
    fun `every query word must match, possibly in different fields`() {
        val it = item("1", transcription = "brown fox", notes = "lazy dog")
        assertThat(matchesQuery(it, "fox dog")).isTrue()
        assertThat(matchesQuery(it, "  fox   dog ")).isTrue()
        assertThat(matchesQuery(it, "fox cat")).isFalse()
    }

    @Test
    fun `null notes and subtitle do not break matching`() {
        val it = item("1", transcription = "hello", notes = null)
        assertThat(matchesQuery(it, "hello")).isTrue()
        assertThat(matchesQuery(it, "null")).isFalse()
    }

    // ---- Tag filter (AND) ----

    @Test
    fun `tag filter requires every selected tag`() {
        val ab = item("ab", listOf(tagA, tagB))
        val a = item("a", listOf(tagA))
        val none = item("none")

        assertThat(matchesTags(ab, emptySet())).isTrue()
        assertThat(matchesTags(none, emptySet())).isTrue()
        assertThat(matchesTags(ab, setOf("a"))).isTrue()
        assertThat(matchesTags(a, setOf("a"))).isTrue()
        assertThat(matchesTags(ab, setOf("a", "b"))).isTrue()
        assertThat(matchesTags(a, setOf("a", "b"))).isFalse()
        assertThat(matchesTags(none, setOf("a"))).isFalse()
    }

    // ---- Source filter ----

    @Test
    fun `source filter splits podcasts from files`() {
        val pod = item("p", source = CaptureSource(CaptureSourceKind.Podcast, "Ep"))
        val file = item("f", source = CaptureSource(CaptureSourceKind.File, "f.mp3"))

        assertThat(filterCaptures(listOf(pod, file), "", emptySet(), CaptureSourceFilter.All))
            .containsExactly(pod, file).inOrder()
        assertThat(filterCaptures(listOf(pod, file), "", emptySet(), CaptureSourceFilter.Podcasts))
            .containsExactly(pod)
        assertThat(filterCaptures(listOf(pod, file), "", emptySet(), CaptureSourceFilter.Files))
            .containsExactly(file)
    }

    @Test
    fun `filters combine`() {
        val match = item(
            "m", listOf(tagA, tagB), CaptureSource(CaptureSourceKind.Podcast, "Ep"), transcription = "needle"
        )
        val wrongSource = item(
            "s", listOf(tagA, tagB), CaptureSource(CaptureSourceKind.File, "f"), transcription = "needle"
        )
        val missingTag = item(
            "t", listOf(tagA), CaptureSource(CaptureSourceKind.Podcast, "Ep"), transcription = "needle"
        )
        val noText = item(
            "q", listOf(tagA, tagB), CaptureSource(CaptureSourceKind.Podcast, "Ep"), transcription = "hay"
        )

        val result = filterCaptures(
            listOf(match, wrongSource, missingTag, noText), "needle", setOf("a", "b"), CaptureSourceFilter.Podcasts
        )
        assertThat(result).containsExactly(match)
    }

    // ---- Day grouping ----

    @Test
    fun `groups by local day, newest day and newest capture first`() {
        val items = listOf(
            item("old", createdAt = millis("2026-10-28T09:00")),
            item("lateToday", createdAt = millis("2026-10-30T22:00")),
            item("earlyToday", createdAt = millis("2026-10-30T00:30")),
            item("yesterday", createdAt = millis("2026-10-29T23:59"))
        )

        val sections = groupByDay(items, utc)

        assertThat(sections.map { it.day }).containsExactly(
            LocalDate.parse("2026-10-30"), LocalDate.parse("2026-10-29"), LocalDate.parse("2026-10-28")
        ).inOrder()
        assertThat(sections[0].items.map { it.capture.id }).containsExactly("lateToday", "earlyToday").inOrder()
        assertThat(sections[1].items.map { it.capture.id }).containsExactly("yesterday")
        assertThat(sections[2].items.map { it.capture.id }).containsExactly("old")
    }

    @Test
    fun `day boundaries follow the given zone`() {
        // 23:30 UTC is already the next day in Amsterdam (UTC+1 in winter).
        val at = millis("2026-11-30T23:30")
        assertThat(groupByDay(listOf(item("x", createdAt = at)), utc).single().day)
            .isEqualTo(LocalDate.parse("2026-11-30"))
        assertThat(groupByDay(listOf(item("x", createdAt = at)), ZoneId.of("Europe/Amsterdam")).single().day)
            .isEqualTo(LocalDate.parse("2026-12-01"))
    }

    @Test
    fun `empty input gives no sections`() {
        assertThat(groupByDay(emptyList(), utc)).isEmpty()
    }

    @Test
    fun `day labels`() {
        val today = LocalDate.parse("2026-10-30")
        assertThat(dayLabel(today, today, Locale.US)).isEqualTo("Today")
        assertThat(dayLabel(LocalDate.parse("2026-10-29"), today, Locale.US)).isEqualTo("Yesterday")
        assertThat(dayLabel(LocalDate.parse("2026-03-03"), today, Locale.US)).isEqualTo("Tue, Mar 3")
        assertThat(dayLabel(LocalDate.parse("2025-12-31"), today, Locale.US)).isEqualTo("Dec 31, 2025")
    }

    // ---- Formatting helpers ----

    @Test
    fun `formatTimestamp matches the Viewer format`() {
        assertThat(formatTimestamp(0)).isEqualTo("00:00")
        assertThat(formatTimestamp(65_000)).isEqualTo("01:05")
        assertThat(formatTimestamp(3_725_000)).isEqualTo("1:02:05")
    }

    @Test
    fun `short transcript is unchanged`() {
        assertThat(transcriptPreview("short ==text==", maxChars = 50)).isEqualTo("short ==text==")
    }

    @Test
    fun `long transcript is cut at a word boundary with an ellipsis`() {
        val text = "one two three four five six seven eight nine ten"
        assertThat(transcriptPreview(text, maxChars = 20)).isEqualTo("one two three four…")
    }

    @Test
    fun `highlight cut in half is closed`() {
        val text = "aaa ==bbb ccc ddd eee== fff"
        assertThat(transcriptPreview(text, maxChars = 14)).isEqualTo("aaa ==bbb ccc==…")
    }

    @Test
    fun `dangling opening marker or half marker at the cut is dropped`() {
        // No space to cut at, so the cut is hard.
        val text = "aaaaaaaaaa==bbbbbbbbbb=="
        assertThat(transcriptPreview(text, maxChars = 12)).isEqualTo("aaaaaaaaaa…")
        assertThat(transcriptPreview(text, maxChars = 11)).isEqualTo("aaaaaaaaaa…")
        assertThat(transcriptPreview("aaaaaaaaaa**bbbbbbbbbb**", maxChars = 12)).isEqualTo("aaaaaaaaaa…")
    }
}
