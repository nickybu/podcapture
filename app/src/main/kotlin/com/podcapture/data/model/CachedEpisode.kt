package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached episode data from the API.
 * Episodes are fetched and stored for offline access and pagination.
 */
@Entity(
    tableName = "cached_episodes",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkedPodcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["publishedDate"])
    ]
)
data class CachedEpisode(
    @PrimaryKey
    val id: Long,                      // Episode ID from API
    val podcastId: Long,               // Foreign key to BookmarkedPodcast
    val title: String,
    val description: String,           // Show notes / description
    val link: String? = null,          // URL to episode's webpage with full show notes
    val publishedDate: Long,           // Unix timestamp
    val duration: Int,                 // Duration in seconds
    val audioUrl: String,
    val audioType: String = "audio/mpeg",
    val audioSize: Long = 0L,
    val imageUrl: String = "",
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Episode with download state for UI display.
 */
data class EpisodeWithDownload(
    val episode: CachedEpisode,
    val downloadState: EpisodeDownloadState = EpisodeDownloadState.NotDownloaded,
    val downloadProgress: Int = 0,
    val localFilePath: String? = null
)

/**
 * Download state for episodes.
 */
enum class EpisodeDownloadState {
    NotDownloaded,
    Downloading,
    Downloaded,
    Error
}

/**
 * Episode with podcast info for display in latest episodes list.
 */
data class LatestEpisode(
    val episode: CachedEpisode,
    val podcastTitle: String,
    val podcastArtworkUrl: String
)
