package com.podcapture.data.model

import com.podcapture.data.api.dto.PodcastDto

data class Podcast(
    val id: Long,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val feedUrl: String,
    val language: String,
    val episodeCount: Int,
    val lastUpdateTime: Long,
    val categories: List<String> = emptyList()
)

fun PodcastDto.toDomain(): Podcast {
    return Podcast(
        id = id,
        title = title,
        author = author ?: "Unknown",
        description = description ?: "",
        artworkUrl = artwork ?: image ?: "",
        feedUrl = url ?: "",
        language = language ?: "en",
        episodeCount = episodeCount ?: 0,
        lastUpdateTime = lastUpdateTime ?: 0L,
        categories = categories?.values?.toList() ?: emptyList()
    )
}
