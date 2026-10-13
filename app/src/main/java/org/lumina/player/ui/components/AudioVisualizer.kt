package org.lumina.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Authentic Siri / Apple Intelligence Luminous Glowing Fluid Aura.
 * Completely autonomous, continuous fluid convection with pure, saturated Apple neon jewel tones.
 * Features a seamless monotonic time integration engine without periodic restart resets or discontinuities,
 * guaranteeing 100% continuous, twitch-free, silky-smooth motion.
 */
@Composable
fun AudioVisualizer(
    spectrumData: FloatArray = FloatArray(0),
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Monotonically integrated continuous time state (seconds).
    // Using frame delta integration avoids periodic modulo resets or restart discontinuities,
    // ensuring mathematically continuous, infinitely smooth motion without any jerks or twitches.
    var timeSeconds by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(isPlaying) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameNanos != 0L) {
                    val dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000.0
                    // Continuous velocity integration:
                    // Standard serene gliding speed when playing, gentle resting drift when paused
                    val speedFactor = if (isPlaying) 1.0 else 0.35
                    timeSeconds += dt * speedFactor
                }
                lastFrameNanos = frameTimeNanos
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSiriLuminousAura(time = timeSeconds)
        }
    }
}

/**
 * Renders the authentic Apple Siri / Apple Intelligence glowing fluid orb convection.
 * Features pure saturated jewel tones (Electric Cyan, Vivid Magenta, Royal Violet, Luminous Amber)
 * overlapping with translucent optical color-mixing against a pristine pure dark canvas.
 * Driven by smooth continuous harmonic trigonometric functions of monotonic time.
 */
private fun DrawScope.drawSiriLuminousAura(
    time: Double
) {
    val width = size.width
    val height = size.height
    val centerX = width * 0.50f
    val centerY = height * 0.50f
    val baseOrbRadius = min(width, height) * 0.42f
    val t = time

    // 1. Siri Electric Violet / Royal Purple Orb (深邃电光紫)
    val violetX = centerX + sin(t * 0.35 + 1.2).toFloat() * (width * 0.16f)
    val violetY = centerY + cos(t * 0.30 + 2.0).toFloat() * (height * 0.13f)
    val violetRadius = baseOrbRadius * (1.05f + sin(t * 0.45).toFloat() * 0.10f)
    val violetAlpha = 0.55f + cos(t * 0.28).toFloat() * 0.08f

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
    val amberX = centerX - sin(t * 0.42 - 0.8).toFloat() * (width * 0.15f)
    val amberY = centerY + cos(t * 0.36 + 0.5).toFloat() * (height * 0.11f)
    val amberRadius = baseOrbRadius * (0.88f + cos(t * 0.38).toFloat() * 0.10f)
    val amberAlpha = 0.52f + sin(t * 0.32).toFloat() * 0.08f

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
    val magentaX = centerX - cos(t * 0.28 + 1.5).toFloat() * (width * 0.18f)
    val magentaY = centerY - sin(t * 0.34 + 0.8).toFloat() * (height * 0.14f)
    val magentaRadius = baseOrbRadius * (1.00f + cos(t * 0.42).toFloat() * 0.12f)
    val magentaAlpha = 0.56f + sin(t * 0.38).toFloat() * 0.08f

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
    val cyanX = centerX + cos(t * 0.45).toFloat() * (width * 0.18f) - sin(t * 0.22).toFloat() * (width * 0.08f)
    val cyanY = centerY + sin(t * 0.38 + 1.8).toFloat() * (height * 0.12f)
    val cyanRadius = baseOrbRadius * (0.95f + sin(t * 0.46).toFloat() * 0.12f)
    val cyanAlpha = 0.58f + cos(t * 0.30).toFloat() * 0.08f

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
    val coreRadius = baseOrbRadius * (0.65f + sin(t * 0.52).toFloat() * 0.08f)
    val coreAlpha = 0.28f + cos(t * 0.40).toFloat() * 0.05f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF).copy(alpha = coreAlpha),
                Color(0xFFE0F7FA).copy(alpha = coreAlpha * 0.80f),
                Color(0xFFC084FC).copy(alpha = coreAlpha * 0.50f),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = coreRadius
        ),
        radius = coreRadius,
        center = Offset(centerX, centerY)
    )
}
