package com.podcapture.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcapture.data.model.CachedEpisode
import com.podcapture.data.model.EpisodeDownloadState
import com.podcapture.data.model.Podcast
import com.podcapture.data.model.Tag
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    podcastId: Long,
    onNavigateBack: () -> Unit,
    onEpisodeSelected: (CachedEpisode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastDetailViewModel = koinViewModel { parametersOf(podcastId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Refresh download states when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshDownloadStates()
    }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onErrorDismissed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.podcast?.title ?: "Podcast",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Tag button (always visible - auto-bookmarks if needed)
                    IconButton(onClick = viewModel::onOpenTagDialog) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = "Tags",
                            tint = if (uiState.tags.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Bookmark button
                    IconButton(
                        onClick = viewModel::onToggleBookmark,
                        enabled = !uiState.isBookmarkLoading
                    ) {
                        if (uiState.isBookmarkLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.isBookmarked)
                                    Icons.Filled.Bookmark
                                else Icons.Default.BookmarkBorder,
                                contentDescription = if (uiState.isBookmarked) "Remove bookmark" else "Bookmark",
                                tint = if (uiState.isBookmarked)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.podcast != null -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Podcast header
                        item {
                            PodcastHeader(
                                podcast = uiState.podcast!!,
                                tags = uiState.tags
                            )
                        }

                        // Episode search bar
                        item {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                placeholder = { Text("Search episodes...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = viewModel::onClearSearch) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Episodes header with count
                        item {
                            val countText = if (uiState.searchQuery.isNotEmpty()) {
                                "${uiState.displayedEpisodes.size} of ${uiState.episodes.size}"
                            } else {
                                "${uiState.episodes.size}"
                            }
                            Text(
                                text = "Episodes ($countText)",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // Episode list (filtered if search is active)
                        items(uiState.displayedEpisodes, key = { it.episode.id }) { episodeItem ->
                            EpisodeCard(
                                episodeItem = episodeItem,
                                podcastArtworkUrl = uiState.podcast?.artworkUrl,
                                onClick = { onEpisodeSelected(episodeItem.episode) },
                                onDownload = { viewModel.onDownloadEpisode(episodeItem.episode.id) }
                            )
                        }

                        // Show message if no results
                        if (uiState.searchQuery.isNotEmpty() && uiState.displayedEpisodes.isEmpty()) {
                            item {
                                Text(
                                    text = "No episodes match \"${uiState.searchQuery}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        }
                    }
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Failed to load podcast",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }

    // Tag editing dialog
    if (uiState.showTagDialog) {
        TagEditDialog(
            allTags = uiState.allTags,
            selectedTagIds = uiState.tags.map { it.id },
            newTagName = uiState.newTagName,
            onNewTagNameChanged = viewModel::onNewTagNameChanged,
            onCreateTag = viewModel::onCreateTag,
            onToggleTag = viewModel::onToggleTag,
            onDismiss = viewModel::onCloseTagDialog
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodcastHeader(
    podcast: Podcast,
    tags: List<Tag>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row {
                // Podcast artwork
                if (podcast.artworkUrl.isNotEmpty()) {
                    AsyncImage(
                        model = podcast.artworkUrl,
                        contentDescription = "${podcast.title} artwork",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = podcast.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${podcast.episodeCount} episodes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Tags
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
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

            if (podcast.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = podcast.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episodeItem: EpisodeUiItem,
    podcastArtworkUrl: String?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = episodeItem.episode

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode/podcast artwork
                val imageUrl = episode.imageUrl.ifEmpty { podcastArtworkUrl ?: "" }
                if (imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (episode.duration > 0) {
                            Text(
                                text = formatDuration(episode.duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (episode.publishedDate > 0) {
                            Text(
                                text = formatDate(episode.publishedDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Download/Downloaded state button
                when (episodeItem.downloadState) {
                    EpisodeDownloadState.NotDownloaded -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    EpisodeDownloadState.Downloading -> {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { episodeItem.downloadProgress / 100f },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    EpisodeDownloadState.Downloaded -> {
                        // Show downloaded icon with play indicator
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Downloaded",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            // Small play indicator
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.BottomEnd),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    EpisodeDownloadState.Error -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Retry download",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Download progress bar with size info
            if (episodeItem.downloadState == EpisodeDownloadState.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    LinearProgressIndicator(
                        progress = { episodeItem.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatBytes(episodeItem.downloadedBytes)} / ${formatBytes(episodeItem.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
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
                                } else null
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

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

private fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000) // API returns seconds
    val format = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return format.format(date)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceIn(0, units.lastIndex)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
}

private fun stripHtml(html: String): String {
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .trim()
}
