package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks playback history for podcast episodes.
 * Allows merging with audio file history on the home screen.
 */
@Entity(
    tableName = "episode_playback_history",
    foreignKeys = [
        ForeignKey(
            entity = CachedEpisode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episodeId"]),
        Index(value = ["lastPlayedAt"])
    ]
)
data class EpisodePlaybackHistory(
    @PrimaryKey
    val episodeId: Long,               // Episode ID
    val podcastId: Long,               // For easy podcast lookup
    val podcastTitle: String,          // Cached for display
    val podcastArtworkUrl: String,     // Cached for display
    val episodeTitle: String,          // Cached for display
    val duration: Int,                 // Duration in seconds
    val positionMs: Long = 0,          // Last playback position
    val firstPlayedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val localFilePath: String? = null  // If downloaded locally
)

/**
 * Unified history item for home screen display.
 * Can represent either an audio file or a podcast episode.
 */
sealed class HistoryItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val durationMs: Long
    abstract val lastPlayedAt: Long
    abstract val firstPlayedAt: Long?

    data class AudioFileItem(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val durationMs: Long,
        override val lastPlayedAt: Long,
        override val firstPlayedAt: Long?,
        val audioFileId: String,
        val captureCount: Int,
        val tags: List<Tag>
    ) : HistoryItem()

    data class EpisodeItem(
        override val id: String,
        override val title: String,
        override val subtitle: String,   // Podcast title
        override val durationMs: Long,
        override val lastPlayedAt: Long,
        override val firstPlayedAt: Long?,
        val episodeId: Long,
        val podcastId: Long,
        val artworkUrl: String,
        val positionMs: Long,
        val localFilePath: String?
    ) : HistoryItem()
}
