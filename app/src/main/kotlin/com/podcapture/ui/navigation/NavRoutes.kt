package com.podcapture.ui.navigation

import kotlinx.serialization.Serializable

sealed interface NavRoute {

    @Serializable
    data object Home : NavRoute

    @Serializable
    data class Player(val audioFileId: String, val seekToMs: Long? = null) : NavRoute

    @Serializable
    data class Viewer(val audioFileId: String, val scrollToCaptureId: String? = null) : NavRoute

    @Serializable
    data object Settings : NavRoute
}
