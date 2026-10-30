package com.podcapture.transcription

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {

    // Stands in for WhisperModelManager's own download scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** A fake download that counts its runs and blocks until [release] completes. */
    private class FakeDownload(private val result: Boolean) {
        val runs = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val block: suspend () -> Boolean = {
            runs.incrementAndGet()
            started.complete(Unit)
            release.await()
            result
        }
    }

    @Test
    fun `two concurrent callers share one download`() = runBlocking {
        val flight = SingleFlight<Boolean>(scope)
        val download = FakeDownload(result = true)

        // UNDISPATCHED: each caller has joined the flight before async returns.
        val first = async(start = CoroutineStart.UNDISPATCHED) { flight.run(download.block) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { flight.run(download.block) }
        download.started.await()
        download.release.complete(Unit)

        assertThat(withTimeout(5_000) { listOf(first, second).awaitAll() }).containsExactly(true, true)
        assertThat(download.runs.get()).isEqualTo(1)
    }

    @Test
    fun `a failure is shared by all waiters and the next call retries`() = runBlocking {
        val flight = SingleFlight<Boolean>(scope)
        val download = FakeDownload(result = false)

        val callers = List(5) {
            async(start = CoroutineStart.UNDISPATCHED) { flight.run(download.block) }
        }
        download.release.complete(Unit)

        assertThat(withTimeout(5_000) { callers.awaitAll() }).containsExactly(false, false, false, false, false)
        assertThat(download.runs.get()).isEqualTo(1)

        // A completed attempt is not reused.
        val retry = FakeDownload(result = true).apply { release.complete(Unit) }
        assertThat(withTimeout(5_000) { flight.run(retry.block) }).isTrue()
        assertThat(retry.runs.get()).isEqualTo(1)
    }

    @Test
    fun `a cancelled caller does not cancel the shared download`() = runBlocking {
        val flight = SingleFlight<Boolean>(scope)
        val download = FakeDownload(result = true)

        val impatient = launch(Dispatchers.Default) { flight.run(download.block) }
        download.started.await()
        impatient.cancelAndJoin()

        val patient = async(start = CoroutineStart.UNDISPATCHED) { flight.run(download.block) }
        download.release.complete(Unit)

        assertThat(withTimeout(5_000) { patient.await() }).isTrue()
        assertThat(download.runs.get()).isEqualTo(1)
    }
}
