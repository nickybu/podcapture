package com.podcapture.ui.viewer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.podcapture.ui.settings.persistVaultFolderPermission

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObsidianExportDialog(
    title: String,
    tags: List<String>,
    tagsInput: String,
    markdownPreview: String,
    vaultPathConfigured: Boolean,
    defaultTags: String,
    isExporting: Boolean,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onTagsInputChange: (String) -> Unit,
    onRemoveTag: (Int) -> Unit,
    onExportToVault: () -> Unit,
    onVaultFolderPicked: (uri: String) -> Unit,
    onShareAsText: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // With no vault set, the export button picks one here instead of sending the user to
    // Settings; the pick is saved as the Settings vault and the export continues.
    val vaultPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            persistVaultFolderPermission(context, it)
            onVaultFolderPicked(it.toString())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export to Obsidian") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    if (vaultPathConfigured) {
                        // The saved folder may have moved or lost its permission.
                        TextButton(onClick = { vaultPicker.launch(null) }) {
                            Text("Choose a Different Vault Folder")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Title field
                Text(
                    text = "Title",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Note title") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tags field
                Text(
                    text = "Additional Tags (space-separated)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = onTagsInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("tag1 tag2 tag3") }
                )

                // Show parsed tags as chips
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEachIndexed { index, tag ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveTag(index) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Default tags info
                Text(
                    text = "Default tags: $defaultTags",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Preview section
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = markdownPreview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Shares this note (with the title and tags above) as plain text. Unlike the
                // toolbar Share, which sends the capture file for local audio files.
                TextButton(onClick = onShareAsText, enabled = !isExporting) {
                    Text("Share as Text")
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else if (vaultPathConfigured) {
                    TextButton(onClick = onExportToVault) {
                        Text("Export to Vault")
                    }
                } else {
                    TextButton(onClick = { vaultPicker.launch(null) }) {
                        Text("Choose Vault & Export")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
