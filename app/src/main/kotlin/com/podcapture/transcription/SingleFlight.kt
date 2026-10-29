package com.podcapture.transcription

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Runs at most one instance of a suspending operation at a time: callers that arrive while it is
 * in flight await the same result instead of starting their own.
 *
 * The operation runs in [scope], not in the caller's coroutine, so a caller that is cancelled
 * while waiting does not cancel the work for everyone else. Once it completes (successfully or
 * not), the next call starts a fresh run.
 */
internal class SingleFlight<T>(private val scope: CoroutineScope) {

    private val lock = Any()
    private var inFlight: Deferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        val deferred = synchronized(lock) {
            inFlight?.takeIf { it.isActive }
                ?: scope.async { block() }.also { inFlight = it }
        }
        return deferred.await()
    }
}
