package com.podcapture.data.repository

import com.podcapture.data.db.CaptureDao
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.Capture
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CaptureRepository(
    private val captureDao: CaptureDao,
    private val markdownManager: MarkdownManager
) {
    fun getCapturesForFile(audioFileId: String): Flow<List<Capture>> =
        captureDao.getCapturesForFile(audioFileId)

    fun getCaptureCountForFile(audioFileId: String): Flow<Int> =
        captureDao.getCaptureCountForFileFlow(audioFileId)

    suspend fun getCapturesForFileOnce(audioFileId: String): List<Capture> =
        captureDao.getCapturesForFileOnce(audioFileId)

    suspend fun createCapture(
        audioFile: AudioFile,
        timestampMs: Long,
        windowStartMs: Long,
        windowEndMs: Long,
        transcription: String
    ): Capture {
        val capture = Capture(
            id = UUID.randomUUID().toString(),
            audioFileId = audioFile.id,
            timestampMs = timestampMs,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            transcription = transcription,
            createdAt = System.currentTimeMillis()
        )

        // Save to database
        captureDao.insertCapture(capture)

        // Update markdown file
        val allCaptures = captureDao.getCapturesForFileOnce(audioFile.id)
        markdownManager.updateMarkdownFile(audioFile, allCaptures)

        return capture
    }

    suspend fun deleteCapture(captureId: String, audioFile: AudioFile) {
        captureDao.deleteCapture(captureId)

        // Update markdown file
        val remainingCaptures = captureDao.getCapturesForFileOnce(audioFile.id)
        if (remainingCaptures.isEmpty()) {
            markdownManager.deleteMarkdownFile(audioFile)
        } else {
            markdownManager.updateMarkdownFile(audioFile, remainingCaptures)
        }
    }

    suspend fun deleteCapturesForFile(audioFileId: String, audioFile: AudioFile) {
        captureDao.deleteCapturesForFile(audioFileId)
        markdownManager.deleteMarkdownFile(audioFile)
    }

    suspend fun updateCaptureNotes(captureId: String, notes: String?, audioFile: AudioFile) {
        captureDao.updateNotes(captureId, notes)

        // Update markdown file with new notes
        val allCaptures = captureDao.getCapturesForFileOnce(audioFile.id)
        markdownManager.updateMarkdownFile(audioFile, allCaptures)
    }

    fun getMarkdownContent(audioFile: AudioFile): String? =
        markdownManager.getMarkdownContent(audioFile)

    fun getMarkdownFilePath(audioFile: AudioFile): String =
        markdownManager.getMarkdownFilePath(audioFile)
}
