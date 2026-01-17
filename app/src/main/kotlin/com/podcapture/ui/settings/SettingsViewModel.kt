package com.podcapture.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val captureWindowSeconds: Int = 30,
    val skipIntervalSeconds: Int = 10,
    val obsidianVaultUri: String = "",
    val obsidianVaultDisplayName: String = "",
    val obsidianDefaultTags: String = "inbox/, resources/references/podcasts",
    val apiCallCount: Int = 0,
    val isLoading: Boolean = true
)

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
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
    }

    fun setCaptureWindowSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowSeconds(seconds)
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
}
