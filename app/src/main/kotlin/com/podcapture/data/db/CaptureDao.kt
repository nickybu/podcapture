package com.podcapture.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.podcapture.data.model.Capture
import kotlinx.coroutines.flow.Flow

/** One row of [CaptureDao.getCaptureCountsByFile]. */
data class AudioFileCaptureCount(
    val audioFileId: String,
    val count: Int
)

@Dao
interface CaptureDao {

    @Query("SELECT * FROM captures WHERE audioFileId = :audioFileId ORDER BY timestampMs ASC")
    fun getCapturesForFile(audioFileId: String): Flow<List<Capture>>

    @Query("SELECT * FROM captures WHERE audioFileId = :audioFileId ORDER BY timestampMs ASC")
    suspend fun getCapturesForFileOnce(audioFileId: String): List<Capture>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getCaptureById(id: String): Capture?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(capture: Capture)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteCapture(id: String)

    @Query("DELETE FROM captures WHERE audioFileId = :audioFileId")
    suspend fun deleteCapturesForFile(audioFileId: String)

    @Query("SELECT COUNT(*) FROM captures WHERE audioFileId = :audioFileId")
    suspend fun getCaptureCountForFile(audioFileId: String): Int

    @Query("SELECT COUNT(*) FROM captures WHERE audioFileId = :audioFileId")
    fun getCaptureCountForFileFlow(audioFileId: String): Flow<Int>

    /** Capture count for every audio file (and "episode_{id}" virtual file) that has captures. */
    @Query("SELECT audioFileId, COUNT(*) AS count FROM captures GROUP BY audioFileId")
    fun getCaptureCountsByFile(): Flow<List<AudioFileCaptureCount>>

    @Query("UPDATE captures SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?)

    @Query("UPDATE captures SET formattedTranscription = :formattedTranscription WHERE id = :id")
    suspend fun updateFormattedTranscription(id: String, formattedTranscription: String?)
}
