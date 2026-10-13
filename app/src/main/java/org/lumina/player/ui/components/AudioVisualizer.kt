package org.lumina.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.lumina.player.ui.theme.LuminaAccent

/**
 * High-performance real-time audio rhythm visualizer.
 * Renders live frequency bands computed directly from the audio playback pipeline.
 */
@Composable
fun AudioVisualizer(
    spectrumData: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = LuminaAccent
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            accentColor,
            primaryColor,
            primaryColor.copy(alpha = 0.6f)
        )
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val minBarHeight = 4.dp.toPx()

        val actualBarCount = barCount.coerceAtMost(spectrumData.size.takeIf { it > 0 } ?: barCount)
        val barWidth = (totalWidth / actualBarCount) * 0.58f
        val gap = (totalWidth - barWidth * actualBarCount) / (actualBarCount - 1).coerceAtLeast(1)

        for (i in 0 until actualBarCount) {
            val magnitude = if (isPlaying && i < spectrumData.size) {
                spectrumData[i].coerceIn(0.04f, 1.0f)
            } else {
                0.04f
            }

            val targetHeight = (canvasHeight * magnitude).coerceAtLeast(minBarHeight)
            val x = i * (barWidth + gap)
            val y = canvasHeight - targetHeight

            // Draw primary rhythm bar with rounded pill caps
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, targetHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )

            // Draw subtle bright peak bead on active beats
            if (magnitude > 0.4f) {
                drawCircle(
                    color = accentColor,
                    radius = barWidth * 0.45f,
                    center = Offset(x + barWidth / 2, (y - 3.dp.toPx()).coerceAtLeast(barWidth * 0.45f))
                )
            }
        }
    }
}
