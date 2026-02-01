package com.podcapture.ui.home

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.podcapture.youtube.YouTubeCaptchaActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.BookmarkedPodcast
import com.podcapture.data.model.Tag
import com.podcapture.ui.home.components.AddSourceBottomSheet
import com.podcapture.ui.home.components.YouTubeUrlDialog
import com.podcapture.youtube.YouTubeDownloadState
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (audioFileId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPodcastSearch: () -> Unit = {},
    onNavigateToPodcastDetail: (podcastId: Long) -> Unit = {},
    onNavigateToEpisodePlayer: (episodeId: Long, podcastId: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so we can access the file later
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onFileSelected(context, it)
        }
    }

    val captchaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onCaptchaSolved()
        } else {
            viewModel.onCaptchaCancelled()
        }
    }

    // Handle navigation to player
    LaunchedEffect(uiState.navigateToPlayer) {
        uiState.navigateToPlayer?.let { audioFileId ->
            onNavigateToPlayer(audioFileId)
            viewModel.onNavigationHandled()
        }
    }

    // Handle navigation to podcast detail
    LaunchedEffect(uiState.navigateToPodcast) {
        uiState.navigateToPodcast?.let { podcastId ->
            onNavigateToPodcastDetail(podcastId)
            viewModel.onPodcastNavigationHandled()
        }
    }

    // Handle navigation to episode player
    LaunchedEffect(uiState.navigateToEpisode) {
        uiState.navigateToEpisode?.let { (episodeId, podcastId) ->
            onNavigateToEpisodePlayer(episodeId, podcastId)
            viewModel.onEpisodeNavigationHandled()
        }
    }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onErrorDismissed()
        }
    }

    // Handle YouTube download state changes
    LaunchedEffect(uiState.youTubeDownloadState) {
        when (val state = uiState.youTubeDownloadState) {
            is YouTubeDownloadState.Completed -> {
                val result = snackbarHostState.showSnackbar(
                    message = "Downloaded: ${state.title}",
                    actionLabel = "Open"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToPlayer(state.audioFileId)
                }
                viewModel.onYouTubeDownloadStateHandled()
            }
            is YouTubeDownloadState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.onYouTubeDownloadStateHandled()
            }
            is YouTubeDownloadState.Downloading -> {
                // Show a snackbar when download starts (only at 0%)
                if (state.percent == 0) {
                    snackbarHostState.showSnackbar("Download started: ${state.title}")
                }
            }
            is YouTubeDownloadState.NeedsCaptcha -> {
                // Launch captcha activity
                val intent = YouTubeCaptchaActivity.createIntent(context, state.url)
                captchaLauncher.launch(intent)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PodCapture") },
                actions = {
                    IconButton(onClick = onNavigateToPodcastSearch) {
                        Icon(Icons.Default.Podcasts, contentDescription = "Search Podcasts")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onShowAddSourceSheet() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add audio")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.audioFiles.isEmpty() && uiState.bookmarkItems.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.audioFiles.isEmpty() && uiState.bookmarkItems.isEmpty() && uiState.selectedTagId == null) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tag filter chips
                    if (uiState.allTags.isNotEmpty()) {
                        item {
                            TagFilterRow(
                                tags = uiState.allTags,
                                selectedTagId = uiState.selectedTagId,
                                onTagSelected = viewModel::onTagFilterSelected
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Bookmarks section (combined podcasts + audio files)
                    if (uiState.bookmarkItems.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bookmarks",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { viewModel.onToggleBookmarkViewMode() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = when (uiState.bookmarkViewMode) {
                                            BookmarkViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                                            BookmarkViewMode.LIST -> Icons.Default.GridView
                                        },
                                        contentDescription = when (uiState.bookmarkViewMode) {
                                            BookmarkViewMode.GRID -> "Switch to list view"
                                            BookmarkViewMode.LIST -> "Switch to grid view"
                                        },
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Filter chips row
                        item {
                            BookmarkFilterChips(
                                selectedFilter = uiState.bookmarkFilterType,
                                podcastCount = uiState.podcastBookmarkCount,
                                fileCount = uiState.fileBookmarkCount,
                                onFilterSelected = { viewModel.onBookmarkFilterChanged(it) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Show bookmarks based on view mode
                        val displayedBookmarks = uiState.filteredBookmarkItems
                        if (displayedBookmarks.isEmpty()) {
                            item {
                                Text(
                                    text = when (uiState.bookmarkFilterType) {
                                        BookmarkFilterType.PODCASTS -> "No bookmarked podcasts"
                                        BookmarkFilterType.FILES -> "No bookmarked files"
                                        BookmarkFilterType.ALL -> "No bookmarks"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            when (uiState.bookmarkViewMode) {
                                BookmarkViewMode.GRID -> {
                                    item {
                                        BookmarksGrid(
                                            items = displayedBookmarks,
                                            onPodcastClick = { viewModel.onPodcastClicked(it) },
                                            onAudioFileClick = { viewModel.onFileClicked(it) }
                                        )
                                    }
                                }
                                BookmarkViewMode.LIST -> {
                                    items(displayedBookmarks, key = { item ->
                                        when (item) {
                                            is BookmarkItem.PodcastBookmark -> "podcast_${item.podcast.id}"
                                            is BookmarkItem.AudioFileBookmark -> "file_${item.audioFile.id}"
                                        }
                                    }) { item ->
                                        BookmarkListItem(
                                            item = item,
                                            onPodcastClick = { viewModel.onPodcastClicked(it) },
                                            onAudioFileClick = { viewModel.onFileClicked(it) },
                                            onUnbookmarkPodcast = { viewModel.onUnbookmarkPodcast(it) },
                                            onToggleAudioFileBookmark = { viewModel.onToggleAudioFileBookmark(it) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // History section (unified recent files + episodes)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "History",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sort toggle
                                IconButton(
                                    onClick = { viewModel.onToggleSortOrder() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = when (uiState.recentSortOrder) {
                                            SortOrder.NEWEST -> "Sorted newest first"
                                            SortOrder.OLDEST -> "Sorted oldest first"
                                        },
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Search field
                    item {
                        OutlinedTextField(
                            value = uiState.historySearchQuery,
                            onValueChange = { viewModel.onHistorySearchQueryChanged(it) },
                            placeholder = { Text("Search history...") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            },
                            trailingIcon = {
                                if (uiState.historySearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onHistorySearchQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Filter chips for history items
                    item {
                        RecentFilterChips(
                            selectedFilter = uiState.recentFilterType,
                            fileCount = uiState.recentFileCount,
                            episodeCount = uiState.recentEpisodeCount,
                            downloadedCount = uiState.downloadedEpisodeCount,
                            onFilterSelected = { viewModel.onRecentFilterChanged(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Sort order indicator
                    item {
                        Text(
                            text = when (uiState.recentSortOrder) {
                                SortOrder.NEWEST -> "Newest first"
                                SortOrder.OLDEST -> "Oldest first"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val displayedRecent = uiState.filteredRecentItems
                    if (displayedRecent.isEmpty()) {
                        item {
                            Text(
                                text = when {
                                    uiState.historySearchQuery.isNotEmpty() -> "No results for \"${uiState.historySearchQuery}\""
                                    uiState.recentFilterType == RecentFilterType.FILES -> "No files played yet"
                                    uiState.recentFilterType == RecentFilterType.EPISODES -> "No episodes played yet"
                                    uiState.recentFilterType == RecentFilterType.DOWNLOADED -> "No downloaded episodes"
                                    else -> "No history yet. Play audio files or podcast episodes to see them here."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(displayedRecent, key = { it.id }) { item ->
                            RecentItemCard(
                                item = item,
                                showDeleteButton = uiState.recentFilterType == RecentFilterType.DOWNLOADED,
                                onFileClick = { fileId ->
                                    viewModel.onFileClicked(fileId)
                                },
                                onEpisodeClick = { episodeId, podcastId ->
                                    viewModel.onEpisodeClicked(episodeId, podcastId)
                                },
                                onDeleteClick = { episode ->
                                    viewModel.onRequestDeleteDownload(episode)
                                }
                            )
                        }
                    }
                }
            }

            // Loading overlay
            if (uiState.isLoading && uiState.audioFiles.isNotEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // Tag editing dialog
    if (uiState.showTagDialog) {
        val editingFile = uiState.audioFiles.find { it.audioFile.id == uiState.editingAudioFileId }
        TagEditDialog(
            allTags = uiState.allTags,
            selectedTagIds = editingFile?.tags?.map { it.id } ?: emptyList(),
            newTagName = uiState.newTagName,
            onNewTagNameChanged = viewModel::onNewTagNameChanged,
            onCreateTag = viewModel::onCreateTag,
            onToggleTag = viewModel::onToggleTagForAudioFile,
            onDeleteTag = viewModel::onDeleteTag,
            onDismiss = viewModel::onCloseTagDialog
        )
    }

    // Delete download confirmation dialog
    if (uiState.showDeleteConfirmDialog && uiState.episodeToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteDialog() },
            title = { Text("Delete Download") },
            text = {
                Text("Delete the downloaded file for \"${uiState.episodeToDelete!!.title}\"?\n\nYour playback history and captures will be preserved.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmDeleteDownload() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add source bottom sheet
    if (uiState.showAddSourceSheet) {
        AddSourceBottomSheet(
            sheetState = bottomSheetState,
            onDismiss = { viewModel.onDismissAddSourceSheet() },
            onImportLocalFile = {
                // Support all common audio formats - ExoPlayer handles decoding
                filePickerLauncher.launch(arrayOf(
                    "audio/mpeg",        // MP3
                    "audio/wav",         // WAV
                    "audio/x-wav",       // WAV alternate
                    "audio/mp4",         // M4A
                    "audio/x-m4a",       // M4A alternate
                    "audio/aac",         // AAC
                    "audio/flac",        // FLAC
                    "audio/ogg",         // OGG Vorbis
                    "audio/opus",        // Opus
                    "audio/*"            // Fallback for any other audio
                ))
            },
            onImportFromYouTube = { viewModel.onShowYouTubeUrlDialog() }
        )
    }

    // YouTube URL dialog
    if (uiState.showYouTubeUrlDialog) {
        YouTubeUrlDialog(
            onDismiss = { viewModel.onDismissYouTubeUrlDialog() },
            onImport = { url -> viewModel.onYouTubeImport(url) }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AudioFile,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No audio files yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to open an audio file",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterRow(
    tags: List<Tag>,
    selectedTagId: String?,
    onTagSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTagId == null,
            onClick = { onTagSelected(null) },
            label = { Text("All") },
            leadingIcon = if (selectedTagId == null) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null
        )

        tags.forEach { tag ->
            FilterChip(
                selected = selectedTagId == tag.id,
                onClick = { onTagSelected(if (selectedTagId == tag.id) null else tag.id) },
                label = { Text(tag.name) },
                leadingIcon = if (selectedTagId == tag.id) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(18.dp)) }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagEditDialog(
    allTags: List<Tag>,
    selectedTagIds: List<String>,
    newTagName: String,
    onNewTagNameChanged: (String) -> Unit,
    onCreateTag: () -> Unit,
    onToggleTag: (String) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags") },
        text = {
            Column {
                // Existing tags
                if (allTags.isNotEmpty()) {
                    Text(
                        text = "Select tags:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            val isSelected = tag.id in selectedTagIds
                            InputChip(
                                selected = isSelected,
                                onClick = { onToggleTag(tag.id) },
                                label = { Text(tag.name) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete tag",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onDeleteTag(tag) }
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Create new tag
                Text(
                    text = "Create new tag:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = onNewTagNameChanged,
                        placeholder = { Text("Tag name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onCreateTag,
                        enabled = newTagName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create tag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioFileCard(
    item: AudioFileWithCaptureCount,
    onClick: () -> Unit,
    onTagClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.audioFile.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatDuration(item.audioFile.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Show first and last played timestamps
                if (item.audioFile.firstPlayedAt != null || item.audioFile.lastPlayedAt != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.audioFile.firstPlayedAt?.let { first ->
                            Text(
                                text = "First: ${formatShortDate(first)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        item.audioFile.lastPlayedAt?.let { last ->
                            Text(
                                text = "Last: ${formatRelativeTime(last)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Show tags
                if (item.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Tag button
                IconButton(
                    onClick = onTagClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        contentDescription = "Edit tags",
                        modifier = Modifier.size(20.dp),
                        tint = if (item.tags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.captureCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Captures",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${item.captureCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatShortDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Extracts a date from a filename and formats it.
 * Supports patterns like: "2024-01-15", "20240115", "Jan 15 2024"
 * Returns the extracted date formatted as "MMM d" or filename if no date found.
 */
private fun extractDateFromFilename(filename: String): String {
    // Try pattern: yyyy-MM-dd (e.g., "2024-01-15_recording.mp3")
    val isoPattern = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    isoPattern.find(filename)?.let { match ->
        try {
            val (year, month, day) = match.destructured
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("$year-$month-$day")
            return date?.let { SimpleDateFormat("MMM d", Locale.getDefault()).format(it) } ?: filename
        } catch (e: Exception) { /* continue */ }
    }

    // Try pattern: yyyyMMdd (e.g., "20240115_audio.mp3")
    val compactPattern = Regex("""(\d{8})""")
    compactPattern.find(filename)?.let { match ->
        try {
            val dateStr = match.groupValues[1]
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).parse(dateStr)
            return date?.let { SimpleDateFormat("MMM d", Locale.getDefault()).format(it) } ?: filename
        } catch (e: Exception) { /* continue */ }
    }

    // Try pattern: MMM d, yyyy or MMM d yyyy (e.g., "Recording Jan 15, 2024.mp3")
    val monthPattern = Regex("""(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}),?\s+(\d{4})""", RegexOption.IGNORE_CASE)
    monthPattern.find(filename)?.let { match ->
        try {
            val date = SimpleDateFormat("MMM d yyyy", Locale.US).parse(
                "${match.groupValues[1]} ${match.groupValues[2]} ${match.groupValues[3]}"
            )
            return date?.let { SimpleDateFormat("MMM d", Locale.getDefault()).format(it) } ?: filename
        } catch (e: Exception) { /* continue */ }
    }

    // No date found - return truncated filename
    val nameWithoutExt = filename.substringBeforeLast(".")
    return if (nameWithoutExt.length > 8) nameWithoutExt.take(8) + "…" else nameWithoutExt
}

/**
 * Filter chips for bookmark types (All / Podcasts / Files)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkFilterChips(
    selectedFilter: BookmarkFilterType,
    podcastCount: Int,
    fileCount: Int,
    onFilterSelected: (BookmarkFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == BookmarkFilterType.ALL,
            onClick = { onFilterSelected(BookmarkFilterType.ALL) },
            label = { Text("All") },
            leadingIcon = if (selectedFilter == BookmarkFilterType.ALL) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null
        )

        FilterChip(
            selected = selectedFilter == BookmarkFilterType.PODCASTS,
            onClick = { onFilterSelected(BookmarkFilterType.PODCASTS) },
            label = { Text("Podcasts ($podcastCount)") },
            leadingIcon = if (selectedFilter == BookmarkFilterType.PODCASTS) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                { Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        )

        FilterChip(
            selected = selectedFilter == BookmarkFilterType.FILES,
            onClick = { onFilterSelected(BookmarkFilterType.FILES) },
            label = { Text("Files ($fileCount)") },
            leadingIcon = if (selectedFilter == BookmarkFilterType.FILES) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        )
    }
}

/**
 * Grid view showing bookmark cover photos (5 per row).
 * No bookmark toggle in this view - just the artwork.
 */
@Composable
private fun BookmarksGrid(
    items: List<BookmarkItem>,
    onPodcastClick: (Long) -> Unit,
    onAudioFileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier
            .fillMaxWidth()
            .height((((items.size + 4) / 5) * 72).dp), // Calculate height based on rows
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false // Parent LazyColumn handles scrolling
    ) {
        items(items, key = { item ->
            when (item) {
                is BookmarkItem.PodcastBookmark -> "grid_podcast_${item.podcast.id}"
                is BookmarkItem.AudioFileBookmark -> "grid_file_${item.audioFile.id}"
            }
        }) { item ->
            BookmarkGridItem(
                item = item,
                onClick = {
                    when (item) {
                        is BookmarkItem.PodcastBookmark -> onPodcastClick(item.podcast.id)
                        is BookmarkItem.AudioFileBookmark -> onAudioFileClick(item.audioFile.id)
                    }
                }
            )
        }
    }
}

/**
 * Single item in the bookmark grid - just the cover photo.
 */
@Composable
private fun BookmarkGridItem(
    item: BookmarkItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        when (item) {
            is BookmarkItem.PodcastBookmark -> {
                AsyncImage(
                    model = item.podcast.artworkUrl,
                    contentDescription = item.podcast.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is BookmarkItem.AudioFileBookmark -> {
                // Audio files don't have artwork, show icon with date from filename
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = item.audioFile.name,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = extractDateFromFilename(item.audioFile.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * List view item for bookmarks - shows full details with bookmark toggle.
 */
@Composable
private fun BookmarkListItem(
    item: BookmarkItem,
    onPodcastClick: (Long) -> Unit,
    onAudioFileClick: (String) -> Unit,
    onUnbookmarkPodcast: (Long) -> Unit,
    onToggleAudioFileBookmark: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                when (item) {
                    is BookmarkItem.PodcastBookmark -> onPodcastClick(item.podcast.id)
                    is BookmarkItem.AudioFileBookmark -> onAudioFileClick(item.audioFile.id)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (item) {
                is BookmarkItem.PodcastBookmark -> {
                    // Podcast artwork
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        AsyncImage(
                            model = item.podcast.artworkUrl,
                            contentDescription = item.podcast.title,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.podcast.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.podcast.author.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.podcast.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.podcast.episodeCount} episodes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { onUnbookmarkPodcast(item.podcast.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Remove bookmark",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is BookmarkItem.AudioFileBookmark -> {
                    // Audio file icon
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.audioFile.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatDuration(item.audioFile.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onToggleAudioFileBookmark(item.audioFile.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (item.audioFile.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (item.audioFile.isBookmarked) "Remove bookmark" else "Add bookmark",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Filter chips for history items (All / Files / Episodes / Downloaded)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentFilterChips(
    selectedFilter: RecentFilterType,
    fileCount: Int,
    episodeCount: Int,
    downloadedCount: Int,
    onFilterSelected: (RecentFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == RecentFilterType.ALL,
            onClick = { onFilterSelected(RecentFilterType.ALL) },
            label = { Text("All") },
            leadingIcon = if (selectedFilter == RecentFilterType.ALL) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        )

        FilterChip(
            selected = selectedFilter == RecentFilterType.FILES,
            onClick = { onFilterSelected(RecentFilterType.FILES) },
            label = { Text("Files ($fileCount)") },
            leadingIcon = if (selectedFilter == RecentFilterType.FILES) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                { Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        )

        FilterChip(
            selected = selectedFilter == RecentFilterType.EPISODES,
            onClick = { onFilterSelected(RecentFilterType.EPISODES) },
            label = { Text("Episodes ($episodeCount)") },
            leadingIcon = if (selectedFilter == RecentFilterType.EPISODES) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                { Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        )

        FilterChip(
            selected = selectedFilter == RecentFilterType.DOWNLOADED,
            onClick = { onFilterSelected(RecentFilterType.DOWNLOADED) },
            label = { Text("Downloaded ($downloadedCount)") },
            leadingIcon = if (selectedFilter == RecentFilterType.DOWNLOADED) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }
        )
    }
}

/**
 * Card for a recent item (audio file or episode).
 */
@Composable
private fun RecentItemCard(
    item: RecentItem,
    showDeleteButton: Boolean = false,
    onFileClick: (String) -> Unit,
    onEpisodeClick: (Long, Long) -> Unit,
    onDeleteClick: (RecentItem.EpisodeRecent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                when (item) {
                    is RecentItem.AudioFileRecent -> {
                        // Extract the original file ID (remove "file_" prefix)
                        val fileId = item.id.removePrefix("file_")
                        onFileClick(fileId)
                    }
                    is RecentItem.EpisodeRecent -> onEpisodeClick(item.episodeId, item.podcastId)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (item) {
                is RecentItem.AudioFileRecent -> {
                    // Audio file icon
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,  // Duration
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatRelativeTime(item.lastPlayedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.captureCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Captures",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.captureCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                is RecentItem.EpisodeRecent -> {
                    // Episode artwork
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = item.title,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,  // Podcast title
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatRelativeTime(item.lastPlayedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!item.isDownloaded) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Not downloaded",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Delete button (shown in Downloaded filter mode)
                        if (showDeleteButton && item.isDownloaded) {
                            IconButton(
                                onClick = { onDeleteClick(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete download",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (item.captureCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Captures",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${item.captureCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        // Progress indicator (position / duration)
                        if (item.positionMs > 0 && item.durationMs > 0) {
                            val progressText = "${formatDuration(item.positionMs)} / ${formatDuration(item.durationMs)}"
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
