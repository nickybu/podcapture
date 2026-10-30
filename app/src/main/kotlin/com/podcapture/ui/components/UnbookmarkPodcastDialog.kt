package com.podcapture.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.repository.PodcastRepository.UnbookmarkImpact
import org.koin.compose.koinInject

/**
 * Confirmation shown before a podcast bookmark is removed. Unbookmarking deletes the
 * podcast's downloaded episodes and playback history, and leaves its captures unreachable
 * from Home, so the dialog states those counts before the user commits.
 */
@Composable
fun UnbookmarkPodcastDialog(
    podcastId: Long,
    podcastTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    podcastRepository: PodcastRepository = koinInject()
) {
    // null while loading; a failed lookup falls back to the generic message.
    val impact by produceState<Result<UnbookmarkImpact>?>(initialValue = null, podcastId) {
        value = runCatching { podcastRepository.getUnbookmarkImpact(podcastId) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Bookmark") },
        text = { Text(unbookmarkMessage(podcastTitle, impact)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = impact != null) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun unbookmarkMessage(podcastTitle: String, impact: Result<UnbookmarkImpact>?): String {
    val header = "Remove \"$podcastTitle\" from your bookmarks? Its playback history will be cleared."
    val counts = impact?.getOrNull()
        ?: return if (impact == null) "$header\n\nChecking downloads and captures…" else header

    return buildString {
        append(header)
        if (counts.downloadedEpisodes > 0) {
            append("\n\n")
            append(plural(counts.downloadedEpisodes, "downloaded episode", "downloaded episodes"))
            append(" will be deleted.")
        }
        if (counts.captures > 0) {
            append("\n\n")
            append(plural(counts.captures, "capture", "captures"))
            append(" will no longer be reachable from Home. Captures are not deleted: bookmark the podcast again and open the episode to see them.")
        }
    }
}

private fun plural(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"
