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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Authentic Siri / Apple Intelligence Luminous Glowing Fluid Aura.
 * Completely autonomous, serene fluid convection with pure, saturated Apple neon jewel tones.
 * Independent of raw audio transients for a relaxing, hypnotic, and 100% spasm-free visual experience.
 */
@Composable
fun AudioVisualizer(
    spectrumData: FloatArray = FloatArray(0),
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 3 continuous, asynchronous harmonic phase angles driving the organic Siri orbital convection
    val infiniteTransition = rememberInfiniteTransition(label = "siri_ambient_aura")

    val phasePrimary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "siri_phase_primary"
    )

    val phaseSecondary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "siri_phase_secondary"
    )

    val phasePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "siri_phase_pulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSiriLuminousAura(
                phase1 = phasePrimary,
                phase2 = phaseSecondary,
                pulse = phasePulse
            )
        }
    }
}

/**
 * Renders the authentic Apple Siri / Apple Intelligence glowing fluid orb convection.
 * Features pure saturated jewel tones (Electric Cyan, Vivid Magenta, Royal Violet, Luminous Amber)
 * overlapping with translucent optical color-mixing against a pristine pure dark canvas.
 */
private fun DrawScope.drawSiriLuminousAura(
    phase1: Float,
    phase2: Float,
    pulse: Float
) {
    val width = size.width
    val height = size.height
    val centerX = width * 0.50f
    val centerY = height * 0.50f
    val baseOrbRadius = min(width, height) * 0.40f

    // 1. Siri Electric Violet / Royal Purple Orb (深邃电光紫)
    val violetX = centerX + sin(phase2 * 1.1f + 2.0f).toFloat() * (width * 0.16f)
    val violetY = centerY + cos(phase1 * 0.9f + 1.5f).toFloat() * (height * 0.13f)
    val violetRadius = baseOrbRadius * (1.05f + sin(pulse * 0.9f).toFloat() * 0.12f)
    val violetAlpha = 0.55f + cos(phase2).toFloat() * 0.08f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF7000FF).copy(alpha = violetAlpha),
                Color(0xFF7C3AED).copy(alpha = violetAlpha * 0.75f),
                Color(0xFFA855F7).copy(alpha = violetAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(violetX, violetY),
            radius = violetRadius
        ),
        radius = violetRadius,
        center = Offset(violetX, violetY)
    )

    // 2. Siri Luminous Amber / Warm Sunrise Gold Orb (温暖日出金)
    val amberX = centerX - sin(phase1 * 1.1f - 1.0f).toFloat() * (width * 0.15f)
    val amberY = centerY + cos(phase2 * 0.8f - 0.5f).toFloat() * (height * 0.11f)
    val amberRadius = baseOrbRadius * (0.88f + cos(pulse * 0.7f).toFloat() * 0.10f)
    val amberAlpha = 0.52f + sin(phase1).toFloat() * 0.08f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF9500).copy(alpha = amberAlpha),
                Color(0xFFFFB800).copy(alpha = amberAlpha * 0.75f),
                Color(0xFFF59E0B).copy(alpha = amberAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(amberX, amberY),
            radius = amberRadius
        ),
        radius = amberRadius,
        center = Offset(amberX, amberY)
    )

    // 3. Siri Vivid Magenta / Hot Pink Orb (标志性明艳洋红)
    val magentaX = centerX - cos(phase1 * 0.85f + 1.2f).toFloat() * (width * 0.18f)
    val magentaY = centerY - sin(phase2 + 0.8f).toFloat() * (height * 0.14f)
    val magentaRadius = baseOrbRadius * (1.00f + cos(pulse * 1.1f).toFloat() * 0.14f)
    val magentaAlpha = 0.56f + sin(pulse).toFloat() * 0.08f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF2D55).copy(alpha = magentaAlpha),
                Color(0xFFFF375F).copy(alpha = magentaAlpha * 0.75f),
                Color(0xFFE024C3).copy(alpha = magentaAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(magentaX, magentaY),
            radius = magentaRadius
        ),
        radius = magentaRadius,
        center = Offset(magentaX, magentaY)
    )

    // 4. Siri Electric Cyan / Bright Aqua Orb (纯净电光天青)
    val cyanX = centerX + cos(phase1).toFloat() * (width * 0.18f) - sin(phase2 * 0.5f).toFloat() * (width * 0.08f)
    val cyanY = centerY + sin(phase1).toFloat() * (height * 0.12f)
    val cyanRadius = baseOrbRadius * (0.95f + sin(pulse).toFloat() * 0.12f)
    val cyanAlpha = 0.58f + cos(pulse * 0.8f).toFloat() * 0.08f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = cyanAlpha),
                Color(0xFF00B4D8).copy(alpha = cyanAlpha * 0.75f),
                Color(0xFF0284C7).copy(alpha = cyanAlpha * 0.35f),
                Color.Transparent
            ),
            center = Offset(cyanX, cyanY),
            radius = cyanRadius
        ),
        radius = cyanRadius,
        center = Offset(cyanX, cyanY)
    )

    // 5. Central Ethereal Luminescence Core (中央融合流光之核)
    val coreRadius = baseOrbRadius * (0.65f + sin(pulse * 1.2f).toFloat() * 0.08f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.28f),
                Color(0xFFE0F7FA).copy(alpha = 0.22f),
                Color(0xFFC084FC).copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = coreRadius
        ),
        radius = coreRadius,
        center = Offset(centerX, centerY)
    )
}
