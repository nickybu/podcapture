package com.podcapture.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.Podcast
import com.podcapture.data.repository.PodcastSearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PodcastSearchUiState(
    val query: String = "",
    val podcasts: List<Podcast> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

class PodcastSearchViewModel(
    private val repository: PodcastSearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastSearchUiState())
    val uiState: StateFlow<PodcastSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)

        // Debounce search
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(500) // Debounce 500ms
                performSearch(query)
            }
        } else if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                podcasts = emptyList(),
                hasSearched = false,
                error = null
            )
        }
    }

    fun onSearch() {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            viewModelScope.launch {
                performSearch(query)
            }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        repository.searchPodcasts(query).fold(
            onSuccess = { podcasts ->
                _uiState.value = _uiState.value.copy(
                    podcasts = podcasts,
                    isLoading = false,
                    hasSearched = true
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Search failed",
                    hasSearched = true
                )
            }
        )
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
