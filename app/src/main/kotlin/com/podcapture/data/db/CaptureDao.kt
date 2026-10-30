package com.podcapture.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.podcapture.data.model.Capture
import com.podcapture.data.model.Tag
import kotlinx.coroutines.flow.Flow

/** One row of [CaptureDao.getCaptureCountsByFile]. */
data class AudioFileCaptureCount(
    val audioFileId: String,
    val count: Int
)

/** One row of [CaptureDao.getAllCaptureTags]: a tag applied to a capture. */
data class CaptureTagRow(
    val captureId: String,
    @Embedded val tag: Tag
)

/**
 * One row of [CaptureDao.getCaptureSources]: what a capture's audioFileId points at.
 * [fileName] comes from audio_files (local files, and downloaded episodes whose row is named
 * after the episode); [episodeTitle]/[podcastTitle] from the cached episode, if still cached.
 */
data class CaptureSourceRow(
    val audioFileId: String,
    val fileName: String?,
    val episodeTitle: String?,
    val podcastTitle: String?
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

    /** Every capture, newest first (global Captures screen). */
    @Query("SELECT * FROM captures ORDER BY createdAt DESC, id ASC")
    fun getAllCapturesNewestFirst(): Flow<List<Capture>>

    /** Every capture-tag pair with its tag, in one query (avoids a per-capture tag lookup). */
    @Query("""
        SELECT ct.captureId AS captureId, t.id AS id, t.name AS name, t.color AS color
        FROM capture_tags ct
        INNER JOIN tags t ON t.id = ct.tagId
        ORDER BY t.name COLLATE NOCASE ASC
    """)
    fun getAllCaptureTags(): Flow<List<CaptureTagRow>>

    /**
     * Display source for every distinct audioFileId that has captures. Episode captures use
     * "episode_{id}"; the id is parsed out (SUBSTR from position 9) so the cached_episodes join
     * is a primary-key lookup rather than a scan comparing 'episode_' || id per row.
     */
    @Query("""
        SELECT s.audioFileId AS audioFileId,
               af.name AS fileName,
               ce.title AS episodeTitle,
               bp.title AS podcastTitle
        FROM (SELECT DISTINCT audioFileId FROM captures) s
        LEFT JOIN audio_files af ON af.id = s.audioFileId
        LEFT JOIN cached_episodes ce
            ON s.audioFileId GLOB 'episode_*'
            AND ce.id = CAST(SUBSTR(s.audioFileId, 9) AS INTEGER)
        LEFT JOIN bookmarked_podcasts bp ON bp.id = ce.podcastId
    """)
    fun getCaptureSources(): Flow<List<CaptureSourceRow>>
}
