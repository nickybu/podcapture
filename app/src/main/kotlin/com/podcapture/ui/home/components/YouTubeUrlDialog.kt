package com.podcapture.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.podcapture.youtube.YouTubeUrlValidator

/**
 * Dialog for entering a YouTube URL to download audio from.
 */
@Composable
fun YouTubeUrlDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Request focus on the text field when the dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Validate URL as user types
    LaunchedEffect(url) {
        validationError = if (url.isBlank()) {
            null // Don't show error for empty field
        } else {
            YouTubeUrlValidator.getValidationError(url)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from YouTube") },
        text = {
            Column {
                Text(
                    text = "Paste a YouTube video URL to download its audio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube URL") },
                    placeholder = { Text("https://youtube.com/watch?v=...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null
                        )
                    },
                    isError = validationError != null,
                    supportingText = if (validationError != null) {
                        { Text(validationError!!, color = MaterialTheme.colorScheme.error) }
                    } else if (url.isNotBlank() && YouTubeUrlValidator.isValidUrl(url)) {
                        { Text("Valid YouTube URL", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (YouTubeUrlValidator.isValidUrl(url)) {
                                onImport(url)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Supported: youtube.com, youtu.be, YouTube Shorts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(url) },
                enabled = YouTubeUrlValidator.isValidUrl(url)
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
