package com.podcapture.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * In-process navigation requests that originate outside the Compose tree
 * (media notification tap, headset gestures, widgets).
 *
 * Producers call [request]; the NavHost collects [events] and navigates.
 * Extra buffer of 1 so a request emitted before the NavHost is composed
 * (cold start from the notification) is not lost.
 */
object DeepLinkEvents {
    sealed interface DeepLink {
        /** Open the player for whatever is currently loaded in the audio service. */
        data object OpenNowPlaying : DeepLink
    }

    private val _events = MutableSharedFlow<DeepLink>(replay = 1, extraBufferCapacity = 1)
    val events: SharedFlow<DeepLink> = _events

    fun request(link: DeepLink) {
        _events.tryEmit(link)
    }

    /** Call after handling so a replayed event is not handled twice. */
    @Suppress("UNUSED_PARAMETER")
    fun consume(link: DeepLink) {
        _events.resetReplayCache()
    }
}
