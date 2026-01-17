package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_files")
data class AudioFile(
    @PrimaryKey
    val id: String,                    // UUID
    val name: String,                  // Display name (e.g., "Episode 42.mp3")
    val filePath: String,              // Full URI path
    val durationMs: Long,              // Duration in milliseconds
    val format: String,                // File format (mp3, wav, m4a, etc.)
    val firstPlayedAt: Long? = null,   // Epoch millis, set once on first play
    val lastPlayedAt: Long? = null,    // Epoch millis, updated on each play
    val lastPositionMs: Long = 0,      // Resume position
    val addedAt: Long,                 // Epoch millis when added
    val playCount: Int = 0,            // Number of times played
    val isBookmarked: Boolean = false, // Whether the file is bookmarked
    val bookmarkedAt: Long? = null     // Epoch millis when bookmarked
)
