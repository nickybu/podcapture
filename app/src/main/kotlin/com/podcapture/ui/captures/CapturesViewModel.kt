package com.podcapture.ui.captures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.model.Tag
import com.podcapture.data.repository.CaptureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId

data class CapturesUiState(
    val isLoading: Boolean = true,
    /** Captures in the database, before filtering. */
    val totalCount: Int = 0,
    /** Tags applied to at least one capture, for the filter chips. */
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<String> = emptySet(),
    val sourceFilter: CaptureSourceFilter = CaptureSourceFilter.All,
    val sections: List<CaptureDaySection> = emptyList(),
    /** Whether a search query (as applied, i.e. debounced), tag or source filter is active. */
    val hasActiveFilters: Boolean = false
)

private const val QUERY_DEBOUNCE_MS = 200L

class CapturesViewModel(
    captureRepository: CaptureRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** The search field's text, updated on every keystroke. Filtering uses a debounced copy. */
    val query: StateFlow<String> = _query.asStateFlow()

    private val selectedTagIds = MutableStateFlow<Set<String>>(emptySet())
    private val sourceFilter = MutableStateFlow(CaptureSourceFilter.All)

    // Three queries (captures, capture-tag pairs, sources) joined in memory: no per-capture lookups.
    private val allItems = combine(
        captureRepository.getAllCapturesNewestFirst(),
        captureRepository.getAllCaptureTags(),
        captureRepository.getCaptureSources(),
        ::buildCaptureItems
    ).flowOn(Dispatchers.Default)

    // Clearing the search applies at once; typing waits for a 200 ms pause.
    @OptIn(FlowPreview::class)
    private val appliedQuery = _query
        .debounce { if (it.isBlank()) 0L else QUERY_DEBOUNCE_MS }
        .distinctUntilChanged()

    val uiState: StateFlow<CapturesUiState> = combine(
        allItems,
        appliedQuery,
        selectedTagIds,
        sourceFilter
    ) { items, query, selectedIds, source ->
        val tags = tagsInUse(items)
        // A selected tag that was deleted (or removed from its last capture) no longer filters.
        val effectiveSelected = selectedIds.intersect(tags.mapTo(HashSet()) { it.id })
        val filtered = filterCaptures(items, query, effectiveSelected, source)
        CapturesUiState(
            isLoading = false,
            totalCount = items.size,
            availableTags = tags,
            selectedTagIds = effectiveSelected,
            sourceFilter = source,
            sections = groupByDay(filtered, ZoneId.systemDefault()),
            hasActiveFilters = query.isNotBlank() || effectiveSelected.isNotEmpty() ||
                source != CaptureSourceFilter.All
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CapturesUiState())

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun onTagToggled(tagId: String) {
        selectedTagIds.update { if (tagId in it) it - tagId else it + tagId }
    }

    fun onSourceFilterChanged(filter: CaptureSourceFilter) {
        sourceFilter.value = filter
    }

    fun onClearFilters() {
        _query.value = ""
        selectedTagIds.value = emptySet()
        sourceFilter.value = CaptureSourceFilter.All
    }
}
