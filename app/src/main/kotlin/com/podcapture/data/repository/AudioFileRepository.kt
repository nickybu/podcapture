package com.podcapture.data.repository

import com.podcapture.data.db.AudioFileDao
import com.podcapture.data.model.AudioFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class AudioFileRepository(
    private val audioFileDao: AudioFileDao
) {
    // Background scope for fire-and-forget writes (playhead saves, play history) that must
    // survive the caller's scope being cancelled. viewModelScope is already cancelled by the
    // time ViewModel.onCleared() runs (lifecycle 2.8+), so a launch there never executes.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    suspend fun updatePosition(id: String, position: Long) {
        audioFileDao.updatePosition(id, position)
    }

    /**
     * Persists the playback position without blocking the caller.
     * Runs on the repository's own scope so the write completes even if the caller
     * (e.g. a ViewModel) is being destroyed during navigation.
     */
    fun updatePositionAsync(id: String, positionMs: Long) {
        backgroundScope.launch {
            audioFileDao.updatePosition(id, positionMs)
        }
    }

    /**
     * Records that playback started (lastPlayedAt / playCount, which drive History).
     * Fire-and-forget on the repository scope, like [updatePositionAsync].
     */
    fun recordPlaybackStartedAsync(id: String) {
        val timestamp = System.currentTimeMillis()
        backgroundScope.launch {
            audioFileDao.recordPlay(id, timestamp)
        }
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
