package com.podcapture.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Runs [block] for every item with at most [maxConcurrency] running at once, and returns
 * one [Result] per item in input order. A failing item is captured as a failed [Result]
 * and never cancels the others; cancellation of the caller still cancels everything.
 */
suspend fun <T, R> List<T>.mapConcurrently(
    maxConcurrency: Int,
    block: suspend (T) -> R
): List<Result<R>> {
    require(maxConcurrency > 0) { "maxConcurrency must be positive" }
    val semaphore = Semaphore(maxConcurrency)
    return coroutineScope {
        map { item ->
            async {
                semaphore.withPermit {
                    try {
                        Result.success(block(item))
                    } catch (e: CancellationException) {
                        // Rethrows if the caller was cancelled; a CancellationException the
                        // block raised on its own (e.g. a timeout) is just this item failing.
                        currentCoroutineContext().ensureActive()
                        Result.failure(e)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
    }
}
