package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "capture_tags",
    primaryKeys = ["captureId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Capture::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("captureId"), Index("tagId")]
)
data class CaptureTag(
    val captureId: String,
    val tagId: String
)
