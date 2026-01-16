package com.podcapture.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.podcapture.audio.PlayerState
import com.podcapture.audio.PlaybackState

@Composable
fun MiniPlayer(
    audioFileName: String?,
    playbackState: PlaybackState,
    skipIntervalSeconds: Int,
    isCapturing: Boolean,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onCapture: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (audioFileName == null) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp) // Bottom margin for gesture bar
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Progress bar at top
            if (playbackState.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = audioFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Rewind
                    IconButton(
                        onClick = onRewind,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.FastRewind,
                            contentDescription = "Rewind $skipIntervalSeconds seconds",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Play/Pause
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        if (playbackState.playerState == PlayerState.LOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                if (playbackState.playerState == PlayerState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.playerState == PlayerState.PLAYING) "Pause" else "Play",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Fast Forward
                    IconButton(
                        onClick = onFastForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "Forward $skipIntervalSeconds seconds",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Capture
                    IconButton(
                        onClick = onCapture,
                        enabled = !isCapturing,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = "Capture",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                        }
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
        String.format("%02d:%02d", minutes, seconds)
    }
}
