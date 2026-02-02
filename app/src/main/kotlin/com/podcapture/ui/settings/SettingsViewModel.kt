package com.podcapture.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val captureWindowSeconds: Int = 30,
    val captureWindowStep: Int = 5,
    val skipIntervalSeconds: Int = 10,
    val obsidianVaultUri: String = "",
    val obsidianVaultDisplayName: String = "",
    val obsidianDefaultTags: String = "inbox/, resources/references/podcasts",
    val apiCallCount: Int = 0,
    val themeBackgroundColor: String = "#13293D",
    val themeAccent1Color: String = "#2A628F",
    val themeAccent2Color: String = "#3E92CC",
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val importResult: PodcastRepository.ImportResult? = null,
    val exportResult: ExportResult? = null
)

data class ImportProgress(
    val current: Int,
    val total: Int,
    val currentPodcast: String
)

sealed class ExportResult {
    data class Success(val count: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val podcastRepository: PodcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.captureWindowSeconds.collect { seconds ->
                _uiState.value = _uiState.value.copy(
                    captureWindowSeconds = seconds,
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.captureWindowStep.collect { step ->
                _uiState.value = _uiState.value.copy(
                    captureWindowStep = step
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.skipIntervalSeconds.collect { seconds ->
                _uiState.value = _uiState.value.copy(
                    skipIntervalSeconds = seconds
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.obsidianVaultUri.collect { uri ->
                val displayName = if (uri.isNotBlank()) {
                    android.net.Uri.parse(uri).lastPathSegment?.replace("primary:", "") ?: "Selected"
                } else ""
                _uiState.value = _uiState.value.copy(
                    obsidianVaultUri = uri,
                    obsidianVaultDisplayName = displayName
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.obsidianDefaultTags.collect { tags ->
                _uiState.value = _uiState.value.copy(
                    obsidianDefaultTags = tags
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.apiCallCount.collect { count ->
                _uiState.value = _uiState.value.copy(
                    apiCallCount = count
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeBackgroundColor.collect { color ->
                _uiState.value = _uiState.value.copy(
                    themeBackgroundColor = color
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeAccent1Color.collect { color ->
                _uiState.value = _uiState.value.copy(
                    themeAccent1Color = color
                )
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeAccent2Color.collect { color ->
                _uiState.value = _uiState.value.copy(
                    themeAccent2Color = color
                )
            }
        }
    }

    fun setCaptureWindowSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowSeconds(seconds)
        }
    }

    fun setCaptureWindowStep(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowStep(seconds)
        }
    }

    fun setSkipIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setSkipIntervalSeconds(seconds)
        }
    }

    fun setObsidianVaultUri(uri: String) {
        viewModelScope.launch {
            settingsDataStore.setObsidianVaultUri(uri)
        }
    }

    fun setObsidianDefaultTags(tags: String) {
        viewModelScope.launch {
            settingsDataStore.setObsidianDefaultTags(tags)
        }
    }

    fun setThemeBackgroundColor(color: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeBackgroundColor(color)
        }
    }

    fun setThemeAccent1Color(color: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeAccent1Color(color)
        }
    }

    fun setThemeAccent2Color(color: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeAccent2Color(color)
        }
    }

    fun resetThemeColors() {
        viewModelScope.launch {
            settingsDataStore.resetThemeColors()
        }
    }

    // ============ OPML Import/Export ============

    fun importOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                importProgress = null,
                importResult = null
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        podcastRepository.importFromOpml(inputStream) { current, total, podcast ->
                            _uiState.value = _uiState.value.copy(
                                importProgress = ImportProgress(current, total, podcast)
                            )
                        }
                    } ?: Result.failure(Exception("Could not open file for reading"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            _uiState.value = _uiState.value.copy(
                isImporting = false,
                importProgress = null,
                importResult = result.getOrNull()
            )
        }
    }

    fun exportOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        podcastRepository.exportToOpml(outputStream)
                    } ?: Result.failure(Exception("Could not open file for writing"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            _uiState.value = _uiState.value.copy(
                exportResult = if (result.isSuccess) {
                    ExportResult.Success(result.getOrDefault(0))
                } else {
                    ExportResult.Error(result.exceptionOrNull()?.message ?: "Export failed")
                }
            )
        }
    }

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }
}
