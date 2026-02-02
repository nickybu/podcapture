package com.podcapture.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

enum class FormattingType {
    HIGHLIGHT,
    BOLD
}

object TranscriptFormattingParser {

    private val HIGHLIGHT_PATTERN = Regex("==([^=]+)==")
    private val BOLD_PATTERN = Regex("\\*\\*([^*]+)\\*\\*")

    /**
     * Strips all formatting markers (== and **) from text.
     */
    fun stripFormatting(text: String): String {
        return text
            .replace(HIGHLIGHT_PATTERN, "$1")
            .replace(BOLD_PATTERN, "$1")
    }

    /**
     * Validates that the formatted text only differs from the original by formatting markers.
     * Returns true if valid (stripped formatted text matches original).
     */
    fun validateFormatting(original: String, formatted: String): Boolean {
        return stripFormatting(formatted) == original
    }

    /**
     * Applies formatting to a text range.
     * Handles nested and overlapping formatting by inserting markers at positions.
     */
    fun applyFormatting(text: String, start: Int, end: Int, type: FormattingType): String {
        if (start < 0 || end > text.length || start >= end) return text

        val selectedText = text.substring(start, end)
        val markers = when (type) {
            FormattingType.HIGHLIGHT -> "==" to "=="
            FormattingType.BOLD -> "**" to "**"
        }

        // Check if the selection is already fully wrapped with this formatting
        val existingPattern = when (type) {
            FormattingType.HIGHLIGHT -> HIGHLIGHT_PATTERN
            FormattingType.BOLD -> BOLD_PATTERN
        }

        // If the entire selection matches the pattern, remove the formatting (toggle off)
        if (existingPattern.matches(selectedText)) {
            val content = existingPattern.find(selectedText)?.groupValues?.get(1) ?: selectedText
            return text.substring(0, start) + content + text.substring(end)
        }

        // Check if we're inside an existing formatting span and need to extend it
        // For now, just wrap the selection with new markers
        val wrappedText = "${markers.first}$selectedText${markers.second}"
        return text.substring(0, start) + wrappedText + text.substring(end)
    }

    /**
     * Removes specific formatting from a text range.
     */
    fun removeFormatting(text: String, start: Int, end: Int, type: FormattingType): String {
        if (start < 0 || end > text.length || start >= end) return text

        val selectedText = text.substring(start, end)
        val pattern = when (type) {
            FormattingType.HIGHLIGHT -> HIGHLIGHT_PATTERN
            FormattingType.BOLD -> BOLD_PATTERN
        }

        // Remove all instances of this formatting type from the selection
        val cleanedText = selectedText.replace(pattern, "$1")
        return text.substring(0, start) + cleanedText + text.substring(end)
    }

    /**
     * Removes all formatting from a text range.
     */
    fun clearFormatting(text: String, start: Int, end: Int): String {
        if (start < 0 || end > text.length || start >= end) return text

        val selectedText = text.substring(start, end)
        val cleanedText = stripFormatting(selectedText)
        return text.substring(0, start) + cleanedText + text.substring(end)
    }

    /**
     * Parses formatted text into an AnnotatedString with visual styling.
     * Supports == for highlight and ** for bold.
     */
    fun parseToAnnotatedString(
        text: String,
        highlightColor: Color,
        boldColor: Color? = null
    ): AnnotatedString {
        return buildAnnotatedString {
            var currentIndex = 0
            val segments = mutableListOf<FormattedSegment>()

            // Find all formatting spans
            val highlightMatches = HIGHLIGHT_PATTERN.findAll(text).toList()
            val boldMatches = BOLD_PATTERN.findAll(text).toList()

            // Combine all matches with their types
            val allMatches = mutableListOf<MatchWithType>()
            highlightMatches.forEach { allMatches.add(MatchWithType(it, FormattingType.HIGHLIGHT)) }
            boldMatches.forEach { allMatches.add(MatchWithType(it, FormattingType.BOLD)) }

            // Sort by start position
            allMatches.sortBy { it.match.range.first }

            // Build segments, handling overlaps by processing in order
            for (matchWithType in allMatches) {
                val match = matchWithType.match
                val matchStart = match.range.first
                val matchEnd = match.range.last + 1

                // Skip if this match overlaps with a previous one we've already processed
                if (matchStart < currentIndex) continue

                // Add plain text before this match
                if (matchStart > currentIndex) {
                    segments.add(FormattedSegment(text.substring(currentIndex, matchStart), emptySet()))
                }

                // Add the formatted content (without markers)
                val content = match.groupValues[1]
                segments.add(FormattedSegment(content, setOf(matchWithType.type)))

                currentIndex = matchEnd
            }

            // Add remaining plain text
            if (currentIndex < text.length) {
                segments.add(FormattedSegment(text.substring(currentIndex), emptySet()))
            }

            // Build the AnnotatedString from segments
            for (segment in segments) {
                if (segment.formatting.isEmpty()) {
                    append(segment.text)
                } else {
                    val style = buildSpanStyle(segment.formatting, highlightColor, boldColor)
                    withStyle(style) {
                        append(segment.text)
                    }
                }
            }
        }
    }

    /**
     * Converts character indices in the formatted text to indices in the stripped text.
     * Used for selection mapping.
     */
    fun formattedIndexToStrippedIndex(formattedText: String, formattedIndex: Int): Int {
        val beforeIndex = formattedText.substring(0, formattedIndex.coerceAtMost(formattedText.length))
        return stripFormatting(beforeIndex).length
    }

    /**
     * Converts character indices in the stripped text to indices in the formatted text.
     * Used for selection mapping.
     */
    fun strippedIndexToFormattedIndex(formattedText: String, strippedIndex: Int): Int {
        var formattedIdx = 0
        var strippedIdx = 0

        while (formattedIdx < formattedText.length && strippedIdx < strippedIndex) {
            // Check if we're at a formatting marker
            val remaining = formattedText.substring(formattedIdx)

            when {
                remaining.startsWith("==") -> {
                    formattedIdx += 2 // Skip the marker
                }
                remaining.startsWith("**") -> {
                    formattedIdx += 2 // Skip the marker
                }
                else -> {
                    formattedIdx++
                    strippedIdx++
                }
            }
        }

        return formattedIdx
    }

    private fun buildSpanStyle(
        formatting: Set<FormattingType>,
        highlightColor: Color,
        boldColor: Color?
    ): SpanStyle {
        var style = SpanStyle()

        if (FormattingType.HIGHLIGHT in formatting) {
            style = style.copy(background = highlightColor)
        }

        if (FormattingType.BOLD in formatting) {
            style = style.copy(
                fontWeight = FontWeight.Bold,
                color = boldColor ?: Color.Unspecified
            )
        }

        return style
    }

    private data class MatchWithType(
        val match: MatchResult,
        val type: FormattingType
    )

    private data class FormattedSegment(
        val text: String,
        val formatting: Set<FormattingType>
    )
}
