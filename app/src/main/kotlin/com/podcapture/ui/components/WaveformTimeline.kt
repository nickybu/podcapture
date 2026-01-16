package com.podcapture.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.podcapture.data.model.Capture
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun WaveformTimeline(
    currentPositionMs: Long,
    durationMs: Long,
    captures: List<Capture>,
    audioFileId: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface

    // Scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }

    // Generate consistent waveform data based on audio file ID
    val waveformData = remember(audioFileId, durationMs) {
        generateWaveformData(audioFileId, 100)
    }

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    // Use scrub position when scrubbing, otherwise use actual position
    val displayProgress = if (isScrubbing) scrubProgress else progress
    val scrubPositionMs = (scrubProgress * durationMs).toLong()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .onSizeChanged { canvasWidth = it.width.toFloat() }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        val progress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((progress * durationMs).toLong())
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
                            onSeek((scrubProgress * durationMs).toLong())
                            isScrubbing = false
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2
            val barWidth = canvasWidth / waveformData.size
            val maxBarHeight = canvasHeight * 0.8f

            // Draw waveform bars
            waveformData.forEachIndexed { index, amplitude ->
                val barProgress = index.toFloat() / waveformData.size
                val barHeight = amplitude * maxBarHeight
                val x = index * barWidth

                val color = if (barProgress <= displayProgress) {
                    primaryColor
                } else {
                    surfaceVariantColor
                }

                // Draw bar centered vertically
                drawRect(
                    color = color,
                    topLeft = Offset(x, centerY - barHeight / 2),
                    size = Size(barWidth - 1.dp.toPx(), barHeight)
                )
            }

            // Draw capture markers (dots above waveform)
            // Color is a darker shade of the waveform primary color
            val markerColor = primaryColor.copy(
                red = (primaryColor.red * 0.7f).coerceIn(0f, 1f),
                green = (primaryColor.green * 0.7f).coerceIn(0f, 1f),
                blue = (primaryColor.blue * 0.7f).coerceIn(0f, 1f)
            )
            val currentPositionForMarkers = if (isScrubbing) scrubPositionMs else currentPositionMs

            if (durationMs > 0) {
                captures.forEach { capture ->
                    val captureProgress = capture.windowStartMs.toFloat() / durationMs
                    val captureX = captureProgress * canvasWidth
                    val dotY = 8.dp.toPx() // Position above the waveform

                    // Check if this capture is currently active
                    val isActive = currentPositionForMarkers >= capture.windowStartMs &&
                            currentPositionForMarkers <= capture.windowEndMs

                    // Larger radius when active
                    val dotRadius = if (isActive) 7.dp.toPx() else 5.dp.toPx()

                    drawCircle(
                        color = markerColor,
                        radius = dotRadius,
                        center = Offset(captureX, dotY)
                    )
                }
            }

            // Draw playhead
            val playheadX = displayProgress * canvasWidth
            drawLine(
                color = tertiaryColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, canvasHeight),
                strokeWidth = 3.dp.toPx()
            )

            // Draw playhead circle (larger when scrubbing)
            val playheadRadius = if (isScrubbing) 12.dp.toPx() else 8.dp.toPx()
            drawCircle(
                color = tertiaryColor,
                radius = playheadRadius,
                center = Offset(playheadX, centerY)
            )
        }

        // Timestamp tooltip when scrubbing
        if (isScrubbing && canvasWidth > 0) {
            val density = LocalDensity.current
            val tooltipOffsetX = with(density) {
                (scrubProgress * canvasWidth).roundToInt() - 30.dp.roundToPx()
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(tooltipOffsetX.coerceIn(0, (canvasWidth - 60.dp.toPx()).toInt()), -36.dp.roundToPx()) }
                    .background(tooltipBackground, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatDuration(scrubPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = tooltipText
                )
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
