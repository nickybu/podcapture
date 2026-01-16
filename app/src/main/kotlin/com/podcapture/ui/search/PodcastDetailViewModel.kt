package com.podcapture.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.Episode
import com.podcapture.data.model.Podcast
import com.podcapture.data.repository.PodcastSearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PodcastDetailUiState(
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class PodcastDetailViewModel(
    private val podcastId: Long,
    private val repository: PodcastSearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    init {
        loadPodcast()
    }

    private fun loadPodcast() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load podcast details
            repository.getPodcastById(podcastId).fold(
                onSuccess = { podcast ->
                    _uiState.value = _uiState.value.copy(podcast = podcast)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load podcast"
                    )
                    return@launch
                }
            )

            // Load episodes
            repository.getEpisodesByPodcastId(podcastId).fold(
                onSuccess = { episodes ->
                    _uiState.value = _uiState.value.copy(
                        episodes = episodes,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load episodes"
                    )
                }
            )
        }
    }

    fun onRetry() {
        loadPodcast()
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
