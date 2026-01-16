package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "audio_file_tags",
    primaryKeys = ["audioFileId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = AudioFile::class,
            parentColumns = ["id"],
            childColumns = ["audioFileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("audioFileId"), Index("tagId")]
)
data class AudioFileTag(
    val audioFileId: String,
    val tagId: String
)
