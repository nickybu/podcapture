package com.podcapture.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpisodeFileIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun File.write(name: String, bytes: Int = 16) =
        File(this, name).apply { writeBytes(ByteArray(bytes)) }

    private fun idsIn(dir: File) = PodcastRepository.EpisodeFileIndex.idsInDir(dir)

    @Test
    fun `finds ids for every extension getLocalEpisodePath probes`() {
        val dir = tmp.newFolder("episodes")
        dir.write("episode_1.mp3")
        dir.write("episode_2.m4a")
        dir.write("episode_3.aac")
        dir.write("episode_4.ogg")

        assertThat(idsIn(dir)).containsExactly(1L, 2L, 3L, 4L)
    }

    @Test
    fun `handles negative ids of custom RSS episodes`() {
        val dir = tmp.newFolder("episodes")
        dir.write("episode_-4611686018427387904.mp3")

        assertThat(idsIn(dir)).containsExactly(-4611686018427387904L)
    }

    @Test
    fun `ignores partial, empty and unrelated files`() {
        val dir = tmp.newFolder("episodes")
        dir.write("episode_1.mp3.tmp")
        dir.write("episode_2.mp3", bytes = 0)
        dir.write("episode_3.wav")
        dir.write("notes.txt")
        File(dir, "episode_4.mp3").mkdir()

        assertThat(idsIn(dir)).isEmpty()
    }

    @Test
    fun `missing directory means nothing downloaded`() {
        assertThat(idsIn(File(tmp.root, "missing"))).isEmpty()
    }
}
