package com.podcapture.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                        text = "Capture Window",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Amount of audio before and after the capture point to transcribe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NumberPicker(
                        value = uiState.captureWindowSeconds,
                        onValueChange = { viewModel.setCaptureWindowSeconds(it) },
                        minValue = 10,
                        maxValue = 60,
                        step = 5,
                        suffix = "s",
                        displayTransform = { "±$it" }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Total capture: ${uiState.captureWindowSeconds * 2} seconds",
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
