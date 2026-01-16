package com.podcapture.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.podcapture.data.model.AudioFile
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioFileDao {

    @Query("SELECT * FROM audio_files ORDER BY CASE WHEN lastPlayedAt IS NULL THEN 1 ELSE 0 END, lastPlayedAt DESC, addedAt DESC")
    fun getAllFiles(): Flow<List<AudioFile>>

    @Query("SELECT * FROM audio_files ORDER BY CASE WHEN lastPlayedAt IS NULL THEN 1 ELSE 0 END, lastPlayedAt DESC, addedAt DESC")
    suspend fun getAllFilesOnce(): List<AudioFile>

    @Query("SELECT * FROM audio_files WHERE id = :id")
    suspend fun getFileById(id: String): AudioFile?

    @Query("SELECT * FROM audio_files WHERE id = :id")
    fun getFileByIdFlow(id: String): Flow<AudioFile?>

    @Query("SELECT * FROM audio_files WHERE filePath = :filePath")
    suspend fun getFileByPath(filePath: String): AudioFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(audioFile: AudioFile)

    @Update
    suspend fun updateFile(audioFile: AudioFile)

    @Query("""
        UPDATE audio_files
        SET lastPlayedAt = :timestamp,
            lastPositionMs = :position,
            playCount = playCount + 1,
            firstPlayedAt = CASE WHEN firstPlayedAt IS NULL THEN :timestamp ELSE firstPlayedAt END
        WHERE id = :id
    """)
    suspend fun updatePlaybackState(id: String, timestamp: Long, position: Long)

    @Query("UPDATE audio_files SET lastPositionMs = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Long)

    @Query("DELETE FROM audio_files WHERE id = :id")
    suspend fun deleteFile(id: String)

    @Query("SELECT COUNT(*) FROM audio_files")
    suspend fun getFileCount(): Int
}
