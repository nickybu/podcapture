package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey
    val id: String,              // UUID
    val name: String,            // Tag display name
    val color: Long = 0xFF6750A4 // Material primary color as default
)
