package com.podcapture.data.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.Collections

class ApiCallCounterTest {

    // ==================== ApiCallTally ====================

    @Test
    fun `calls on the same day accumulate until drained`() {
        val tally = ApiCallTally()
        repeat(3) { assertThat(tally.record("2026-10-29")).isNull() }

        assertThat(tally.drain()).isEqualTo(ApiCallBatch("2026-10-29", 3))
        assertThat(tally.drain()).isNull()
    }

    @Test
    fun `day rollover hands back the previous day's calls and restarts the count`() {
        val tally = ApiCallTally()
        tally.record("2026-10-29")
        tally.record("2026-10-29")

        val previous = tally.record("2026-10-30")

        assertThat(previous).isEqualTo(ApiCallBatch("2026-10-29", 2))
        assertThat(tally.drain()).isEqualTo(ApiCallBatch("2026-10-30", 1))
    }

    @Test
    fun `rollover after a drain has nothing left to hand back`() {
        val tally = ApiCallTally()
        tally.record("2026-10-29")
        tally.drain()

        assertThat(tally.record("2026-10-30")).isNull()
        assertThat(tally.drain()).isEqualTo(ApiCallBatch("2026-10-30", 1))
    }

    @Test
    fun `first call ever has no previous day`() {
        assertThat(ApiCallTally().record("2026-10-29")).isNull()
    }

    // ==================== ApiCallBatch.mergeInto ====================

    @Test
    fun `batch for the stored day adds to the stored count`() {
        assertThat(ApiCallBatch("2026-10-29", 4).mergeInto("2026-10-29", 10)).isEqualTo("2026-10-29" to 14)
    }

    @Test
    fun `batch for a newer day replaces the stored count`() {
        assertThat(ApiCallBatch("2026-10-30", 4).mergeInto("2026-10-29", 10)).isEqualTo("2026-10-30" to 4)
    }

    @Test
    fun `batch with nothing stored yet starts the count`() {
        assertThat(ApiCallBatch("2026-10-29", 4).mergeInto(null, null)).isEqualTo("2026-10-29" to 4)
        assertThat(ApiCallBatch("2026-10-29", 4).mergeInto("", 0)).isEqualTo("2026-10-29" to 4)
    }

    @Test
    fun `late batch for an older day does not clobber today's count`() {
        assertThat(ApiCallBatch("2026-10-29", 4).mergeInto("2026-10-30", 2)).isEqualTo("2026-10-30" to 2)
    }

    // ==================== ApiCallCounter ====================

    private class Store {
        var date: String? = null
        var count: Int? = null
        val writes: MutableList<ApiCallBatch> = Collections.synchronizedList(mutableListOf())

        @Synchronized
        fun add(batch: ApiCallBatch) {
            writes += batch
            val (d, c) = batch.mergeInto(date, count)
            date = d
            count = c
        }
    }

    private fun counter(store: Store, scope: CoroutineScope, flushDelayMs: Long = 100, maxPending: Int = 1_000) =
        ApiCallCounter(flush = { store.add(it) }, scope = scope, flushDelayMs = flushDelayMs, maxPending = maxPending)

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @Test
    fun `a burst of calls is written once after the quiet period`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = Store()
        val counter = counter(store, scope)

        repeat(10) { counter.record("2026-10-29") }
        assertThat(store.writes).isEmpty()

        awaitCondition { store.writes.isNotEmpty() }
        delay(200) // no further writes trail the single flush
        assertThat(store.writes).containsExactly(ApiCallBatch("2026-10-29", 10))
        assertThat(store.count).isEqualTo(10)
        scope.cancel()
    }

    @Test
    fun `day rollover flushes the previous day immediately and keeps today's count separate`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = Store()
        val counter = counter(store, scope, flushDelayMs = 60_000)

        repeat(3) { counter.record("2026-10-29") }
        counter.record("2026-10-30")

        // The old day goes out without waiting for the (one minute) debounce.
        awaitCondition { store.writes.isNotEmpty() }
        assertThat(store.writes).containsExactly(ApiCallBatch("2026-10-29", 3))
        assertThat(store.date).isEqualTo("2026-10-29")
        scope.cancel()
    }

    @Test
    fun `reaching maxPending flushes without waiting for the quiet period`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = Store()
        val counter = counter(store, scope, flushDelayMs = 60_000, maxPending = 5)

        repeat(5) { counter.record("2026-10-29") }

        awaitCondition { store.count == 5 }
        assertThat(store.date).isEqualTo("2026-10-29")
        scope.cancel()
    }

    @Test
    fun `a failing write does not crash or stop later writes`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        val written = Collections.synchronizedList(mutableListOf<ApiCallBatch>())
        val counter = ApiCallCounter(
            flush = { batch ->
                if (attempts.getAndIncrement() == 0) throw java.io.IOException("disk full")
                written += batch
            },
            scope = scope,
            flushDelayMs = 50
        )

        counter.record("2026-10-29")
        awaitCondition { attempts.get() == 1 }
        counter.record("2026-10-29")
        awaitCondition { written.isNotEmpty() }

        assertThat(written).containsExactly(ApiCallBatch("2026-10-29", 1))
        scope.cancel()
    }
}
