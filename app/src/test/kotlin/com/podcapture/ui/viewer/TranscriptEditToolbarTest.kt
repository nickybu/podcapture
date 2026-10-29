package com.podcapture.ui.viewer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptEditToolbarTest {

    @Test
    fun `collapsed selection leaves value unchanged`() {
        val value = TextFieldValue("hello world", selection = TextRange(3))
        assertThat(applyToolbarFormatting(value, ToolbarAction.HIGHLIGHT)).isEqualTo(value)
    }

    @Test
    fun `highlight wraps selection and puts caret after closing marker`() {
        val value = TextFieldValue("hello world", selection = TextRange(6, 11))
        val result = applyToolbarFormatting(value, ToolbarAction.HIGHLIGHT)
        assertThat(result.text).isEqualTo("hello ==world==")
        assertThat(result.selection).isEqualTo(TextRange(15))
    }

    @Test
    fun `bold handles reversed selection`() {
        val value = TextFieldValue("hello world", selection = TextRange(5, 0))
        val result = applyToolbarFormatting(value, ToolbarAction.BOLD)
        assertThat(result.text).isEqualTo("**hello** world")
        assertThat(result.selection).isEqualTo(TextRange(9))
    }

    @Test
    fun `toggling highlight off puts caret after unwrapped text`() {
        val value = TextFieldValue("say ==hi== now", selection = TextRange(4, 10))
        val result = applyToolbarFormatting(value, ToolbarAction.HIGHLIGHT)
        assertThat(result.text).isEqualTo("say hi now")
        assertThat(result.selection).isEqualTo(TextRange(6))
    }

    @Test
    fun `clear strips markers within selection`() {
        val value = TextFieldValue("a ==b== **c** d", selection = TextRange(0, 13))
        val result = applyToolbarFormatting(value, ToolbarAction.CLEAR)
        assertThat(result.text).isEqualTo("a b c d")
        assertThat(result.selection).isEqualTo(TextRange(5))
    }
}
