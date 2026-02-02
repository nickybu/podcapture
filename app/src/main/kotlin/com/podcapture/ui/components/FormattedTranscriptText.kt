package com.podcapture.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import com.podcapture.ui.theme.HighlightYellow
import com.podcapture.util.TranscriptFormattingParser

/**
 * Renders transcript text with visual styling for highlights (==) and bold (**) markers.
 */
@Composable
fun FormattedTranscriptText(
    text: String,
    modifier: Modifier = Modifier,
    isErrorMessage: Boolean = false
) {
    val annotatedString = remember(text) {
        TranscriptFormattingParser.parseToAnnotatedString(
            text = text,
            highlightColor = HighlightYellow,
            boldColor = null
        )
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = if (isErrorMessage) FontStyle.Italic else FontStyle.Normal,
        modifier = modifier
    )
}
