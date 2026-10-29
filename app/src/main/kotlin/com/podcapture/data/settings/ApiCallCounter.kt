package com.podcapture.data.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** API calls made on [day] (`yyyy-MM-dd`) that have not been written to storage yet. */
data class ApiCallBatch(val day: String, val count: Int) {

    /**
     * Folds this batch into the stored (date, count) pair. Dates are `yyyy-MM-dd`, so they
     * order as strings. A batch for an older day than the stored one is dropped: the stored
     * value already belongs to a later day, and only today's count is ever shown.
     */
    fun mergeInto(storedDate: String?, storedCount: Int?): Pair<String, Int> = when {
        storedDate == day -> day to (storedCount ?: 0) + count
        storedDate == null || day > storedDate -> day to count
        else -> storedDate to (storedCount ?: 0)
    }
}

/**
 * In-memory tally of API calls for the current day. Thread-safe; holds only calls that have
 * not been drained yet.
 */
class ApiCallTally {
    private var day: String? = null
    private var count = 0

    /**
     * Counts one call on [today]. When [today] differs from the day being counted, the
     * previous day's calls are returned so they can be flushed immediately.
     */
    @Synchronized
    fun record(today: String): ApiCallBatch? {
        val previous = if (day != null && day != today && count > 0) ApiCallBatch(day!!, count) else null
        if (day != today) {
            day = today
            count = 0
        }
        count++
        return previous
    }

    @Synchronized
    fun pending(): Int = count

    /** Removes and returns the calls counted so far, or null when there are none. */
    @Synchronized
    fun drain(): ApiCallBatch? {
        val current = day ?: return null
        if (count == 0) return null
        return ApiCallBatch(current, count).also { count = 0 }
    }
}

/**
 * Counts API calls in memory and persists them through [flush] in batches: [flushDelayMs]
 * after the last call, as soon as [maxPending] calls have built up, and immediately when the
 * day rolls over. This replaces one DataStore file rewrite + fsync per request.
 *
 * Calls counted in the last [flushDelayMs] before the process dies are lost; the count is
 * informational, so that trade-off is accepted.
 */
class ApiCallCounter(
    private val flush: suspend (ApiCallBatch) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val flushDelayMs: Long = 5_000L,
    private val maxPending: Int = 25
) {
    private val tally = ApiCallTally()
    private var timer: Job? = null

    fun record(today: String) {
        tally.record(today)?.let { previousDay -> scope.launch { write(previousDay) } }
        if (tally.pending() >= maxPending) {
            flushSoon()
        } else {
            restartTimer()
        }
    }

    @Synchronized
    private fun restartTimer() {
        timer?.cancel()
        // The flush itself is launched on [scope], not as a child of the timer, so a later
        // restartTimer() cancelling the timer can never interrupt a drain-and-write midway.
        timer = scope.launch {
            delay(flushDelayMs)
            flushSoon()
        }
    }

    private fun flushSoon() {
        scope.launch { tally.drain()?.let { write(it) } }
    }

    private suspend fun write(batch: ApiCallBatch) {
        try {
            flush(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed write only loses part of an informational counter; never crash for it.
        }
    }
}
