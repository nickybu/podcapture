package com.podcapture.data.repository

import com.podcapture.data.db.TagDao
import com.podcapture.data.model.AudioFileTag
import com.podcapture.data.model.CaptureTag
import com.podcapture.data.model.Tag
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TagRepository(private val tagDao: TagDao) {

    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun createTag(name: String, color: Long = 0xFF6750A4): Tag {
        val tag = Tag(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            color = color
        )
        tagDao.insertTag(tag)
        return tag
    }

    suspend fun updateTag(tag: Tag) {
        tagDao.updateTag(tag)
    }

    suspend fun deleteTag(tag: Tag) {
        tagDao.deleteTag(tag)
    }

    // AudioFile tag operations
    fun getTagsForAudioFile(audioFileId: String): Flow<List<Tag>> {
        return tagDao.getTagsForAudioFile(audioFileId)
    }

    suspend fun getTagsForAudioFileOnce(audioFileId: String): List<Tag> {
        return tagDao.getTagsForAudioFileOnce(audioFileId)
    }

    suspend fun addTagToAudioFile(audioFileId: String, tagId: String) {
        tagDao.addTagToAudioFile(AudioFileTag(audioFileId, tagId))
    }

    suspend fun removeTagFromAudioFile(audioFileId: String, tagId: String) {
        tagDao.removeTagFromAudioFile(AudioFileTag(audioFileId, tagId))
    }

    suspend fun setAudioFileTags(audioFileId: String, tagIds: List<String>) {
        tagDao.removeAllTagsFromAudioFile(audioFileId)
        tagIds.forEach { tagId ->
            tagDao.addTagToAudioFile(AudioFileTag(audioFileId, tagId))
        }
    }

    // Capture tag operations
    fun getTagsForCapture(captureId: String): Flow<List<Tag>> {
        return tagDao.getTagsForCapture(captureId)
    }

    suspend fun getTagsForCaptureOnce(captureId: String): List<Tag> {
        return tagDao.getTagsForCaptureOnce(captureId)
    }

    suspend fun addTagToCapture(captureId: String, tagId: String) {
        tagDao.addTagToCapture(CaptureTag(captureId, tagId))
    }

    suspend fun removeTagFromCapture(captureId: String, tagId: String) {
        tagDao.removeTagFromCapture(CaptureTag(captureId, tagId))
    }

    suspend fun setCaptureTags(captureId: String, tagIds: List<String>) {
        tagDao.removeAllTagsFromCapture(captureId)
        tagIds.forEach { tagId ->
            tagDao.addTagToCapture(CaptureTag(captureId, tagId))
        }
    }

    // Filtering
    suspend fun getAudioFileIdsWithTag(tagId: String): List<String> {
        return tagDao.getAudioFileIdsWithTag(tagId)
    }

    suspend fun getCaptureIdsWithTag(tagId: String): List<String> {
        return tagDao.getCaptureIdsWithTag(tagId)
    }
}
