package com.podcapture.ui.viewer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.podcapture.R
import com.podcapture.data.model.Capture
import com.podcapture.data.model.Tag
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    audioFileId: String,
    scrollToCaptureId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToTimestamp: (audioFileId: String, timestampMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = koinViewModel { parametersOf(audioFileId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Scroll to the specified capture when it becomes available
    LaunchedEffect(scrollToCaptureId, uiState.captures) {
        if (scrollToCaptureId != null && uiState.captures.isNotEmpty()) {
            val captureIndex = uiState.captures.indexOfFirst { it.id == scrollToCaptureId }
            if (captureIndex >= 0) {
                // Add 1 to account for the header item
                listState.animateScrollToItem(captureIndex + 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Captures",
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
                    // Obsidian export button - always visible if file is loaded
                    if (uiState.audioFile != null) {
                        IconButton(
                            onClick = { viewModel.onOpenObsidianDialog() }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_obsidian),
                                contentDescription = "Export to Obsidian",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                        }
                    }
                    // Share button - only when markdown file exists
                    if (uiState.markdownFilePath != null) {
                        IconButton(
                            onClick = {
                                val file = File(uiState.markdownFilePath!!)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share captures"))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No captures yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                // Header with file info
                item {
                    uiState.audioFile?.let { audioFile ->
                        Text(
                            text = audioFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${uiState.captures.size} capture(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Capture cards
                itemsIndexed(uiState.capturesWithTags, key = { _, captureWithTags -> captureWithTags.capture.id }) { index, captureWithTags ->
                    CaptureCard(
                        capture = captureWithTags.capture,
                        tags = captureWithTags.tags,
                        index = index + 1,
                        onClick = {
                            onNavigateToTimestamp(audioFileId, captureWithTags.capture.windowStartMs)
                        },
                        onEditNotes = {
                            viewModel.onEditNotes(captureWithTags.capture.id, captureWithTags.capture.notes)
                        },
                        onTagClick = {
                            viewModel.onOpenTagDialog(captureWithTags.capture.id)
                        }
                    )
                }
            }
        }

        // Notes editing dialog
        if (uiState.editingCaptureId != null) {
            EditNotesDialog(
                notes = uiState.editingNotes,
                onNotesChange = viewModel::onNotesChanged,
                onSave = viewModel::onSaveNotes,
                onDismiss = viewModel::onDismissEditNotes
            )
        }

        // Tag editing dialog
        if (uiState.showTagDialog) {
            val editingCapture = uiState.capturesWithTags.find { it.capture.id == uiState.editingTagsCaptureId }
            TagEditDialog(
                allTags = uiState.allTags,
                selectedTagIds = editingCapture?.tags?.map { it.id } ?: emptyList(),
                newTagName = uiState.newTagName,
                onNewTagNameChanged = viewModel::onNewTagNameChanged,
                onCreateTag = viewModel::onCreateTag,
                onToggleTag = viewModel::onToggleTagForCapture,
                onDeleteTag = viewModel::onDeleteTag,
                onDismiss = viewModel::onCloseTagDialog
            )
        }

        // Obsidian export dialog
        if (uiState.showObsidianDialog) {
            ObsidianExportDialog(
                title = uiState.obsidianTitle,
                tags = uiState.obsidianTags,
                tagsInput = uiState.obsidianTagsInput,
                markdownPreview = uiState.obsidianPreview,
                vaultPathConfigured = uiState.obsidianVaultUri.isNotBlank(),
                defaultTags = uiState.obsidianDefaultTags,
                onTitleChange = viewModel::onObsidianTitleChanged,
                onTagsInputChange = viewModel::onObsidianTagsInputChanged,
                onRemoveTag = viewModel::onRemoveObsidianTag,
                onExportToVault = { viewModel.exportToObsidianVault() },
                onShare = {
                    // Share the Obsidian-formatted content
                    val content = viewModel.getObsidianContent()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share to Obsidian"))
                    viewModel.onCloseObsidianDialog()
                },
                onDismiss = viewModel::onCloseObsidianDialog
            )
        }

        // Handle export result
        LaunchedEffect(uiState.obsidianExportResult) {
            when (val result = uiState.obsidianExportResult) {
                is ObsidianExportResult.Success -> {
                    Toast.makeText(context, "Exported to: ${result.filePath}", Toast.LENGTH_LONG).show()
                    viewModel.clearExportResult()
                }
                is ObsidianExportResult.Error -> {
                    Toast.makeText(context, "Export failed: ${result.message}", Toast.LENGTH_LONG).show()
                    viewModel.clearExportResult()
                }
                null -> {}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureCard(
    capture: Capture,
    tags: List<Tag>,
    index: Int,
    onClick: () -> Unit,
    onEditNotes: () -> Unit,
    onTagClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Capture $index",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tag button
                    IconButton(
                        onClick = { onTagClick() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = "Edit tags",
                            modifier = Modifier.size(18.dp),
                            tint = if (tags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Edit notes button
                    IconButton(
                        onClick = { onEditNotes() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (capture.notes.isNullOrBlank()) Icons.AutoMirrored.Filled.NoteAdd else Icons.Default.Edit,
                            contentDescription = if (capture.notes.isNullOrBlank()) "Add notes" else "Edit notes",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDuration(capture.timestampMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Tags row (if present)
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Window info
            Text(
                text = "Window: ${formatDuration(capture.windowStartMs)} → ${formatDuration(capture.windowEndMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Captured time
            Text(
                text = formatDateTime(capture.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Notes section (if present)
            if (!capture.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = capture.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transcription
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = capture.transcription,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (capture.transcription.startsWith("[")) FontStyle.Italic else FontStyle.Normal,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun EditNotesDialog(
    notes: String,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Notes") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                placeholder = { Text("Add your notes about this capture...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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

private fun formatDuration(ms: Long): String {
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

private fun formatDateTime(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}
