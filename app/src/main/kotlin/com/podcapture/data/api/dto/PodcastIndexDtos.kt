package com.podcapture.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PodcastSearchResponse(
    val status: String,
    val feeds: List<PodcastDto> = emptyList(),
    val count: Int = 0,
    val description: String? = null
)

@Serializable
data class PodcastDto(
    val id: Long,
    val title: String,
    val url: String? = null,
    val author: String? = null,
    val description: String? = null,
    val image: String? = null,
    val artwork: String? = null,
    val language: String? = null,
    val categories: Map<String, String>? = null,
    val episodeCount: Int? = null,
    @SerialName("lastUpdateTime")
    val lastUpdateTime: Long? = null,
    @SerialName("newestItemPubdate")
    val newestItemPubdate: Long? = null
)

@Serializable
data class EpisodesResponse(
    val status: String,
    val items: List<EpisodeDto> = emptyList(),
    val count: Int = 0,
    val description: String? = null
)

@Serializable
data class EpisodeDto(
    val id: Long,
    @SerialName("feedId")
    val feedId: Long,
    val title: String,
    val description: String? = null,
    val link: String? = null,  // URL to episode's webpage with full show notes
    @SerialName("datePublished")
    val datePublished: Long? = null,
    val duration: Int? = null,
    @SerialName("enclosureUrl")
    val enclosureUrl: String? = null,
    @SerialName("enclosureType")
    val enclosureType: String? = null,
    @SerialName("enclosureLength")
    val enclosureLength: Long? = null,
    val image: String? = null,
    @SerialName("feedImage")
    val feedImage: String? = null,
    @SerialName("chaptersUrl")
    val chaptersUrl: String? = null,
    @SerialName("transcriptUrl")
    val transcriptUrl: String? = null
)

@Serializable
data class PodcastByIdResponse(
    val status: String,
    val feed: PodcastDto? = null,
    val description: String? = null
)
