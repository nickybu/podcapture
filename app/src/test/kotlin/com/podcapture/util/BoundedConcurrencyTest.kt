package com.podcapture.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BoundedConcurrencyTest {

    @Test
    fun `never runs more than maxConcurrency at once`() = runBlocking(Dispatchers.Default) {
        val running = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)

        val results = (1..20).toList().mapConcurrently(4) { id ->
            val now = running.incrementAndGet()
            maxSeen.updateAndGet { maxOf(it, now) }
            delay(20)
            running.decrementAndGet()
            id * 10
        }

        assertThat(maxSeen.get()).isEqualTo(4)
        assertThat(results.map { it.getOrThrow() }).isEqualTo((1..20).map { it * 10 })
    }

    @Test
    fun `one failure does not cancel the others`() = runBlocking(Dispatchers.Default) {
        val completed = AtomicInteger(0)

        val results = (1..8).toList().mapConcurrently(4) { id ->
            if (id == 2) throw IllegalStateException("feed $id is broken")
            delay(30) // still running when the failure happens
            completed.incrementAndGet()
            id
        }

        assertThat(completed.get()).isEqualTo(7)
        assertThat(results[1].exceptionOrNull()).hasMessageThat().isEqualTo("feed 2 is broken")
        assertThat(results.filter { it.isSuccess }.map { it.getOrThrow() }).containsExactly(1, 3, 4, 5, 6, 7, 8).inOrder()
    }

    @Test
    fun `a cancellation raised by one item only fails that item`() = runBlocking(Dispatchers.Default) {
        val results = (1..3).toList().mapConcurrently(4) { id ->
            if (id == 1) throw CancellationException("timed out")
            delay(10)
            id
        }

        assertThat(results[0].isFailure).isTrue()
        assertThat(results.drop(1).map { it.getOrThrow() }).containsExactly(2, 3).inOrder()
    }

    @Test
    fun `cancelling the caller cancels every item`() = runBlocking(Dispatchers.Default) {
        val started = AtomicInteger(0)
        val allStarted = CompletableDeferred<Unit>()
        val finished = AtomicInteger(0)

        val job = launch {
            (1..4).toList().mapConcurrently(4) {
                if (started.incrementAndGet() == 4) allStarted.complete(Unit)
                delay(10_000)
                finished.incrementAndGet()
            }
        }
        withTimeout(5_000) { allStarted.await() }
        job.cancel()
        job.join()
        yield()

        assertThat(job.isCancelled).isTrue()
        assertThat(finished.get()).isEqualTo(0)
    }
}
