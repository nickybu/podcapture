package com.podcapture.ui.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UniqueMarkdownFileNameTest {

    @Test
    fun `returns plain name when free`() {
        assertThat(uniqueMarkdownFileName("Episode") { false }).isEqualTo("Episode.md")
    }

    @Test
    fun `appends counter starting at 2 when name is taken`() {
        val taken = setOf("Episode.md")
        assertThat(uniqueMarkdownFileName("Episode") { it in taken }).isEqualTo("Episode (2).md")
    }

    @Test
    fun `skips taken counters`() {
        val taken = setOf("Episode.md", "Episode (2).md", "Episode (3).md")
        assertThat(uniqueMarkdownFileName("Episode") { it in taken }).isEqualTo("Episode (4).md")
    }
}
