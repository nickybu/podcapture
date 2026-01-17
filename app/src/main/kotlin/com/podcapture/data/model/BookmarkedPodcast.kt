package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A podcast that the user has bookmarked/subscribed to.
 */
@Entity(tableName = "bookmarked_podcasts")
data class BookmarkedPodcast(
    @PrimaryKey
    val id: Long,                  // Podcast Index ID
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val feedUrl: String,
    val language: String = "en",
    val episodeCount: Int = 0,
    val lastUpdateTime: Long = 0L,
    val bookmarkedAt: Long = System.currentTimeMillis()
)

/**
 * Junction table for podcast tags.
 */
@Entity(
    tableName = "podcast_tags",
    primaryKeys = ["podcastId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = BookmarkedPodcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["tagId"])
    ]
)
data class PodcastTag(
    val podcastId: Long,
    val tagId: String
)
