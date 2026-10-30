package com.podcapture.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadedEpisodeFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun File.write(name: String, bytes: Int = 16) =
        File(this, name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `finds episode ids for each supported extension`() {
        val dir = tmp.newFolder("episodes")
        dir.write("episode_101.mp3")
        dir.write("episode_102.m4a")
        dir.write("episode_103.aac")
        dir.write("episode_104.ogg")

        assertThat(PodcastRepository.DownloadedEpisodeFiles.idsInDir(dir))
            .containsExactly(101L, 102L, 103L, 104L)
    }

    @Test
    fun `handles negative ids used by custom RSS episodes`() {
        val dir = tmp.newFolder("episodes")
        dir.write("episode_-4611686018427387904.mp3")

        assertThat(PodcastRepository.DownloadedEpisodeFiles.idsInDir(dir))
            .containsExactly(-4611686018427387904L)
    }

    @Test
    fun `ignores partial downloads, empty files and unrelated files`() {
        val dir = tmp.newFolder("episodes")
        dir.write("episode_1.mp3.tmp")
        dir.write("episode_2.mp3", bytes = 0)
        dir.write("episode_3.wav")
        dir.write("notes.txt")
        File(dir, "episode_4.mp3").mkdir()

        assertThat(PodcastRepository.DownloadedEpisodeFiles.idsInDir(dir)).isEmpty()
    }

    @Test
    fun `missing directory means nothing downloaded`() {
        assertThat(PodcastRepository.DownloadedEpisodeFiles.idsInDir(File(tmp.root, "nope"))).isEmpty()
    }
}
