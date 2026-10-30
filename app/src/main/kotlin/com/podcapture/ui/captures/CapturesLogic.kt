package com.podcapture.ui.captures

import com.podcapture.data.db.CaptureSourceRow
import com.podcapture.data.db.CaptureTagRow
import com.podcapture.data.model.Capture
import com.podcapture.data.model.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Pure joining, filtering and grouping for the global Captures screen. Kept free of Android
 * and repository types so it can be unit tested and run off the main thread by the ViewModel.
 */

private const val EPISODE_PREFIX = "episode_"

/** Which captures the source chips let through. */
enum class CaptureSourceFilter { All, Podcasts, Files }

enum class CaptureSourceKind { Podcast, File }

/**
 * Where a capture was taken: an episode ([subtitle] is the podcast title, when known) or a
 * local audio file.
 */
data class CaptureSource(
    val kind: CaptureSourceKind,
    val title: String,
    val subtitle: String? = null
)

/** A capture with its tags and source, as shown in one row of the Captures list. */
data class CaptureListItem(
    val capture: Capture,
    val tags: List<Tag>,
    val source: CaptureSource
)

/** The captures taken on one calendar day, newest first. */
data class CaptureDaySection(
    val day: LocalDate,
    val items: List<CaptureListItem>
)

/**
 * Resolves the display source of [audioFileId] from its [row] (null when the query returned
 * none). Episodes prefer the cached episode title and fall back to the downloaded file's row,
 * which is named after the episode, so captures stay labelled after a podcast is unbookmarked.
 */
internal fun resolveCaptureSource(audioFileId: String, row: CaptureSourceRow?): CaptureSource =
    if (audioFileId.startsWith(EPISODE_PREFIX)) {
        CaptureSource(
            kind = CaptureSourceKind.Podcast,
            title = row?.episodeTitle?.takeIf { it.isNotBlank() }
                ?: row?.fileName?.takeIf { it.isNotBlank() }
                ?: "Unknown episode",
            subtitle = row?.podcastTitle?.takeIf { it.isNotBlank() }
        )
    } else {
        CaptureSource(
            kind = CaptureSourceKind.File,
            title = row?.fileName?.takeIf { it.isNotBlank() } ?: "Unknown file"
        )
    }

/**
 * Joins captures with their tags and sources in memory, keeping the order of [captures].
 * Tags keep the order of [tagRows] (the query sorts them by name).
 */
internal fun buildCaptureItems(
    captures: List<Capture>,
    tagRows: List<CaptureTagRow>,
    sourceRows: List<CaptureSourceRow>
): List<CaptureListItem> {
    val tagsByCapture = tagRows.groupBy({ it.captureId }, { it.tag })
    val rowsById = sourceRows.associateBy { it.audioFileId }
    val sources = HashMap<String, CaptureSource>()
    return captures.map { capture ->
        CaptureListItem(
            capture = capture,
            tags = tagsByCapture[capture.id].orEmpty(),
            source = sources.getOrPut(capture.audioFileId) {
                resolveCaptureSource(capture.audioFileId, rowsById[capture.audioFileId])
            }
        )
    }
}

/** Distinct tags used by at least one capture, by name (case-insensitive). */
internal fun tagsInUse(items: List<CaptureListItem>): List<Tag> =
    items.asSequence()
        .flatMap { it.tags }
        .distinctBy { it.id }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .toList()

/**
 * True when every whitespace-separated word of [query] appears (case-insensitively) in the
 * transcription, notes, source title or podcast title. A blank query matches everything.
 * Formatting markers (== and **) in the formatted transcript are not searched; the plain
 * transcription holds the same words.
 */
internal fun matchesQuery(item: CaptureListItem, query: String): Boolean {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    val fields = listOfNotNull(
        item.capture.transcription,
        item.capture.notes,
        item.source.title,
        item.source.subtitle
    )
    return terms.all { term -> fields.any { it.contains(term, ignoreCase = true) } }
}

/** True when the item carries every tag in [selectedTagIds] (AND semantics). */
internal fun matchesTags(item: CaptureListItem, selectedTagIds: Set<String>): Boolean {
    if (selectedTagIds.isEmpty()) return true
    val ids = item.tags.mapTo(HashSet()) { it.id }
    return ids.containsAll(selectedTagIds)
}

internal fun matchesSource(item: CaptureListItem, filter: CaptureSourceFilter): Boolean =
    when (filter) {
        CaptureSourceFilter.All -> true
        CaptureSourceFilter.Podcasts -> item.source.kind == CaptureSourceKind.Podcast
        CaptureSourceFilter.Files -> item.source.kind == CaptureSourceKind.File
    }

/** Applies the query, tag and source filters, keeping order. */
internal fun filterCaptures(
    items: List<CaptureListItem>,
    query: String,
    selectedTagIds: Set<String>,
    sourceFilter: CaptureSourceFilter
): List<CaptureListItem> =
    items.filter {
        matchesSource(it, sourceFilter) && matchesTags(it, selectedTagIds) && matchesQuery(it, query)
    }

/**
 * Groups [items] into sections by the local day of [Capture.createdAt] in [zone]. Sections come
 * out newest day first and items newest first within a day, whatever the input order.
 */
internal fun groupByDay(items: List<CaptureListItem>, zone: ZoneId): List<CaptureDaySection> =
    items
        .sortedByDescending { it.capture.createdAt }
        .groupBy { Instant.ofEpochMilli(it.capture.createdAt).atZone(zone).toLocalDate() }
        .map { (day, dayItems) -> CaptureDaySection(day, dayItems) }

/** "Today", "Yesterday", "Mon, Mar 3" this year, else "Mar 3, 2024". */
internal fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
    when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
        else -> day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
    }

/** Playback position as "mm:ss", or "h:mm:ss" from an hour up (as in the Viewer). */
internal fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Shortens a (possibly formatted) transcript to about [maxChars], cutting at a word boundary
 * and adding an ellipsis. A highlight or bold span cut in half is closed again so the preview
 * renders it instead of showing a stray "==" or "**".
 */
internal fun transcriptPreview(text: String, maxChars: Int = 280): String {
    if (text.length <= maxChars) return text
    val lastSpace = text.lastIndexOf(' ', startIndex = maxChars)
    var cut = text.substring(0, if (lastSpace > maxChars / 2) lastSpace else maxChars).trimEnd()
    // Don't end on half of a marker.
    if (cut.endsWith("=") && !cut.endsWith("==")) cut = cut.dropLast(1)
    if (cut.endsWith("*") && !cut.endsWith("**")) cut = cut.dropLast(1)
    cut = balanceMarker(cut, "==")
    cut = balanceMarker(cut, "**")
    return "$cut…"
}

/** Closes an unpaired [marker] in [text], or drops it when nothing follows it. */
private fun balanceMarker(text: String, marker: String): String {
    if (Regex(Regex.escape(marker)).findAll(text).count() % 2 == 0) return text
    return if (text.endsWith(marker)) text.dropLast(marker.length).trimEnd() else text + marker
}
