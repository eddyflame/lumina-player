package org.lumina.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Apple Music Style Fluid Aurora Ambient Visualizer.
 * Completely eliminates fragile geometric line waveforms. Employs volumetric multi-layer
 * diffused neon fluid energy fields that organically breathe, expand, and pulse with musical beats.
 * Physically immune to jitter, distortion, or erratic spasming.
 */
@Composable
fun AudioVisualizer(
    spectrumData: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Dual continuous phase drift angles for natural organic fluid convection
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_fluid")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_primary"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_secondary"
    )

    // Continuous smooth interpolation state across animation frames (butter-smooth 60/120Hz gliding)
    val smoothedSpectrum = remember { FloatArray(24) { 0.04f } }
    var frameTick by remember { mutableStateOf(0L) }

    LaunchedEffect(isPlaying) {
        while (isActive) {
            withFrameNanos { frameNanos ->
                frameTick = frameNanos
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTick to trigger 60/120Hz continuous rendering
            val _tick = frameTick

            // Exponential fluid damping towards target spectrum on each rendered frame
            val lerpFactor = 0.08f
            for (i in smoothedSpectrum.indices) {
                val target = if (isPlaying && i < spectrumData.size) spectrumData[i] else 0.04f
                smoothedSpectrum[i] += (target - smoothedSpectrum[i]) * lerpFactor
            }

            drawAppleMusicFluidAurora(
                spectrum = smoothedSpectrum,
                isPlaying = isPlaying,
                phase1 = phase1,
                phase2 = phase2
            )
        }
    }
}

/**
 * Renders the multi-layer volumetric fluid energy fields with optical translucent blending.
 */
private fun DrawScope.drawAppleMusicFluidAurora(
    spectrum: FloatArray,
    isPlaying: Boolean,
    phase1: Float,
    phase2: Float
) {
    val width = size.width
    val height = size.height
    val centerY = height * 0.50f
    val baseOrbRadius = min(width, height) * 0.38f

    // Extract frequency band energies (smoothed by per-frame damping)
    val s = spectrum.size
    val bassEnergy = if (isPlaying && s > 0) {
        (0 until min(4, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    val midEnergy = if (isPlaying && s >= 6) {
        (4 until min(12, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    val trebleEnergy = if (isPlaying && s >= 12) {
        (12 until s).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    val overallEnergy = (bassEnergy * 0.45f + midEnergy * 0.35f + trebleEnergy * 0.20f).coerceIn(0.04f, 1f)

    // 1. Deep Atmospheric Indigo Base Bed (暗夜底盘基底)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1E1B4B).copy(alpha = (0.35f * (0.6f + overallEnergy * 0.4f)).coerceIn(0.1f, 0.6f)),
                Color(0xFF0F172A).copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = Offset(width * 0.50f, centerY),
            radius = width * 0.65f
        ),
        radius = width * 0.65f,
        center = Offset(width * 0.50f, centerY)
    )

    // 2. ORB 1: Amber Gold Bass Pulse Orb (暖金低音脉冲核，核心节拍驱动)
    val amberX = width * 0.38f + cos(phase1 * 0.8f).toFloat() * 18.dp.toPx()
    val amberY = centerY + 24.dp.toPx() + sin(phase2).toFloat() * 12.dp.toPx()
    val amberRadius = baseOrbRadius * (0.85f + bassEnergy * 0.55f)
    val amberAlpha = (0.42f + bassEnergy * 0.38f).coerceIn(0.20f, 0.80f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFF59E0B).copy(alpha = amberAlpha),
                Color(0xFFFBBF24).copy(alpha = amberAlpha * 0.70f),
                Color(0xFFD97706).copy(alpha = amberAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(amberX, amberY),
            radius = amberRadius
        ),
        radius = amberRadius,
        center = Offset(amberX, amberY)
    )

    // 3. ORB 2: Royal Violet Harmonic Orb (皇家紫罗兰中频流光)
    val violetX = width * 0.64f + sin(phase1).toFloat() * 22.dp.toPx()
    val violetY = centerY - 18.dp.toPx() + cos(phase2 * 0.9f).toFloat() * 15.dp.toPx()
    val violetRadius = baseOrbRadius * (0.95f + midEnergy * 0.50f)
    val violetAlpha = (0.45f + midEnergy * 0.35f).coerceIn(0.20f, 0.80f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF7C3AED).copy(alpha = violetAlpha),
                Color(0xFF9333EA).copy(alpha = violetAlpha * 0.75f),
                Color(0xFFC084FC).copy(alpha = violetAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(violetX, violetY),
            radius = violetRadius
        ),
        radius = violetRadius,
        center = Offset(violetX, violetY)
    )

    // 4. ORB 3: Neon Cyan Treble Fluid Orb (天青高音极光光斑)
    val cyanX = width * 0.32f + cos(phase2 * 1.1f).toFloat() * 20.dp.toPx()
    val cyanY = centerY - 12.dp.toPx() + sin(phase1 * 0.85f).toFloat() * 14.dp.toPx()
    val cyanRadius = baseOrbRadius * (0.90f + trebleEnergy * 0.50f)
    val cyanAlpha = (0.48f + trebleEnergy * 0.38f).coerceIn(0.20f, 0.86f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF00F5D4).copy(alpha = cyanAlpha),
                Color(0xFF06B6D4).copy(alpha = cyanAlpha * 0.75f),
                Color(0xFF38BDF8).copy(alpha = cyanAlpha * 0.38f),
                Color.Transparent
            ),
            center = Offset(cyanX, cyanY),
            radius = cyanRadius
        ),
        radius = cyanRadius,
        center = Offset(cyanX, cyanY)
    )

    // 5. ORB 4: Vivid Magenta Harmonic Core (洋红共鸣微光，交织混色之核)
    val magentaX = width * 0.52f + sin(phase2 * 0.7f).toFloat() * 16.dp.toPx()
    val magentaY = centerY + 10.dp.toPx() + cos(phase1 * 1.2f).toFloat() * 10.dp.toPx()
    val magentaRadius = baseOrbRadius * (0.75f + (midEnergy + bassEnergy) * 0.35f)
    val magentaAlpha = (0.32f + midEnergy * 0.30f).coerceIn(0.15f, 0.62f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFD946EF).copy(alpha = magentaAlpha),
                Color(0xFFEC4899).copy(alpha = magentaAlpha * 0.60f),
                Color.Transparent
            ),
            center = Offset(magentaX, magentaY),
            radius = magentaRadius
        ),
        radius = magentaRadius,
        center = Offset(magentaX, magentaY)
    )

    // 6. Central Acoustic Horizon Line (中央声学微光基准丝)
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF00F5D4).copy(alpha = (0.18f * (0.5f + trebleEnergy * 0.5f)).coerceIn(0.05f, 0.35f)),
                Color(0xFFC084FC).copy(alpha = (0.28f * (0.5f + midEnergy * 0.5f)).coerceIn(0.05f, 0.45f)),
                Color(0xFFFBBF24).copy(alpha = (0.22f * (0.5f + bassEnergy * 0.5f)).coerceIn(0.05f, 0.40f)),
                Color.Transparent
            ),
            startX = 0f,
            endX = width
        ),
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 1.dp.toPx()
    )
}
