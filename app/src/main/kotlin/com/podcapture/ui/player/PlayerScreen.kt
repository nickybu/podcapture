package com.podcapture.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.podcapture.audio.PlayerState
import com.podcapture.data.model.Capture
import com.podcapture.ui.components.WaveformTimeline
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    audioFileId: String,
    seekToMs: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (audioFileId: String, captureId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel { parametersOf(audioFileId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle seek to timestamp from navigation
    LaunchedEffect(seekToMs) {
        seekToMs?.let { position ->
            viewModel.onSeekTo(position)
        }
    }

    // Save position when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePlaybackPosition()
        }
    }

    // Handle capture success
    LaunchedEffect(uiState.captureSuccess) {
        uiState.captureSuccess?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onCaptureSuccessDismissed()
        }
    }

    // Handle errors
    LaunchedEffect(uiState.captureError) {
        uiState.captureError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onCaptureErrorDismissed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.audioFile?.name ?: "Loading...",
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
                    // Bookmark button
                    IconButton(onClick = { viewModel.onToggleBookmark() }) {
                        Icon(
                            imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (uiState.isBookmarked) "Remove bookmark" else "Add bookmark",
                            tint = if (uiState.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Captures button
                    if (uiState.captures.isNotEmpty()) {
                        TextButton(onClick = { onNavigateToViewer(audioFileId, null) }) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${uiState.captures.size}")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Time display
                    TimeDisplay(
                        currentPositionMs = uiState.playbackState.currentPositionMs,
                        durationMs = uiState.playbackState.durationMs
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Waveform timeline with capture markers
                    WaveformTimeline(
                        currentPositionMs = uiState.playbackState.currentPositionMs,
                        durationMs = uiState.playbackState.durationMs,
                        captures = uiState.captures,
                        audioFileId = audioFileId,
                        onSeek = { viewModel.onSeekTo(it) }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Playback controls
                    PlaybackControls(
                        isPlaying = uiState.playbackState.playerState == PlayerState.PLAYING,
                        isLoading = uiState.playbackState.playerState == PlayerState.LOADING,
                        skipIntervalSeconds = uiState.skipIntervalSeconds,
                        onPlayPause = { viewModel.onPlayPause() },
                        onRewind = { viewModel.onRewind() },
                        onFastForward = { viewModel.onFastForward() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed control with +/- buttons
                    SpeedControl(
                        currentSpeed = uiState.playbackState.playbackSpeed,
                        onSpeedChange = { viewModel.onSpeedChange(it) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Capture button
                    CaptureButton(
                        windowSeconds = uiState.captureWindowSeconds,
                        isCapturing = uiState.isCapturing,
                        captureProgress = uiState.captureProgress,
                        onCapture = { viewModel.onCapture() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Capture popup
            AnimatedVisibility(
                visible = uiState.activeCapture != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                uiState.activeCapture?.let { capture ->
                    CapturePopup(
                        capture = capture,
                        captureIndex = uiState.activeCaptureIndex,
                        onView = {
                            viewModel.dismissCapturePopup()
                            onNavigateToViewer(audioFileId, capture.id)
                        },
                        onDismiss = { viewModel.dismissCapturePopup() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeDisplay(
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDuration(currentPositionMs),
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = " / ",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    skipIntervalSeconds: Int,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rewind
        IconButton(
            onClick = onRewind,
            modifier = Modifier.size(64.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FastRewind,
                    contentDescription = "Rewind $skipIntervalSeconds seconds",
                    modifier = Modifier.size(32.dp)
                )
                Text("${skipIntervalSeconds}s", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Play/Pause
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Fast Forward
        IconButton(
            onClick = onFastForward,
            modifier = Modifier.size(64.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = "Forward $skipIntervalSeconds seconds",
                    modifier = Modifier.size(32.dp)
                )
                Text("${skipIntervalSeconds}s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CaptureButton(
    windowSeconds: Int,
    isCapturing: Boolean,
    captureProgress: String?,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onCapture,
            enabled = !isCapturing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onTertiary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(captureProgress ?: "Capturing...")
            } else {
                Text(
                    text = "CAPTURE",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Window: ±${windowSeconds}s (change in Settings)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CapturePopup(
    capture: Capture,
    captureIndex: Int,
    onView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Capture $captureIndex",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                text = capture.transcription.take(100) + if (capture.transcription.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onView,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Captures")
            }
        }
    }
}

@Composable
private fun SpeedControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                val newSpeed = (currentSpeed - 0.05f).coerceAtLeast(0.5f)
                onSpeedChange((newSpeed * 20).toInt() / 20f) // Round to 0.05
            },
            enabled = currentSpeed > 0.5f
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Decrease speed",
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = String.format("%.2fx", currentSpeed),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = {
                val newSpeed = (currentSpeed + 0.05f).coerceAtMost(2.0f)
                onSpeedChange((newSpeed * 20).toInt() / 20f) // Round to 0.05
            },
            enabled = currentSpeed < 2.0f
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Increase speed",
                modifier = Modifier.size(28.dp)
            )
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
