package com.podcapture.data.repository

import com.podcapture.data.db.AudioFileDao
import com.podcapture.data.model.AudioFile
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AudioFileRepository(
    private val audioFileDao: AudioFileDao
) {
    val allFiles: Flow<List<AudioFile>> = audioFileDao.getAllFiles()

    suspend fun getAllFilesOnce(): List<AudioFile> = audioFileDao.getAllFilesOnce()

    fun getFileById(id: String): Flow<AudioFile?> = audioFileDao.getFileByIdFlow(id)

    suspend fun getFileByIdOnce(id: String): AudioFile? = audioFileDao.getFileById(id)

    suspend fun getFileByPath(filePath: String): AudioFile? = audioFileDao.getFileByPath(filePath)

    suspend fun addOrUpdateFile(
        name: String,
        filePath: String,
        durationMs: Long,
        format: String
    ): AudioFile {
        // Check if file already exists
        val existingFile = audioFileDao.getFileByPath(filePath)

        return if (existingFile != null) {
            // Update existing file (in case metadata changed)
            val updatedFile = existingFile.copy(
                name = name,
                durationMs = durationMs,
                format = format
            )
            audioFileDao.updateFile(updatedFile)
            updatedFile
        } else {
            // Create new file
            val newFile = AudioFile(
                id = UUID.randomUUID().toString(),
                name = name,
                filePath = filePath,
                durationMs = durationMs,
                format = format,
                addedAt = System.currentTimeMillis()
            )
            audioFileDao.insertFile(newFile)
            newFile
        }
    }

    suspend fun updatePlaybackState(id: String, position: Long) {
        audioFileDao.updatePlaybackState(
            id = id,
            timestamp = System.currentTimeMillis(),
            position = position
        )
    }

    suspend fun updatePosition(id: String, position: Long) {
        audioFileDao.updatePosition(id, position)
    }

    suspend fun deleteFile(id: String) {
        audioFileDao.deleteFile(id)
    }

    // Bookmark methods
    val bookmarkedFiles: Flow<List<AudioFile>> = audioFileDao.getBookmarkedFiles()

    suspend fun getBookmarkedFilesOnce(): List<AudioFile> = audioFileDao.getBookmarkedFilesOnce()

    suspend fun toggleBookmark(id: String) {
        val file = audioFileDao.getFileById(id) ?: return
        val newBookmarkState = !file.isBookmarked
        val bookmarkedAt = if (newBookmarkState) System.currentTimeMillis() else null
        audioFileDao.updateBookmarkStatus(id, newBookmarkState, bookmarkedAt)
    }

    suspend fun setBookmarked(id: String, isBookmarked: Boolean) {
        val bookmarkedAt = if (isBookmarked) System.currentTimeMillis() else null
        audioFileDao.updateBookmarkStatus(id, isBookmarked, bookmarkedAt)
    }
}
