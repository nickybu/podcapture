package com.podcapture.download

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.data.repository.PodcastRepository
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DownloadManagerTest {

    /** Fake backend whose downloads block until the test completes them. */
    private class FakeBackend : DownloadManager.Backend {
        val downloadCalls: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val started = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        val gates = ConcurrentHashMap<Long, CompletableDeferred<Result<String>>>()
        val cancelled: MutableList<Long> = Collections.synchronizedList(mutableListOf())
        val failPrepareFor: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)

        fun startedSignal(id: Long) = started.getOrPut(id) { CompletableDeferred() }
        fun gate(id: Long) = gates.getOrPut(id) { CompletableDeferred() }

        override fun getLocalEpisodePath(episodeId: Long): String? = null

        override suspend fun prepare(request: DownloadManager.DownloadRequest) {
            if (request.episode.id in failPrepareFor) throw IllegalStateException("db exploded")
        }

        override suspend fun download(
            episode: CachedEpisode,
            onProgress: (PodcastRepository.DownloadProgress) -> Unit
        ): Result<String> {
            downloadCalls += episode.id
            val now = running.incrementAndGet()
            maxRunning.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                onProgress(PodcastRepository.DownloadProgress(10, 10, 100))
                startedSignal(episode.id).complete(Unit)
                return gate(episode.id).await()
            } catch (e: CancellationException) {
                cancelled += episode.id
                throw e
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private val backend = FakeBackend()
    private val serviceStarts = AtomicInteger(0)
    private val manager = DownloadManager(
        backend = backend,
        serviceStarter = { serviceStarts.incrementAndGet() },
        dispatcher = Dispatchers.Default
    )

    private fun episode(id: Long) = CachedEpisode(
        id = id,
        podcastId = 1,
        title = "Episode $id",
        description = "",
        publishedDate = 0,
        duration = 60,
        audioUrl = "https://example.com/$id.mp3"
    )

    private fun runTest(block: suspend () -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    private suspend fun awaitState(id: Long, state: EpisodeDownloadState?) {
        manager.downloadStates.first { it[id]?.state == state }
    }

    private suspend fun awaitIdle() {
        manager.activity.first { it.isIdle }
    }

    @Test
    fun `queueing a second download does not restart or duplicate the active one`() = runTest {
        manager.downloadEpisode(episode(1), "Pod")
        backend.startedSignal(1).await()

        manager.downloadEpisode(episode(2), "Pod")
        manager.downloadEpisode(episode(3), "Pod")
        delay(200) // give a buggy implementation time to launch extra downloads

        assertThat(backend.downloadCalls).containsExactly(1L)
        assertThat(manager.getPendingCount()).isEqualTo(2)
        assertThat(manager.getCurrentDownloadInfo()?.episodeId).isEqualTo(1L)
        assertThat(manager.currentActivity().queuedCount).isEqualTo(2)

        backend.gate(1).complete(Result.success("/a"))
        backend.startedSignal(2).await()
        backend.gate(2).complete(Result.success("/b"))
        backend.startedSignal(3).await()
        backend.gate(3).complete(Result.success("/c"))
        awaitIdle()

        assertThat(backend.downloadCalls).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(backend.maxRunning.get()).isEqualTo(1)
        assertThat(manager.getDownloadState(1)?.state).isEqualTo(EpisodeDownloadState.Downloaded)
        assertThat(manager.getDownloadState(3)?.state).isEqualTo(EpisodeDownloadState.Downloaded)
        assertThat(manager.getPendingCount()).isEqualTo(0)
        assertThat(manager.getCurrentDownloadInfo()).isNull()
    }

    @Test
    fun `enqueueing the same episode twice downloads it once`() = runTest {
        manager.downloadEpisode(episode(1), "Pod")
        manager.downloadEpisode(episode(1), "Pod")
        backend.startedSignal(1).await()
        manager.downloadEpisode(episode(1), "Pod")

        backend.gate(1).complete(Result.success("/a"))
        awaitIdle()

        assertThat(backend.downloadCalls).containsExactly(1L)
        assertThat(serviceStarts.get()).isEqualTo(1)
    }

    @Test
    fun `exception while preparing maps to Error and the queue keeps going`() = runTest {
        backend.failPrepareFor += 1L
        manager.downloadEpisode(episode(1), "Pod")
        manager.downloadEpisode(episode(2), "Pod")

        awaitState(1, EpisodeDownloadState.Error)
        backend.startedSignal(2).await()
        backend.gate(2).complete(Result.success("/b"))
        awaitState(2, EpisodeDownloadState.Downloaded)

        assertThat(backend.downloadCalls).containsExactly(2L)
    }

    @Test
    fun `failed download result maps to Error`() = runTest {
        manager.downloadEpisode(episode(1), "Pod")
        backend.gate(1).complete(Result.failure(java.io.IOException("offline")))
        awaitState(1, EpisodeDownloadState.Error)
        awaitIdle()
    }

    @Test
    fun `cancelling the active download stops it and starts the next`() = runTest {
        manager.downloadEpisode(episode(1), "Pod")
        manager.downloadEpisode(episode(2), "Pod")
        backend.startedSignal(1).await()

        manager.cancelDownload(1)

        backend.startedSignal(2).await()
        assertThat(backend.cancelled).containsExactly(1L)
        assertThat(manager.getDownloadState(1)).isNull()
        assertThat(manager.getCurrentDownloadInfo()?.episodeId).isEqualTo(2L)

        backend.gate(2).complete(Result.success("/b"))
        awaitIdle()
        // A cancelled download must not reappear as Error or Downloaded.
        assertThat(manager.getDownloadState(1)).isNull()
    }

    @Test
    fun `cancelling a queued download removes it without downloading`() = runTest {
        manager.downloadEpisode(episode(1), "Pod")
        manager.downloadEpisode(episode(2), "Pod")
        backend.startedSignal(1).await()

        manager.cancelDownload(2)
        assertThat(manager.getPendingCount()).isEqualTo(0)

        backend.gate(1).complete(Result.success("/a"))
        awaitIdle()

        assertThat(backend.downloadCalls).containsExactly(1L)
        assertThat(manager.getDownloadState(2)).isNull()
    }

    @Test
    fun `episode can be re-queued immediately after it was cancelled`() = runTest {
        manager.downloadEpisode(episode(1), "Pod")
        backend.startedSignal(1).await()
        backend.started.remove(1L)
        backend.gates.remove(1L)

        manager.cancelDownload(1)
        manager.downloadEpisode(episode(1), "Pod")
        backend.startedSignal(1).await()
        backend.gate(1).complete(Result.success("/a"))
        awaitState(1, EpisodeDownloadState.Downloaded)

        assertThat(backend.downloadCalls).containsExactly(1L, 1L)
        assertThat(backend.maxRunning.get()).isEqualTo(1)
    }
}
