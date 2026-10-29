package com.podcapture.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.podcapture.data.model.Capture
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val BAR_COUNT = 100

/**
 * Waveform scrubber with capture markers.
 *
 * Takes the position as a plain value for existing callers; it's forwarded through a State
 * so a position change only redraws the canvas instead of recomposing the timeline. Callers
 * that can supply the position lazily should use the `() -> Long` overload.
 */
@Composable
fun WaveformTimeline(
    currentPositionMs: Long,
    durationMs: Long,
    captures: List<Capture>,
    audioFileId: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCaptureMarkerClick: ((Capture) -> Unit)? = null
) {
    val position = rememberUpdatedState(currentPositionMs)
    WaveformTimeline(
        currentPositionMs = { position.value },
        durationMs = durationMs,
        captures = captures,
        audioFileId = audioFileId,
        onSeek = onSeek,
        modifier = modifier,
        onCaptureMarkerClick = onCaptureMarkerClick
    )
}

/**
 * Waveform scrubber with capture markers. [currentPositionMs] is only read while drawing,
 * so playback ticks invalidate the draw phase, not composition.
 *
 * @param onCaptureMarkerClick if set, tapping a capture dot calls it instead of seeking to
 *   the tapped position.
 */
@Composable
fun WaveformTimeline(
    currentPositionMs: () -> Long,
    durationMs: Long,
    captures: List<Capture>,
    audioFileId: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCaptureMarkerClick: ((Capture) -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    // Capture dots are a darker shade of the waveform's primary color
    val markerColor = remember(primaryColor) {
        primaryColor.copy(
            red = primaryColor.red * 0.7f,
            green = primaryColor.green * 0.7f,
            blue = primaryColor.blue * 0.7f
        )
    }

    // Scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }

    // Generate consistent waveform data based on audio file ID
    val waveformData = remember(audioFileId) {
        generateWaveformData(audioFileId, BAR_COUNT)
    }

    // Read from the long-lived gesture and draw lambdas, which shouldn't restart or rebuild
    // their caches just because the caller passed a new list or lambda instance.
    val currentCaptures by rememberUpdatedState(captures)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnMarkerClick by rememberUpdatedState(onCaptureMarkerClick)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .onSizeChanged { canvasWidth = it.width.toFloat() }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        val onMarkerClick = currentOnMarkerClick
                        val marker = onMarkerClick?.let {
                            findCaptureMarkerAt(
                                x = offset.x,
                                y = offset.y,
                                captures = currentCaptures,
                                durationMs = durationMs,
                                width = size.width.toFloat(),
                                dotY = 8.dp.toPx(),
                                touchRadius = 16.dp.toPx()
                            )
                        }
                        if (marker != null && onMarkerClick != null) {
                            onMarkerClick(marker)
                        } else {
                            val progress = (offset.x / size.width).coerceIn(0f, 1f)
                            currentOnSeek((progress * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            scrubProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            isScrubbing = true
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            currentOnSeek((scrubProgress * durationMs).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
                .drawWithCache {
                    // Geometry depends only on the size and the waveform, so it's built once
                    // per layout rather than on every frame.
                    val canvasHeight = size.height
                    val centerY = canvasHeight / 2
                    val barWidth = size.width / waveformData.size
                    val barSize = Array(waveformData.size) { index ->
                        Size(barWidth - 1.dp.toPx(), waveformData[index] * canvasHeight * 0.8f)
                    }
                    val barTopLeft = Array(waveformData.size) { index ->
                        Offset(index * barWidth, centerY - barSize[index].height / 2)
                    }
                    val dotY = 8.dp.toPx()
                    val dotRadius = 5.dp.toPx()
                    val activeDotRadius = 7.dp.toPx()
                    val playheadStroke = 3.dp.toPx()
                    val playheadRadius = 8.dp.toPx()
                    val scrubbingPlayheadRadius = 12.dp.toPx()

                    onDrawBehind {
                        // State reads below only invalidate this draw block.
                        val scrubbing = isScrubbing
                        val positionMs = currentPositionMs()
                        val displayProgress = if (scrubbing) {
                            scrubProgress
                        } else {
                            playbackProgress(positionMs, durationMs)
                        }

                        // Waveform bars, played portion in the primary color
                        for (index in barSize.indices) {
                            val barProgress = index.toFloat() / barSize.size
                            drawRect(
                                color = if (barProgress <= displayProgress) primaryColor else surfaceVariantColor,
                                topLeft = barTopLeft[index],
                                size = barSize[index]
                            )
                        }

                        // Capture markers (dots above the waveform), larger while playing
                        if (durationMs > 0) {
                            val markerPositionMs = if (scrubbing) {
                                (scrubProgress * durationMs).toLong()
                            } else {
                                positionMs
                            }
                            for (capture in currentCaptures) {
                                val isActive = markerPositionMs >= capture.windowStartMs &&
                                    markerPositionMs <= capture.windowEndMs
                                drawCircle(
                                    color = markerColor,
                                    radius = if (isActive) activeDotRadius else dotRadius,
                                    center = Offset(
                                        captureMarkerX(capture, durationMs, size.width),
                                        dotY
                                    )
                                )
                            }
                        }

                        // Playhead line and handle (larger when scrubbing)
                        val playheadX = displayProgress * size.width
                        drawLine(
                            color = tertiaryColor,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, canvasHeight),
                            strokeWidth = playheadStroke
                        )
                        drawCircle(
                            color = tertiaryColor,
                            radius = if (scrubbing) scrubbingPlayheadRadius else playheadRadius,
                            center = Offset(playheadX, centerY)
                        )
                    }
                }
        )

        // Timestamp tooltip when scrubbing
        if (isScrubbing && canvasWidth > 0) {
            ScrubTooltip(
                text = formatDuration((scrubProgress * durationMs).toLong()),
                offsetX = { scrubProgress * canvasWidth },
                maxOffsetX = { canvasWidth },
                background = tooltipBackground,
                textColor = tooltipText
            )
        }
    }
}

@Composable
private fun ScrubTooltip(
    text: String,
    offsetX: () -> Float,
    maxOffsetX: () -> Float,
    background: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .offset {
                val x = offsetX().roundToInt() - 30.dp.roundToPx()
                IntOffset(x.coerceIn(0, (maxOffsetX() - 60.dp.toPx()).toInt().coerceAtLeast(0)), -36.dp.roundToPx())
            }
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

internal fun playbackProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

internal fun captureMarkerX(capture: Capture, durationMs: Long, width: Float): Float =
    capture.windowStartMs.toFloat() / durationMs * width

/**
 * The capture whose marker dot is nearest to ([x], [y]) within [touchRadius], or null.
 * Dots sit at height [dotY], at their capture's window start along the timeline.
 */
internal fun findCaptureMarkerAt(
    x: Float,
    y: Float,
    captures: List<Capture>,
    durationMs: Long,
    width: Float,
    dotY: Float,
    touchRadius: Float
): Capture? {
    if (durationMs <= 0 || width <= 0f || abs(y - dotY) > touchRadius) return null
    return captures
        .minByOrNull { abs(captureMarkerX(it, durationMs, width) - x) }
        ?.takeIf { abs(captureMarkerX(it, durationMs, width) - x) <= touchRadius }
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

/**
 * Generates consistent pseudo-random waveform data based on the audio file ID.
 * This creates a visually appealing waveform pattern that's unique to each file.
 */
private fun generateWaveformData(audioFileId: String, barCount: Int): List<Float> {
    val seed = audioFileId.hashCode().toLong()
    val random = Random(seed)

    return List(barCount) { index ->
        // Create a more natural looking waveform with varying amplitudes
        val baseAmplitude = 0.3f + random.nextFloat() * 0.5f
        val variation = sin(index * 0.2f) * 0.2f
        val noise = (random.nextFloat() - 0.5f) * 0.2f

        (baseAmplitude + variation + noise).coerceIn(0.1f, 1f)
    }
}
