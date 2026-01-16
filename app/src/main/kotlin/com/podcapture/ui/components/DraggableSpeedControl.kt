package com.podcapture.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun DraggableSpeedControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minSpeed: Float = 0.5f,
    maxSpeed: Float = 2.0f,
    stepSize: Float = 0.05f
) {
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 20f // pixels to trigger one step

    Box(
        modifier = modifier
            .pointerInput(currentSpeed) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = { dragAccumulator = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount

                        // Calculate steps based on accumulated drag
                        val steps = (dragAccumulator / dragThreshold).toInt()
                        if (steps != 0) {
                            val newSpeed = (currentSpeed + steps * stepSize)
                                .coerceIn(minSpeed, maxSpeed)
                            // Round to nearest step to avoid floating point issues
                            val roundedSpeed = (newSpeed / stepSize).roundToInt() * stepSize
                            onSpeedChange(roundedSpeed)
                            dragAccumulator -= steps * dragThreshold
                        }
                    }
                )
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%.2fx", currentSpeed),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
