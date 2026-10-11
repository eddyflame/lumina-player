package org.lumina.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.lumina.player.ui.theme.LuminaAccent
import org.lumina.player.ui.theme.LuminaPrimary
import kotlin.random.Random

/**
 * 现代动态频谱律动动画组件 (Audio Spectrum Visualizer)
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val animatables = remember(barCount) {
        List(barCount) { Animatable(0.15f) }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            animatables.forEachIndexed { index, anim ->
                val duration = 300 + (index % 5) * 80
                val target = 0.35f + Random.nextFloat() * 0.65f
                anim.animateTo(
                    targetValue = target,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = duration, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        } else {
            animatables.forEach { anim ->
                anim.animateTo(0.08f, tween(300))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.6f
        val gap = (totalWidth - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)

        val gradient = Brush.verticalGradient(
            colors = listOf(LuminaAccent, color)
        )

        animatables.forEachIndexed { index, anim ->
            val barHeight = size.height * anim.value
            val x = index * (barWidth + gap)
            val y = size.height - barHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
