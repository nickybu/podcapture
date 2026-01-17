package com.podcapture.ui.player

import android.text.style.URLSpan
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Remove
import com.podcapture.data.model.EpisodeDownloadState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.podcapture.audio.PlayerState
import com.podcapture.data.model.Capture
import com.podcapture.ui.components.WaveformTimeline
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodePlayerScreen(
    episodeId: Long,
    podcastId: Long,
    seekToMs: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (audioFileId: String, captureId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodePlayerViewModel = koinViewModel { parametersOf(episodeId, podcastId) }
) {
    // Handle seek request when navigating from captures
    LaunchedEffect(seekToMs) {
        seekToMs?.let { position ->
            viewModel.onSeekTo(position)
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val virtualAudioFileId = "episode_$episodeId"

    // Track active capture for popup
    var activeCapture by remember { mutableStateOf<Capture?>(null) }
    var activeCaptureIndex by remember { mutableStateOf(0) }
    var userDismissedCapture by remember { mutableStateOf<String?>(null) }

    // Check for active capture based on playback position
    // Popup stays visible until playback moves past the capture's end time or user dismisses
    LaunchedEffect(uiState.playbackState.currentPositionMs, uiState.captures) {
        val currentPos = uiState.playbackState.currentPositionMs
        val capture = uiState.captures.find { capture ->
            currentPos >= capture.windowStartMs && currentPos <= capture.windowEndMs
        }

        if (capture != null) {
            // Only show popup if user hasn't dismissed this specific capture
            if (capture.id != userDismissedCapture) {
                activeCapture = capture
                activeCaptureIndex = uiState.captures.indexOf(capture) + 1
            }
        } else {
            // Hide popup when leaving capture window (past end time)
            activeCapture = null
            // Reset dismissed state when leaving a capture window
            userDismissedCapture = null
        }
    }

    // Save position when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePlaybackPosition()
        }
    }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onErrorDismissed()
        }
    }

    // Clear capture success silently (no snackbar notification)
    LaunchedEffect(uiState.captureSuccess) {
        uiState.captureSuccess?.let {
            viewModel.onCaptureSuccessDismissed()
        }
    }

    // Handle capture errors
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
                    Column {
                        Text(
                            text = uiState.episode?.title ?: "Loading...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        uiState.podcast?.title?.let { podcastTitle ->
                            Text(
                                text = podcastTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Captures count button
                    if (uiState.captures.isNotEmpty()) {
                        TextButton(onClick = { onNavigateToViewer(virtualAudioFileId, null) }) {
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Episode artwork
                    val imageUrl = uiState.episode?.imageUrl?.takeIf { it.isNotEmpty() }
                        ?: uiState.podcast?.artworkUrl ?: ""

                    if (imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Episode artwork",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Podcasts,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Episode title
                    Text(
                        text = uiState.episode?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Time display
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(uiState.playbackState.currentPositionMs),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            text = " / ",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(uiState.playbackState.durationMs),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Waveform timeline with capture markers
                    WaveformTimeline(
                        currentPositionMs = uiState.playbackState.currentPositionMs,
                        durationMs = uiState.playbackState.durationMs,
                        captures = uiState.captures,
                        audioFileId = "episode_$episodeId",
                        onSeek = { viewModel.onSeekTo(it) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Playback controls
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind
                        IconButton(
                            onClick = { viewModel.onRewind() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FastRewind,
                                    contentDescription = "Rewind",
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "${uiState.skipIntervalSeconds}s",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Play/Pause
                        IconButton(
                            onClick = { viewModel.onPlayPause() },
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            if (uiState.playbackState.playerState == PlayerState.LOADING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    if (uiState.playbackState.playerState == PlayerState.PLAYING)
                                        Icons.Default.Pause
                                    else Icons.Default.PlayArrow,
                                    contentDescription = if (uiState.playbackState.playerState == PlayerState.PLAYING)
                                        "Pause" else "Play",
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Fast Forward
                        IconButton(
                            onClick = { viewModel.onFastForward() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FastForward,
                                    contentDescription = "Fast Forward",
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "${uiState.skipIntervalSeconds}s",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                val newSpeed = (uiState.playbackState.playbackSpeed - 0.05f)
                                    .coerceAtLeast(0.5f)
                                viewModel.onSpeedChange((newSpeed * 20).toInt() / 20f)
                            },
                            enabled = uiState.playbackState.playbackSpeed > 0.5f
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Decrease speed",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = String.format("%.2fx", uiState.playbackState.playbackSpeed),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            onClick = {
                                val newSpeed = (uiState.playbackState.playbackSpeed + 0.05f)
                                    .coerceAtMost(2.0f)
                                viewModel.onSpeedChange((newSpeed * 20).toInt() / 20f)
                            },
                            enabled = uiState.playbackState.playbackSpeed < 2.0f
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Increase speed",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Download button when not downloaded, or Capture button when downloaded
                    if (uiState.downloadState == EpisodeDownloadState.NotDownloaded ||
                        uiState.downloadState == EpisodeDownloadState.Downloading ||
                        uiState.downloadState == EpisodeDownloadState.Error) {
                        EpisodeDownloadButton(
                            downloadState = uiState.downloadState,
                            downloadProgress = uiState.downloadProgress,
                            onDownload = { viewModel.onDownload() }
                        )
                    } else {
                        // Capture button (only when downloaded)
                        EpisodeCaptureButton(
                            windowSeconds = uiState.captureWindowSeconds,
                            isCapturing = uiState.isCapturing,
                            captureProgress = uiState.captureProgress,
                            isDownloaded = uiState.localFilePath != null,
                            onCapture = { viewModel.onCapture() }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Show notes section (podcast-specific)
                    ShowNotesSection(
                        description = uiState.episode?.description ?: "",
                        episodeLink = uiState.episode?.link,
                        isExpanded = uiState.showNotes,
                        onToggle = { viewModel.toggleShowNotes() },
                        onTimestampClick = { timestampMs ->
                            viewModel.onSeekTo(timestampMs)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Capture popup - stays visible until playback moves past capture end time or user dismisses
            AnimatedVisibility(
                visible = activeCapture != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                activeCapture?.let { capture ->
                    EpisodeCapturePopup(
                        capture = capture,
                        captureIndex = activeCaptureIndex,
                        onView = {
                            onNavigateToViewer(virtualAudioFileId, capture.id)
                        },
                        onDismiss = {
                            userDismissedCapture = capture.id
                            activeCapture = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadButton(
    downloadState: EpisodeDownloadState,
    downloadProgress: Int,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onDownload,
            enabled = downloadState == EpisodeDownloadState.NotDownloaded ||
                    downloadState == EpisodeDownloadState.Error,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            when (downloadState) {
                EpisodeDownloadState.NotDownloaded -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DOWNLOAD TO PLAY",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                EpisodeDownloadState.Downloading -> {
                    CircularProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloading... $downloadProgress%",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                EpisodeDownloadState.Error -> {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETRY DOWNLOAD",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                EpisodeDownloadState.Downloaded -> {
                    // Shouldn't reach here, but handle gracefully
                    Text("Downloaded")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Download required for playback and capture",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShowNotesSection(
    description: String,
    episodeLink: String?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val timestampColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val showNotesScrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            TextButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Notes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(showNotesScrollState)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (description.isNotEmpty()) {
                        // Parse HTML to Spanned, preserving links
                        val spanned = HtmlCompat.fromHtml(
                            description,
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )

                        // Build AnnotatedString with clickable links and timestamps
                        val annotatedString = buildAnnotatedString {
                            val text = spanned.toString()
                                .replace(Regex("\n{3,}"), "\n\n")  // Max 2 line breaks
                                .trim()

                            append(text)

                            // Track URL ranges to avoid overlapping with timestamps
                            val urlRanges = mutableListOf<IntRange>()

                            // Find and annotate URLs from the HTML
                            spanned.getSpans(0, spanned.length, URLSpan::class.java).forEach { urlSpan ->
                                val start = spanned.getSpanStart(urlSpan)
                                val end = spanned.getSpanEnd(urlSpan)
                                // Adjust for trimmed content
                                val trimOffset = spanned.toString().indexOf(text.take(20).ifEmpty { text })
                                val adjustedStart = (start - trimOffset).coerceAtLeast(0)
                                val adjustedEnd = (end - trimOffset).coerceAtMost(text.length)

                                if (adjustedStart >= 0 && adjustedEnd <= text.length && adjustedStart < adjustedEnd) {
                                    urlRanges.add(adjustedStart until adjustedEnd)
                                    addStyle(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        start = adjustedStart,
                                        end = adjustedEnd
                                    )
                                    addStringAnnotation(
                                        tag = "URL",
                                        annotation = urlSpan.url,
                                        start = adjustedStart,
                                        end = adjustedEnd
                                    )
                                }
                            }

                            // Find and annotate timestamps (formats: 1:23:45, 12:34, 0:45)
                            val timestampRegex = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)""")
                            timestampRegex.findAll(text).forEach { match ->
                                val hours: Int
                                val minutes: Int
                                val seconds: Int

                                if (match.groupValues[3].isNotEmpty()) {
                                    // Format: H:MM:SS or HH:MM:SS
                                    hours = match.groupValues[1].toIntOrNull() ?: 0
                                    minutes = match.groupValues[2].toIntOrNull() ?: 0
                                    seconds = match.groupValues[3].toIntOrNull() ?: 0
                                } else {
                                    // Format: M:SS or MM:SS
                                    hours = 0
                                    minutes = match.groupValues[1].toIntOrNull() ?: 0
                                    seconds = match.groupValues[2].toIntOrNull() ?: 0
                                }

                                // Validate reasonable timestamp values
                                if (minutes < 60 && seconds < 60) {
                                    val timestampMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L

                                    // Check if this range doesn't overlap with an existing URL annotation
                                    val overlapsWithUrl = urlRanges.any { urlRange ->
                                        match.range.first < urlRange.last && match.range.last >= urlRange.first
                                    }

                                    if (!overlapsWithUrl) {
                                        addStyle(
                                            style = SpanStyle(
                                                color = timestampColor,
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            start = match.range.first,
                                            end = match.range.last + 1
                                        )
                                        addStringAnnotation(
                                            tag = "TIMESTAMP",
                                            annotation = timestampMs.toString(),
                                            start = match.range.first,
                                            end = match.range.last + 1
                                        )
                                    }
                                }
                            }
                        }

                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f
                            ),
                            onClick = { offset ->
                                // Check for timestamp clicks first
                                annotatedString.getStringAnnotations(
                                    tag = "TIMESTAMP",
                                    start = offset,
                                    end = offset
                                ).firstOrNull()?.let { annotation ->
                                    val timestampMs = annotation.item.toLongOrNull()
                                    if (timestampMs != null) {
                                        onTimestampClick(timestampMs)
                                    }
                                    return@ClickableText
                                }

                                // Then check for URL clicks
                                annotatedString.getStringAnnotations(
                                    tag = "URL",
                                    start = offset,
                                    end = offset
                                ).firstOrNull()?.let { annotation ->
                                    try {
                                        uriHandler.openUri(annotation.item)
                                    } catch (e: Exception) {
                                        // Handle invalid URLs gracefully
                                    }
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "No show notes available for this episode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // "Read full show notes online" link
                    if (!episodeLink.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(episodeLink)
                                } catch (e: Exception) {
                                    // Handle invalid URL gracefully
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Read full show notes online",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCaptureButton(
    windowSeconds: Int,
    isCapturing: Boolean,
    captureProgress: String?,
    isDownloaded: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onCapture,
            enabled = !isCapturing && isDownloaded,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
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
            text = if (isDownloaded) {
                "Window: ±${windowSeconds}s"
            } else {
                "Download episode to enable capture"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EpisodeCapturePopup(
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
                text = capture.transcription.take(150) + if (capture.transcription.length > 150) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                maxLines = 3,
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
