package com.podcapture.data.model

import com.podcapture.data.api.dto.EpisodeDto

data class Episode(
    val id: Long,
    val podcastId: Long,
    val title: String,
    val description: String,
    val link: String?,  // URL to episode's webpage with full show notes
    val publishedDate: Long,
    val duration: Int,
    val audioUrl: String,
    val audioType: String,
    val audioSize: Long,
    val imageUrl: String,
    val chaptersUrl: String?,
    val transcriptUrl: String?
)

fun EpisodeDto.toDomain(): Episode {
    return Episode(
        id = id,
        podcastId = feedId,
        title = title,
        description = description ?: "",
        link = link,
        publishedDate = datePublished ?: 0L,
        duration = duration ?: 0,
        audioUrl = enclosureUrl ?: "",
        audioType = enclosureType ?: "audio/mpeg",
        audioSize = enclosureLength ?: 0L,
        imageUrl = image ?: feedImage ?: "",
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl
    )
}
