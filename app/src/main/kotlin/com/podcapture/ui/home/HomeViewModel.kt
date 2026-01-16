package com.podcapture.ui.home

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.Tag
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AudioFileWithCaptureCount(
    val audioFile: AudioFile,
    val captureCount: Int,
    val tags: List<Tag> = emptyList()
)

data class HomeUiState(
    val audioFiles: List<AudioFileWithCaptureCount> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val selectedTagId: String? = null,  // For filtering
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToPlayer: String? = null,  // audioFileId to navigate to
    val showTagDialog: Boolean = false,
    val editingAudioFileId: String? = null,  // AudioFile being tagged
    val newTagName: String = ""
)

class HomeViewModel(
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allFilesWithData: List<AudioFileWithCaptureCount> = emptyList()

    init {
        observeAudioFiles()
        observeTags()
    }

    private fun observeAudioFiles() {
        viewModelScope.launch {
            audioFileRepository.allFiles.collect { files ->
                val filesWithData = files.map { file ->
                    val captures = captureRepository.getCapturesForFileOnce(file.id)
                    val tags = tagRepository.getTagsForAudioFileOnce(file.id)
                    AudioFileWithCaptureCount(file, captures.size, tags)
                }
                allFilesWithData = filesWithData
                applyFilter()
            }
        }
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.value = _uiState.value.copy(allTags = tags)
            }
        }
    }

    private fun applyFilter() {
        val selectedTagId = _uiState.value.selectedTagId
        val filteredFiles = if (selectedTagId == null) {
            allFilesWithData
        } else {
            allFilesWithData.filter { item -> item.tags.any { it.id == selectedTagId } }
        }
        _uiState.value = _uiState.value.copy(
            audioFiles = filteredFiles,
            isLoading = false
        )
    }

    fun onTagFilterSelected(tagId: String?) {
        _uiState.value = _uiState.value.copy(selectedTagId = tagId)
        applyFilter()
    }

    fun onOpenTagDialog(audioFileId: String) {
        _uiState.value = _uiState.value.copy(
            showTagDialog = true,
            editingAudioFileId = audioFileId
        )
    }

    fun onCloseTagDialog() {
        _uiState.value = _uiState.value.copy(
            showTagDialog = false,
            editingAudioFileId = null,
            newTagName = ""
        )
    }

    fun onNewTagNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(newTagName = name)
    }

    fun onCreateTag() {
        val name = _uiState.value.newTagName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val tag = tagRepository.createTag(name)
            // If editing an audio file, add the new tag to it
            _uiState.value.editingAudioFileId?.let { audioFileId ->
                tagRepository.addTagToAudioFile(audioFileId, tag.id)
            }
            _uiState.value = _uiState.value.copy(newTagName = "")
            refreshAudioFiles()
        }
    }

    fun onToggleTagForAudioFile(tagId: String) {
        val audioFileId = _uiState.value.editingAudioFileId ?: return
        val currentFile = allFilesWithData.find { it.audioFile.id == audioFileId } ?: return
        val hasTag = currentFile.tags.any { it.id == tagId }

        viewModelScope.launch {
            if (hasTag) {
                tagRepository.removeTagFromAudioFile(audioFileId, tagId)
            } else {
                tagRepository.addTagToAudioFile(audioFileId, tagId)
            }
            refreshAudioFiles()
        }
    }

    fun onDeleteTag(tag: Tag) {
        viewModelScope.launch {
            tagRepository.deleteTag(tag)
            refreshAudioFiles()
        }
    }

    private suspend fun refreshAudioFiles() {
        val files = audioFileRepository.getAllFilesOnce()
        val filesWithData = files.map { file ->
            val captures = captureRepository.getCapturesForFileOnce(file.id)
            val tags = tagRepository.getTagsForAudioFileOnce(file.id)
            AudioFileWithCaptureCount(file, captures.size, tags)
        }
        allFilesWithData = filesWithData
        applyFilter()
    }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Get file metadata
                val fileName = getFileName(context, uri) ?: "Unknown"
                val format = getFileFormat(fileName)
                val duration = getAudioDuration(context, uri)

                if (format == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Unsupported file format. Supported: MP3, WAV, M4A, AAC, FLAC, OGG, OPUS."
                    )
                    return@launch
                }

                // Add or update file in repository
                val audioFile = audioFileRepository.addOrUpdateFile(
                    name = fileName,
                    filePath = uri.toString(),
                    durationMs = duration,
                    format = format
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    navigateToPlayer = audioFile.id
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load file: ${e.message}"
                )
            }
        }
    }

    fun onFileClicked(audioFileId: String) {
        _uiState.value = _uiState.value.copy(navigateToPlayer = audioFileId)
    }

    fun onNavigationHandled() {
        _uiState.value = _uiState.value.copy(navigateToPlayer = null)
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun getFileFormat(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp3" -> "mp3"
            "wav" -> "wav"
            "m4a" -> "m4a"
            "aac" -> "aac"
            "flac" -> "flac"
            "ogg" -> "ogg"
            "opus" -> "opus"
            "wma" -> "wma"
            "webm" -> "webm"
            else -> null
        }
    }

    private fun getAudioDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }
}
