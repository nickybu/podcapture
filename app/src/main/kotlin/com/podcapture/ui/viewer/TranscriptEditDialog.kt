package com.podcapture.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.podcapture.ui.components.FormattedTranscriptText
import com.podcapture.util.FormattingType
import com.podcapture.util.TranscriptFormattingParser

/**
 * Full-screen dialog for formatting transcript text.
 * Users can select text and apply highlight or bold formatting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptEditDialog(
    formattedText: String,
    originalTranscription: String,
    validationError: Boolean,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember(formattedText) {
        mutableStateOf(TextFieldValue(formattedText))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Format Transcript") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = onSave) {
                            Text("Save")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .imePadding()
            ) {
                // Instructions
                Text(
                    text = "Select text and use the toolbar to format. Only highlighting and bolding are allowed - the original text cannot be changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Formatting toolbar
                FormattingToolbar(
                    onHighlight = {
                        val selection = textFieldValue.selection
                        if (!selection.collapsed) {
                            val newText = TranscriptFormattingParser.applyFormatting(
                                textFieldValue.text,
                                selection.min,
                                selection.max,
                                FormattingType.HIGHLIGHT
                            )
                            textFieldValue = textFieldValue.copy(
                                text = newText,
                                selection = TextRange(selection.min)
                            )
                            onTextChanged(newText)
                        }
                    },
                    onBold = {
                        val selection = textFieldValue.selection
                        if (!selection.collapsed) {
                            val newText = TranscriptFormattingParser.applyFormatting(
                                textFieldValue.text,
                                selection.min,
                                selection.max,
                                FormattingType.BOLD
                            )
                            textFieldValue = textFieldValue.copy(
                                text = newText,
                                selection = TextRange(selection.min)
                            )
                            onTextChanged(newText)
                        }
                    },
                    onClear = {
                        val selection = textFieldValue.selection
                        if (!selection.collapsed) {
                            val newText = TranscriptFormattingParser.clearFormatting(
                                textFieldValue.text,
                                selection.min,
                                selection.max
                            )
                            textFieldValue = textFieldValue.copy(
                                text = newText,
                                selection = TextRange(selection.min)
                            )
                            onTextChanged(newText)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Validation error message
                if (validationError) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Text content was modified. Saving will discard changes and revert to the original transcript.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Text editor
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onTextChanged(newValue.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = { Text("Transcript with formatting") },
                    supportingText = {
                        Text("Use == for highlight and ** for bold")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Preview section
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FormattedTranscriptText(
                            text = textFieldValue.text,
                            isErrorMessage = originalTranscription.startsWith("[")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattingToolbar(
    onHighlight: () -> Unit,
    onBold: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onHighlight,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Highlight,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Highlight")
        }

        OutlinedButton(
            onClick = onBold,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.FormatBold,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Bold")
        }

        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.FormatClear,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear")
        }
    }
}
