package com.podcapture.ui.captures

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podcapture.data.model.Tag
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

/**
 * Every capture across podcasts and files, newest first, grouped by day, with search, tag and
 * source filters. Tapping a capture opens it in the Viewer, scrolled to that capture.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CapturesScreen(
    onNavigateBack: () -> Unit,
    onOpenCapture: (audioFileId: String, captureId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CapturesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val today = remember { LocalDate.now() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Captures") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.totalCount > 0) {
                CaptureFilters(
                    query = query,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearch = { focusManager.clearFocus() },
                    tags = uiState.availableTags,
                    selectedTagIds = uiState.selectedTagIds,
                    onTagToggled = viewModel::onTagToggled,
                    sourceFilter = uiState.sourceFilter,
                    onSourceFilterChanged = viewModel::onSourceFilterChanged
                )
            }

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                uiState.totalCount == 0 -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    title = "No captures yet",
                    message = "Press the capture button while listening to save a moment. " +
                        "Your captures from every podcast and file will appear here."
                )

                uiState.sections.isEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No matches",
                    message = "No captures match your search and filters.",
                    actionLabel = if (uiState.hasActiveFilters) "Clear filters" else null,
                    onAction = viewModel::onClearFilters
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.sections.forEach { section ->
                        stickyHeader(key = "day-${section.day}", contentType = "day") {
                            DayHeader(dayLabel(section.day, today))
                        }
                        items(
                            items = section.items,
                            key = { it.capture.id },
                            contentType = { "capture" }
                        ) { item ->
                            CaptureRow(
                                item = item,
                                onClick = {
                                    onOpenCapture(item.capture.audioFileId, item.capture.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureFilters(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    tags: List<Tag>,
    selectedTagIds: Set<String>,
    onTagToggled: (String) -> Unit,
    sourceFilter: CaptureSourceFilter,
    onSourceFilterChanged: (CaptureSourceFilter) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search transcripts, notes, titles") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )

        if (tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = { onTagToggled(tag.id) },
                        label = { Text(tag.name) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CaptureSourceFilter.entries, key = { it.name }) { filter ->
                FilterChip(
                    selected = sourceFilter == filter,
                    onClick = { onSourceFilterChanged(filter) },
                    label = {
                        Text(
                            when (filter) {
                                CaptureSourceFilter.All -> "All"
                                CaptureSourceFilter.Podcasts -> "Podcasts"
                                CaptureSourceFilter.Files -> "Files"
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    // Opaque so cards scrolling under the sticky header don't show through.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
