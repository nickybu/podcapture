package com.podcapture.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.podcapture.data.model.Tag
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (audioFileId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Handle navigation
    LaunchedEffect(uiState.navigateToPlayer) {
        uiState.navigateToPlayer?.let { audioFileId ->
            onNavigateToPlayer(audioFileId)
            viewModel.onNavigationHandled()
        }
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
                title = { Text("PodCapture") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
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
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Open audio file")
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
            if (uiState.isLoading && uiState.audioFiles.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.audioFiles.isEmpty() && uiState.selectedTagId == null) {
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

                    item {
                        Text(
                            text = if (uiState.selectedTagId != null) "Filtered Files" else "Recent Files",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (uiState.audioFiles.isEmpty()) {
                        item {
                            Text(
                                text = "No files match the selected tag",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(uiState.audioFiles, key = { it.audioFile.id }) { item ->
                        AudioFileCard(
                            item = item,
                            onClick = { viewModel.onFileClicked(item.audioFile.id) },
                            onTagClick = { viewModel.onOpenTagDialog(item.audioFile.id) }
                        )
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
