package com.podcapture.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(
            entity = AudioFile::class,
            parentColumns = ["id"],
            childColumns = ["audioFileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["audioFileId"])]
)
data class Capture(
    @PrimaryKey
    val id: String,                    // UUID
    val audioFileId: String,           // FK to AudioFile
    val timestampMs: Long,             // Position when capture button was pressed
    val windowStartMs: Long,           // Capture window start
    val windowEndMs: Long,             // Capture window end
    val transcription: String,         // Transcribed text from Vosk
    val notes: String? = null,         // User's notes about this capture
    val createdAt: Long                // Epoch millis when captured
)
