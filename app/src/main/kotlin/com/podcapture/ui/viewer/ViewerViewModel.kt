package com.podcapture.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.db.PodcastDao
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.Capture
import com.podcapture.data.model.EpisodePlaybackHistory
import com.podcapture.data.model.Tag
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.MarkdownManager
import com.podcapture.data.repository.TagRepository
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureWithTags(
    val capture: Capture,
    val tags: List<Tag>
)

data class ViewerUiState(
    val audioFile: AudioFile? = null,
    val capturesWithTags: List<CaptureWithTags> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val markdownContent: String? = null,
    val markdownFilePath: String? = null,
    val isLoading: Boolean = true,
    val editingCaptureId: String? = null,
    val editingNotes: String = "",
    val showTagDialog: Boolean = false,
    val editingTagsCaptureId: String? = null,
    val newTagName: String = "",
    // Episode info for episode captures
    val episodeTitle: String? = null,
    val episodePlaybackHistory: EpisodePlaybackHistory? = null,
    // Obsidian export state
    val showObsidianDialog: Boolean = false,
    val obsidianTitle: String = "",
    val obsidianTagsInput: String = "",
    val obsidianTags: List<String> = emptyList(),
    val obsidianPreview: String = "",
    val obsidianVaultUri: String = "",
    val obsidianDefaultTags: String = "inbox/, resources/references/podcasts",
    val obsidianExportResult: ObsidianExportResult? = null
) {
    // Helper property for backwards compatibility
    val captures: List<Capture> get() = capturesWithTags.map { it.capture }
}

sealed class ObsidianExportResult {
    data class Success(val filePath: String) : ObsidianExportResult()
    data class Error(val message: String) : ObsidianExportResult()
}

class ViewerViewModel(
    private val audioFileId: String,
    private val context: Context,
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    private val tagRepository: TagRepository,
    private val markdownManager: MarkdownManager,
    private val settingsDataStore: SettingsDataStore,
    private val podcastDao: PodcastDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadData()
        loadEpisodeInfoIfNeeded()
        observeTags()
        observeVaultPath()
    }

    /**
     * Loads episode info if this is an episode capture (audioFileId starts with "episode_").
     */
    private fun loadEpisodeInfoIfNeeded() {
        if (!audioFileId.startsWith("episode_")) return

        viewModelScope.launch {
            try {
                val episodeId = audioFileId.removePrefix("episode_").toLongOrNull() ?: return@launch
                val episode = podcastDao.getEpisodeById(episodeId)
                val playbackHistory = podcastDao.getPlaybackHistoryForEpisode(episodeId)

                _uiState.value = _uiState.value.copy(
                    episodeTitle = episode?.title,
                    episodePlaybackHistory = playbackHistory
                )
            } catch (e: Exception) {
                // Ignore errors - episode info is optional
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load audio file
            val audioFile = audioFileRepository.getFileByIdOnce(audioFileId)
            if (audioFile != null) {
                _uiState.value = _uiState.value.copy(
                    audioFile = audioFile,
                    markdownContent = captureRepository.getMarkdownContent(audioFile),
                    markdownFilePath = captureRepository.getMarkdownFilePath(audioFile)
                )
            }

            // Observe captures
            captureRepository.getCapturesForFile(audioFileId).collect { captures ->
                val currentAudioFile = _uiState.value.audioFile
                val capturesWithTags = captures.map { capture ->
                    CaptureWithTags(
                        capture = capture,
                        tags = tagRepository.getTagsForCaptureOnce(capture.id)
                    )
                }
                _uiState.value = _uiState.value.copy(
                    capturesWithTags = capturesWithTags,
                    markdownContent = currentAudioFile?.let { captureRepository.getMarkdownContent(it) },
                    isLoading = false
                )
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

    fun onEditNotes(captureId: String, currentNotes: String?) {
        _uiState.value = _uiState.value.copy(
            editingCaptureId = captureId,
            editingNotes = currentNotes ?: ""
        )
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(editingNotes = notes)
    }

    fun onSaveNotes() {
        val captureId = _uiState.value.editingCaptureId ?: return
        val notes = _uiState.value.editingNotes.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            val audioFile = _uiState.value.audioFile
            if (audioFile != null) {
                // Update with markdown file sync
                captureRepository.updateCaptureNotes(captureId, notes, audioFile)
            } else {
                // Just save notes without markdown (e.g., for episodes)
                captureRepository.updateCaptureNotesSimple(captureId, notes)
            }
            _uiState.value = _uiState.value.copy(
                editingCaptureId = null,
                editingNotes = ""
            )
        }
    }

    fun onDismissEditNotes() {
        _uiState.value = _uiState.value.copy(
            editingCaptureId = null,
            editingNotes = ""
        )
    }

    // Tag management
    fun onOpenTagDialog(captureId: String) {
        _uiState.value = _uiState.value.copy(
            showTagDialog = true,
            editingTagsCaptureId = captureId
        )
    }

    fun onCloseTagDialog() {
        _uiState.value = _uiState.value.copy(
            showTagDialog = false,
            editingTagsCaptureId = null,
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
            // If editing a capture, add the new tag to it
            _uiState.value.editingTagsCaptureId?.let { captureId ->
                tagRepository.addTagToCapture(captureId, tag.id)
            }
            _uiState.value = _uiState.value.copy(newTagName = "")
            refreshCaptureTags()
        }
    }

    fun onToggleTagForCapture(tagId: String) {
        val captureId = _uiState.value.editingTagsCaptureId ?: return
        val currentCapture = _uiState.value.capturesWithTags.find { it.capture.id == captureId } ?: return
        val hasTag = currentCapture.tags.any { it.id == tagId }

        viewModelScope.launch {
            if (hasTag) {
                tagRepository.removeTagFromCapture(captureId, tagId)
            } else {
                tagRepository.addTagToCapture(captureId, tagId)
            }
            refreshCaptureTags()
        }
    }

    fun onDeleteTag(tag: Tag) {
        viewModelScope.launch {
            tagRepository.deleteTag(tag)
            refreshCaptureTags()
        }
    }

    private suspend fun refreshCaptureTags() {
        val captures = captureRepository.getCapturesForFileOnce(audioFileId)
        val capturesWithTags = captures.map { capture ->
            CaptureWithTags(
                capture = capture,
                tags = tagRepository.getTagsForCaptureOnce(capture.id)
            )
        }
        _uiState.value = _uiState.value.copy(capturesWithTags = capturesWithTags)
    }

    private fun observeVaultPath() {
        viewModelScope.launch {
            settingsDataStore.obsidianVaultUri.collect { uri ->
                _uiState.value = _uiState.value.copy(obsidianVaultUri = uri)
            }
        }
        viewModelScope.launch {
            settingsDataStore.obsidianDefaultTags.collect { tags ->
                _uiState.value = _uiState.value.copy(obsidianDefaultTags = tags)
            }
        }
    }

    // Obsidian export methods
    fun onOpenObsidianDialog() {
        // Get title from audioFile, episode title, or fallback to capture timestamp
        val title = _uiState.value.audioFile?.name
            ?: _uiState.value.episodeTitle
            ?: _uiState.value.captures.firstOrNull()?.let {
                // For captures without episode info, use timestamp
                "Capture - ${formatTimestamp(it.timestampMs)}"
            }
            ?: audioFileId.removePrefix("episode_")

        val sanitizedTitle = markdownManager.sanitizeFilename(title)
        val preview = generateObsidianPreview(sanitizedTitle, emptyList())

        _uiState.value = _uiState.value.copy(
            showObsidianDialog = true,
            obsidianTitle = sanitizedTitle,
            obsidianTagsInput = "",
            obsidianTags = emptyList(),
            obsidianPreview = preview,
            obsidianExportResult = null
        )
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun onCloseObsidianDialog() {
        _uiState.value = _uiState.value.copy(
            showObsidianDialog = false,
            obsidianExportResult = null
        )
    }

    fun onObsidianTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(
            obsidianTitle = title,
            obsidianPreview = generateObsidianPreview(title, _uiState.value.obsidianTags)
        )
    }

    fun onObsidianTagsInputChanged(input: String) {
        // Parse tags from input - split by space
        val tags = input.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        _uiState.value = _uiState.value.copy(
            obsidianTagsInput = input,
            obsidianTags = tags,
            obsidianPreview = generateObsidianPreview(_uiState.value.obsidianTitle, tags)
        )
    }

    fun onRemoveObsidianTag(index: Int) {
        val tags = _uiState.value.obsidianTags.toMutableList()
        if (index in tags.indices) {
            tags.removeAt(index)
            val newInput = tags.joinToString(" ")
            _uiState.value = _uiState.value.copy(
                obsidianTagsInput = newInput,
                obsidianTags = tags,
                obsidianPreview = generateObsidianPreview(_uiState.value.obsidianTitle, tags)
            )
        }
    }

    private fun generateObsidianPreview(title: String, tags: List<String>): String {
        val audioFile = _uiState.value.audioFile
        val captures = _uiState.value.captures
        val defaultTags = _uiState.value.obsidianDefaultTags
        val playbackHistory = _uiState.value.episodePlaybackHistory

        return if (audioFile != null) {
            markdownManager.generateObsidianContent(audioFile, captures, title, tags, defaultTags)
        } else {
            // Use simple version for episodes without AudioFile entry
            // Include first/last listened from playback history if available
            markdownManager.generateObsidianContentSimple(
                captures = captures,
                title = title,
                userTags = tags,
                defaultTags = defaultTags,
                firstListenedAt = playbackHistory?.firstPlayedAt,
                lastListenedAt = playbackHistory?.lastPlayedAt
            )
        }
    }

    fun getObsidianContent(): String {
        return generateObsidianPreview(
            _uiState.value.obsidianTitle,
            _uiState.value.obsidianTags
        )
    }

    fun exportToObsidianVault() {
        val vaultUriString = _uiState.value.obsidianVaultUri
        val title = _uiState.value.obsidianTitle
        val content = getObsidianContent()

        if (vaultUriString.isBlank()) {
            _uiState.value = _uiState.value.copy(
                obsidianExportResult = ObsidianExportResult.Error("Vault folder not selected. Go to Settings to select your Obsidian vault folder.")
            )
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val vaultUri = Uri.parse(vaultUriString)
                val vaultDir = DocumentFile.fromTreeUri(context, vaultUri)
                    ?: throw IllegalStateException("Cannot access vault folder")

                // Sanitize filename
                val safeTitle = title
                    .replace(Regex("[\\\\/:*?\"<>|#]"), "-")  // Replace problematic chars
                    .replace(Regex("-[\\s-]*-"), "-")  // Collapse dashes with spaces/dashes between them
                    .replace(Regex("\\s+"), " ")  // Collapse multiple spaces
                    .trim('-', ' ')
                val fileName = "${safeTitle}.md"

                // Check if file already exists and delete it
                vaultDir.findFile(fileName)?.delete()

                // Create new file
                val newFile = vaultDir.createFile("text/markdown", safeTitle)
                    ?: throw IllegalStateException("Cannot create file in vault folder")

                // Write content
                context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                } ?: throw IllegalStateException("Cannot write to file")

                _uiState.value = _uiState.value.copy(
                    obsidianExportResult = ObsidianExportResult.Success(fileName),
                    showObsidianDialog = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    obsidianExportResult = ObsidianExportResult.Error("Export failed: ${e.message}")
                )
            }
        }
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(obsidianExportResult = null)
    }
}
