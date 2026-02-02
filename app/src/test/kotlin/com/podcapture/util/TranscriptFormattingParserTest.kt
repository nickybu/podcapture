package com.podcapture.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptFormattingParserTest {

    // ==================== stripFormatting tests ====================

    @Test
    fun `stripFormatting removes highlight markers`() {
        val input = "This is ==highlighted== text"
        val result = TranscriptFormattingParser.stripFormatting(input)
        assertThat(result).isEqualTo("This is highlighted text")
    }

    @Test
    fun `stripFormatting removes bold markers`() {
        val input = "This is **bold** text"
        val result = TranscriptFormattingParser.stripFormatting(input)
        assertThat(result).isEqualTo("This is bold text")
    }

    @Test
    fun `stripFormatting removes multiple highlights`() {
        val input = "==First== and ==second== highlights"
        val result = TranscriptFormattingParser.stripFormatting(input)
        assertThat(result).isEqualTo("First and second highlights")
    }

    @Test
    fun `stripFormatting removes mixed formatting`() {
        val input = "==Highlighted== and **bold** text"
        val result = TranscriptFormattingParser.stripFormatting(input)
        assertThat(result).isEqualTo("Highlighted and bold text")
    }

    @Test
    fun `stripFormatting handles text without formatting`() {
        val input = "Plain text without any formatting"
        val result = TranscriptFormattingParser.stripFormatting(input)
        assertThat(result).isEqualTo(input)
    }

    @Test
    fun `stripFormatting handles empty string`() {
        val result = TranscriptFormattingParser.stripFormatting("")
        assertThat(result).isEmpty()
    }

    @Test
    fun `stripFormatting handles adjacent formatting`() {
        val input = "==highlight====another=="
        val result = TranscriptFormattingParser.stripFormatting(input)
        assertThat(result).isEqualTo("highlightanother")
    }

    // ==================== validateFormatting tests ====================

    @Test
    fun `validateFormatting returns true when only formatting added`() {
        val original = "This is important text"
        val formatted = "This is ==important== text"
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isTrue()
    }

    @Test
    fun `validateFormatting returns true when bold added`() {
        val original = "This is important text"
        val formatted = "This is **important** text"
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isTrue()
    }

    @Test
    fun `validateFormatting returns true for unformatted matching text`() {
        val original = "Plain text"
        val formatted = "Plain text"
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isTrue()
    }

    @Test
    fun `validateFormatting returns false when text content modified`() {
        val original = "This is text"
        val formatted = "This is modified text"
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isFalse()
    }

    @Test
    fun `validateFormatting returns false when text deleted`() {
        val original = "This is text"
        val formatted = "This is"
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isFalse()
    }

    @Test
    fun `validateFormatting returns false when text added`() {
        val original = "This is text"
        val formatted = "This is extra text"
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isFalse()
    }

    @Test
    fun `validateFormatting handles multiple formatting spans`() {
        val original = "First second third"
        val formatted = "==First== **second** ==third=="
        val result = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(result).isTrue()
    }

    // ==================== applyFormatting tests ====================

    @Test
    fun `applyFormatting adds highlight markers`() {
        val text = "Hello world"
        val result = TranscriptFormattingParser.applyFormatting(text, 0, 5, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo("==Hello== world")
    }

    @Test
    fun `applyFormatting adds bold markers`() {
        val text = "Hello world"
        val result = TranscriptFormattingParser.applyFormatting(text, 6, 11, FormattingType.BOLD)
        assertThat(result).isEqualTo("Hello **world**")
    }

    @Test
    fun `applyFormatting in middle of text`() {
        val text = "The quick brown fox"
        val result = TranscriptFormattingParser.applyFormatting(text, 4, 9, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo("The ==quick== brown fox")
    }

    @Test
    fun `applyFormatting toggles off existing highlight`() {
        val text = "Hello ==world=="
        val result = TranscriptFormattingParser.applyFormatting(text, 6, 15, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo("Hello world")
    }

    @Test
    fun `applyFormatting toggles off existing bold`() {
        val text = "Hello **world**"
        val result = TranscriptFormattingParser.applyFormatting(text, 6, 15, FormattingType.BOLD)
        assertThat(result).isEqualTo("Hello world")
    }

    @Test
    fun `applyFormatting returns original for invalid range`() {
        val text = "Hello world"
        val result = TranscriptFormattingParser.applyFormatting(text, -1, 5, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo(text)
    }

    @Test
    fun `applyFormatting returns original for reversed range`() {
        val text = "Hello world"
        val result = TranscriptFormattingParser.applyFormatting(text, 5, 0, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo(text)
    }

    @Test
    fun `applyFormatting returns original for out of bounds range`() {
        val text = "Hello"
        val result = TranscriptFormattingParser.applyFormatting(text, 0, 10, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo(text)
    }

    @Test
    fun `applyFormatting entire text`() {
        val text = "Hello"
        val result = TranscriptFormattingParser.applyFormatting(text, 0, 5, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo("==Hello==")
    }

    // ==================== clearFormatting tests ====================

    @Test
    fun `clearFormatting removes highlight from selection`() {
        val text = "Hello ==world== there"
        val result = TranscriptFormattingParser.clearFormatting(text, 6, 15)
        assertThat(result).isEqualTo("Hello world there")
    }

    @Test
    fun `clearFormatting removes bold from selection`() {
        val text = "Hello **world** there"
        val result = TranscriptFormattingParser.clearFormatting(text, 6, 15)
        assertThat(result).isEqualTo("Hello world there")
    }

    @Test
    fun `clearFormatting removes multiple formats from selection`() {
        val text = "==Highlighted== and **bold** here"
        val result = TranscriptFormattingParser.clearFormatting(text, 0, 28)
        assertThat(result).isEqualTo("Highlighted and bold here")
    }

    @Test
    fun `clearFormatting leaves text outside selection unchanged`() {
        val text = "==Keep== ==remove== ==keep=="
        val result = TranscriptFormattingParser.clearFormatting(text, 9, 19)
        assertThat(result).isEqualTo("==Keep== remove ==keep==")
    }

    @Test
    fun `clearFormatting returns original for invalid range`() {
        val text = "Hello ==world=="
        val result = TranscriptFormattingParser.clearFormatting(text, -1, 5)
        assertThat(result).isEqualTo(text)
    }

    @Test
    fun `clearFormatting handles text without formatting`() {
        val text = "Plain text"
        val result = TranscriptFormattingParser.clearFormatting(text, 0, 5)
        assertThat(result).isEqualTo("Plain text")
    }

    // ==================== removeFormatting tests ====================

    @Test
    fun `removeFormatting removes only highlight type`() {
        val text = "==Highlighted== and **bold**"
        val result = TranscriptFormattingParser.removeFormatting(text, 0, 28, FormattingType.HIGHLIGHT)
        assertThat(result).isEqualTo("Highlighted and **bold**")
    }

    @Test
    fun `removeFormatting removes only bold type`() {
        val text = "==Highlighted== and **bold**"
        val result = TranscriptFormattingParser.removeFormatting(text, 0, 28, FormattingType.BOLD)
        assertThat(result).isEqualTo("==Highlighted== and bold")
    }

    // ==================== formattedIndexToStrippedIndex tests ====================

    @Test
    fun `formattedIndexToStrippedIndex before any markers`() {
        val text = "Hello ==world=="
        val result = TranscriptFormattingParser.formattedIndexToStrippedIndex(text, 3)
        assertThat(result).isEqualTo(3) // "Hel" -> "Hel"
    }

    @Test
    fun `formattedIndexToStrippedIndex after opening marker`() {
        val text = "Hello ==world=="
        val result = TranscriptFormattingParser.formattedIndexToStrippedIndex(text, 8)
        // text[0:8] = "Hello ==" which is incomplete (no closing ==)
        // So stripFormatting returns it unchanged, length 8
        assertThat(result).isEqualTo(8)
    }

    @Test
    fun `formattedIndexToStrippedIndex after complete highlight`() {
        val text = "==Hi== world"
        // text[0:6] = "==Hi==" -> stripped = "Hi" (length 2)
        val result = TranscriptFormattingParser.formattedIndexToStrippedIndex(text, 6)
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `formattedIndexToStrippedIndex at end`() {
        val text = "Hello ==world=="
        val result = TranscriptFormattingParser.formattedIndexToStrippedIndex(text, text.length)
        assertThat(result).isEqualTo(11) // "Hello world"
    }

    // ==================== strippedIndexToFormattedIndex tests ====================

    @Test
    fun `strippedIndexToFormattedIndex before any markers`() {
        val text = "Hello ==world=="
        val result = TranscriptFormattingParser.strippedIndexToFormattedIndex(text, 3)
        assertThat(result).isEqualTo(3) // "Hel" position unchanged
    }

    @Test
    fun `strippedIndexToFormattedIndex after stripped marker area`() {
        val text = "Hello ==world=="
        // stripped "Hello world", index 7 = 'o' in world
        // formatted has "Hello ==" before world, so need to skip markers
        val result = TranscriptFormattingParser.strippedIndexToFormattedIndex(text, 7)
        assertThat(result).isEqualTo(9) // position of 'o' in formatted text
    }

    // ==================== Edge cases ====================

    @Test
    fun `handles single equals signs without matching`() {
        val text = "a = b == c = d"
        val result = TranscriptFormattingParser.stripFormatting(text)
        // Only == surrounded by content should be treated as markers
        // Single = should remain unchanged
        assertThat(result).isEqualTo("a = b == c = d")
    }

    @Test
    fun `handles single asterisks without matching`() {
        val text = "a * b ** c * d"
        val result = TranscriptFormattingParser.stripFormatting(text)
        // Only ** surrounded by content should be treated as markers
        assertThat(result).isEqualTo("a * b ** c * d")
    }

    @Test
    fun `handles empty markers`() {
        // Empty markers like ==== should not create empty matches
        val text = "text ==== more"
        val result = TranscriptFormattingParser.stripFormatting(text)
        // The pattern requires at least one character between markers
        assertThat(result).isEqualTo("text ==== more")
    }

    @Test
    fun `handles newlines in text`() {
        val original = "Line one\nLine two"
        val formatted = "Line ==one==\nLine **two**"
        val isValid = TranscriptFormattingParser.validateFormatting(original, formatted)
        assertThat(isValid).isTrue()
    }

    @Test
    fun `handles special characters in highlighted text`() {
        val text = "Check ==this: special!== text"
        val result = TranscriptFormattingParser.stripFormatting(text)
        assertThat(result).isEqualTo("Check this: special! text")
    }
}
