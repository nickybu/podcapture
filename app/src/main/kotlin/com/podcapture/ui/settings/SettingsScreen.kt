package com.podcapture.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.podcapture.ui.theme.isValidHexColor
import com.podcapture.ui.theme.parseHexColor
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so we can access this folder later
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setObsidianVaultUri(it.toString())
        }
    }

    // OPML import launcher
    val opmlImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importOpml(context, it)
        }
    }

    // OPML export launcher
    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportOpml(context, it)
        }
    }

    // Import progress dialog
    if (uiState.isImporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Importing Podcasts") },
            text = {
                Column {
                    uiState.importProgress?.let { progress ->
                        Text(
                            text = "Processing ${progress.current} of ${progress.total}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progress.currentPodcast,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } ?: run {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Reading OPML file...")
                        }
                    }
                }
            },
            confirmButton = { }
        )
    }

    // Import result dialog
    uiState.importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { Text("Import Complete") },
            text = {
                Column {
                    Text("Imported: ${result.imported} podcasts")
                    if (result.skipped > 0) {
                        Text("Skipped: ${result.skipped} (already bookmarked)")
                    }
                    if (result.failed > 0) {
                        Text("Failed: ${result.failed}")
                        if (result.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.errors.take(3).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportResult() }) {
                    Text("OK")
                }
            }
        )
    }

    // Export result dialog
    uiState.exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { Text(if (result is ExportResult.Success) "Export Complete" else "Export Failed") },
            text = {
                when (result) {
                    is ExportResult.Success -> Text("Exported ${result.count} podcasts to OPML file")
                    is ExportResult.Error -> Text(result.message)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Playback section
            Text(
                text = "Playback",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Skip Interval",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Amount to rewind or fast-forward when pressing skip buttons",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NumberPicker(
                        value = uiState.skipIntervalSeconds,
                        onValueChange = { viewModel.setSkipIntervalSeconds(it) },
                        minValue = 5,
                        maxValue = 60,
                        step = 5,
                        suffix = "s"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Capture section
            Text(
                text = "Capture",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Capture Step",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Amount to adjust capture window when tapping +/- buttons",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NumberPicker(
                        value = uiState.captureWindowStep,
                        onValueChange = { viewModel.setCaptureWindowStep(it) },
                        minValue = 5,
                        maxValue = 15,
                        step = 5,
                        suffix = "s"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Capture window is adjusted directly on the player screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Obsidian section
            Text(
                text = "Obsidian Export",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Vault Folder",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Select your Obsidian vault folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.obsidianVaultUri.isNotBlank())
                                uiState.obsidianVaultDisplayName
                            else
                                "Select Vault Folder"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Default Tags",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Tags added to frontmatter by default (comma-separated)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.obsidianDefaultTags,
                        onValueChange = { viewModel.setObsidianDefaultTags(it) },
                        placeholder = { Text("inbox/, resources/references/podcasts") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Podcast Index API section
            Text(
                text = "Podcast Index API",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "API Usage",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Podcast search and episode data is provided by the Podcast Index API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Calls today",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${uiState.apiCallCount}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.podcastindex.org"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Developer Dashboard")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Visit the Podcast Index dashboard to view detailed API usage and manage your account",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Podcast Subscriptions section
            Text(
                text = "Podcast Subscriptions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Import & Export",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Transfer your podcast subscriptions using standard OPML format",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { opmlImportLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import")
                        }

                        Button(
                            onClick = { opmlExportLauncher.launch("podcapture_subscriptions.opml") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "OPML files can be imported from or exported to other podcast apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme section
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Custom Colors",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Customize the app's color scheme with hex colors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ColorInputField(
                        label = "Background",
                        value = uiState.themeBackgroundColor,
                        onValueChange = { viewModel.setThemeBackgroundColor(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ColorInputField(
                        label = "Accent 1 (Secondary)",
                        value = uiState.themeAccent1Color,
                        onValueChange = { viewModel.setThemeAccent1Color(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ColorInputField(
                        label = "Accent 2 (Primary)",
                        value = uiState.themeAccent2Color,
                        onValueChange = { viewModel.setThemeAccent2Color(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { viewModel.resetThemeColors() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Default Colors")
                    }
                }
            }

            // Bottom spacing to ensure content is scrollable
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int,
    step: Int,
    suffix: String = "",
    displayTransform: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(minValue)) },
            enabled = value > minValue,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Decrease",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "${displayTransform(value)}$suffix",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.width(100.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = { onValueChange((value + step).coerceAtMost(maxValue)) },
            enabled = value < maxValue,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Increase",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ColorInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isValid = isValidHexColor(value)
    val parsedColor = parseHexColor(value)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Ensure the value starts with # and limit to 7 characters
                val cleanValue = if (newValue.startsWith("#")) {
                    "#" + newValue.removePrefix("#").take(6).uppercase()
                } else {
                    "#" + newValue.take(6).uppercase()
                }
                onValueChange(cleanValue)
            },
            label = { Text(label) },
            singleLine = true,
            isError = !isValid && value.length > 1,
            colors = if (!isValid && value.length > 1) {
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.error,
                    unfocusedBorderColor = MaterialTheme.colorScheme.error
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(parsedColor ?: Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (isValid) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(8.dp)
                )
        )
    }
}
