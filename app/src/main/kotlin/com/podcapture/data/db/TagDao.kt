package com.podcapture.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.podcapture.data.model.AudioFileTag
import com.podcapture.data.model.CaptureTag
import com.podcapture.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    // Tag CRUD
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getTagById(tagId: String): Tag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag)

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    // AudioFile-Tag relationships
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN audio_file_tags aft ON t.id = aft.tagId
        WHERE aft.audioFileId = :audioFileId
        ORDER BY t.name ASC
    """)
    fun getTagsForAudioFile(audioFileId: String): Flow<List<Tag>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN audio_file_tags aft ON t.id = aft.tagId
        WHERE aft.audioFileId = :audioFileId
        ORDER BY t.name ASC
    """)
    suspend fun getTagsForAudioFileOnce(audioFileId: String): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagToAudioFile(audioFileTag: AudioFileTag)

    @Delete
    suspend fun removeTagFromAudioFile(audioFileTag: AudioFileTag)

    @Query("DELETE FROM audio_file_tags WHERE audioFileId = :audioFileId")
    suspend fun removeAllTagsFromAudioFile(audioFileId: String)

    // Capture-Tag relationships
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN capture_tags ct ON t.id = ct.tagId
        WHERE ct.captureId = :captureId
        ORDER BY t.name ASC
    """)
    fun getTagsForCapture(captureId: String): Flow<List<Tag>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN capture_tags ct ON t.id = ct.tagId
        WHERE ct.captureId = :captureId
        ORDER BY t.name ASC
    """)
    suspend fun getTagsForCaptureOnce(captureId: String): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagToCapture(captureTag: CaptureTag)

    @Delete
    suspend fun removeTagFromCapture(captureTag: CaptureTag)

    @Query("DELETE FROM capture_tags WHERE captureId = :captureId")
    suspend fun removeAllTagsFromCapture(captureId: String)

    // Filtering queries
    @Query("""
        SELECT DISTINCT af.id FROM audio_files af
        INNER JOIN audio_file_tags aft ON af.id = aft.audioFileId
        WHERE aft.tagId = :tagId
    """)
    suspend fun getAudioFileIdsWithTag(tagId: String): List<String>

    @Query("""
        SELECT DISTINCT c.id FROM captures c
        INNER JOIN capture_tags ct ON c.id = ct.captureId
        WHERE ct.tagId = :tagId
    """)
    suspend fun getCaptureIdsWithTag(tagId: String): List<String>
}
