package org.lumina.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.lumina.player.R
import org.lumina.player.ui.theme.HiResGold
import org.lumina.player.ui.theme.LuminaAccent
import org.lumina.player.ui.theme.LuminaPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Visualizer animation styles supported by the full player.
 */
enum class VisualizerStyle(val displayNameRes: Int) {
    AURORA_WAVES(R.string.visualizer_aurora),         // Fluid Aurora Waves
    NEON_HALO(R.string.visualizer_halo),             // Breathing Neon Halo
    CRYSTAL_SPECTRUM(R.string.visualizer_crystal),   // Floating Crystal Spectrum
    REACTIVE_LIGHT_STRIP(R.string.visualizer_strip)  // Music Reactive Light Strip
}

/**
 * High-performance, multi-style real-time audio rhythm visualizer.
 * Supports smooth organic fluid waveforms, breathing halo rings, crystal spectrums, and reactive LED light strips.
 */
@Composable
fun AudioVisualizer(
    spectrumData: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    style: VisualizerStyle = VisualizerStyle.AURORA_WAVES,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = LuminaAccent,
    onCycleStyle: () -> Unit = {}
) {
    // Continuous phase angle for smooth organic wave & halo drift
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_phase")
    val phaseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_drift"
    )

    // Temporary toast indicator when user switches visualizer style
    var showStyleBadge by remember { mutableStateOf(false) }
    var currentBadgeStyle by remember { mutableStateOf(style) }

    LaunchedEffect(style) {
        currentBadgeStyle = style
        showStyleBadge = true
        delay(1800)
        showStyleBadge = false
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Suppress rectangular ripple
            ) {
                onCycleStyle()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (style) {
                VisualizerStyle.AURORA_WAVES -> {
                    drawAuroraWaves(
                        spectrum = spectrumData,
                        isPlaying = isPlaying,
                        phase = phaseAngle,
                        primaryColor = primaryColor,
                        accentColor = accentColor
                    )
                }
                VisualizerStyle.NEON_HALO -> {
                    drawNeonHalo(
                        spectrum = spectrumData,
                        isPlaying = isPlaying,
                        phase = phaseAngle,
                        primaryColor = primaryColor,
                        accentColor = accentColor
                    )
                }
                VisualizerStyle.CRYSTAL_SPECTRUM -> {
                    drawCrystalSpectrum(
                        spectrum = spectrumData,
                        isPlaying = isPlaying,
                        primaryColor = primaryColor,
                        accentColor = accentColor
                    )
                }
                VisualizerStyle.REACTIVE_LIGHT_STRIP -> {
                    drawReactiveLightStrip(
                        spectrum = spectrumData,
                        isPlaying = isPlaying,
                        primaryColor = primaryColor,
                        accentColor = accentColor
                    )
                }
            }
        }

        // Floating style indicator badge on tap
        AnimatedVisibility(
            visible = showStyleBadge,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = stringResource(currentBadgeStyle.displayNameRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 1. Fluid Aurora Waves: Multi-layer continuous Bezier soundwaves with luminous gradient fill.
 */
private fun DrawScope.drawAuroraWaves(
    spectrum: FloatArray,
    isPlaying: Boolean,
    phase: Float,
    primaryColor: Color,
    accentColor: Color
) {
    val width = size.width
    val height = size.height
    val centerY = height * 0.52f
    val maxAmplitude = height * 0.38f

    // Extract low, mid, and high energy from spectrum
    val bassEnergy = if (isPlaying && spectrum.isNotEmpty()) {
        (0 until min(6, spectrum.size)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.05f

    val midEnergy = if (isPlaying && spectrum.size >= 12) {
        (6 until min(16, spectrum.size)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.05f

    val trebleEnergy = if (isPlaying && spectrum.size >= 18) {
        (16 until spectrum.size).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    // 1. Background diffuse ambient wave
    val bgPath = Path()
    bgPath.moveTo(0f, height)
    bgPath.lineTo(0f, centerY)

    val stepCount = 48
    val dx = width / stepCount

    for (i in 0..stepCount) {
        val x = i * dx
        val normalizedX = x / width
        val wave1 = sin(normalizedX * 2.5 * PI + phase)
        val wave2 = cos(normalizedX * 4.0 * PI - phase * 0.7)
        val combined = (wave1 * 0.65 + wave2 * 0.35).toFloat()
        val y = centerY + combined * maxAmplitude * (bassEnergy * 0.8f + midEnergy * 0.4f)
        bgPath.lineTo(x, y)
    }
    bgPath.lineTo(width, height)
    bgPath.close()

    drawPath(
        path = bgPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.22f),
                primaryColor.copy(alpha = 0.12f),
                Color.Transparent
            ),
            startY = centerY - maxAmplitude,
            endY = height
        )
    )

    // 2. Primary harmonic glowing ribbon (Foreground)
    val fgPath = Path()
    var startY = centerY
    for (i in 0..stepCount) {
        val x = i * dx
        val normalizedX = x / width
        val wave1 = sin(normalizedX * 3.2 * PI + phase * 1.2)
        val wave2 = sin(normalizedX * 6.0 * PI - phase) * 0.25
        val combined = (wave1 + wave2).toFloat()
        val y = centerY + combined * maxAmplitude * (midEnergy * 0.75f + trebleEnergy * 0.45f)
        if (i == 0) {
            fgPath.moveTo(x, y)
            startY = y
        } else {
            fgPath.lineTo(x, y)
        }
    }

    drawPath(
        path = fgPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.8f),
                accentColor,
                HiResGold,
                primaryColor
            )
        ),
        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
    )

    // 3. Counter-phase secondary soft ribbon
    val secPath = Path()
    for (i in 0..stepCount) {
        val x = i * dx
        val normalizedX = x / width
        val wave = cos(normalizedX * 2.8 * PI - phase * 0.9).toFloat()
        val y = centerY + wave * maxAmplitude * 0.6f * (bassEnergy * 0.7f + trebleEnergy * 0.5f)
        if (i == 0) secPath.moveTo(x, y) else secPath.lineTo(x, y)
    }

    drawPath(
        path = secPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.5f),
                primaryColor.copy(alpha = 0.85f),
                accentColor.copy(alpha = 0.6f)
            )
        ),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * 2. Breathing Neon Halo: Radial pulsating sound ring with organic concentric glow rings.
 */
private fun DrawScope.drawNeonHalo(
    spectrum: FloatArray,
    isPlaying: Boolean,
    phase: Float,
    primaryColor: Color,
    accentColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = min(size.width, size.height) * 0.46f
    val baseRadius = maxRadius * 0.55f

    val bassMagnitude = if (isPlaying && spectrum.isNotEmpty()) {
        (0 until min(4, spectrum.size)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    // 1. Center pulsing core glow
    val coreRadius = baseRadius * (0.35f + bassMagnitude * 0.25f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.35f + bassMagnitude * 0.3f),
                primaryColor.copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * 1.5f
        ),
        radius = coreRadius * 1.5f,
        center = center
    )

    // 2. 360-degree modulated radial sound ring
    val rayCount = 48
    val rayPath = Path()

    for (i in 0 until rayCount) {
        val angle = (2 * PI * i / rayCount + phase * 0.2).toFloat()
        val spectrumIndex = (i % spectrum.size.coerceAtLeast(1))
        val mag = if (isPlaying && spectrumIndex < spectrum.size) spectrum[spectrumIndex].coerceIn(0.04f, 1f) else 0.04f
        val r = baseRadius + mag * (maxRadius - baseRadius) * 0.85f

        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (i == 0) rayPath.moveTo(x, y) else rayPath.lineTo(x, y)
    }
    rayPath.close()

    drawPath(
        path = rayPath,
        brush = Brush.sweepGradient(
            colors = listOf(
                primaryColor,
                accentColor,
                HiResGold,
                accentColor,
                primaryColor
            ),
            center = center
        ),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )

    // 3. Floating orbit beads around active peaks
    for (i in 0 until rayCount step 4) {
        val angle = (2 * PI * i / rayCount + phase * 0.2).toFloat()
        val spectrumIndex = (i % spectrum.size.coerceAtLeast(1))
        val mag = if (isPlaying && spectrumIndex < spectrum.size) spectrum[spectrumIndex] else 0.04f
        if (mag > 0.35f) {
            val r = baseRadius + mag * (maxRadius - baseRadius) * 0.95f + 6.dp.toPx()
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            drawCircle(
                color = accentColor,
                radius = 3.5.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 3. Floating Crystal Spectrum: Modern symmetrical crystal pillars with peak-hold delayed points.
 */
private fun DrawScope.drawCrystalSpectrum(
    spectrum: FloatArray,
    isPlaying: Boolean,
    primaryColor: Color,
    accentColor: Color
) {
    val totalWidth = size.width
    val canvasHeight = size.height
    val centerY = canvasHeight * 0.5f
    val barCount = 24.coerceAtMost(spectrum.size.takeIf { it > 0 } ?: 24)

    val usableWidth = totalWidth * 0.92f
    val barWidth = (usableWidth / barCount) * 0.55f
    val gap = (usableWidth - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
    val startX = (totalWidth - usableWidth) / 2f

    val gradient = Brush.verticalGradient(
        colors = listOf(
            accentColor,
            primaryColor,
            primaryColor.copy(alpha = 0.5f)
        )
    )

    for (i in 0 until barCount) {
        val magnitude = if (isPlaying && i < spectrum.size) {
            spectrum[i].coerceIn(0.04f, 1.0f)
        } else {
            0.04f
        }

        val halfHeight = (canvasHeight * 0.42f * magnitude).coerceAtLeast(3.dp.toPx())
        val x = startX + i * (barWidth + gap)
        val top = centerY - halfHeight
        val bottom = centerY + halfHeight

        // Draw symmetrical rounded capsule
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(x, top),
            size = Size(barWidth, bottom - top),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        // Floating glowing peak beads on top and bottom
        if (magnitude > 0.3f) {
            drawCircle(
                color = accentColor,
                radius = barWidth * 0.5f,
                center = Offset(x + barWidth / 2f, top - 4.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.6f),
                radius = barWidth * 0.4f,
                center = Offset(x + barWidth / 2f, bottom + 4.dp.toPx())
            )
        }
    }
}

/**
 * 4. Music Reactive Light Strip: Desktop ambient smart LED strip model pulsing outward from center.
 */
private fun DrawScope.drawReactiveLightStrip(
    spectrum: FloatArray,
    isPlaying: Boolean,
    primaryColor: Color,
    accentColor: Color
) {
    val totalWidth = size.width
    val canvasHeight = size.height
    val centerY = canvasHeight * 0.5f
    val centerX = totalWidth * 0.5f

    // 16 segments expanding to left and 16 to right
    val halfSegments = 16
    val segmentWidth = (totalWidth * 0.44f) / halfSegments
    val gap = 3.dp.toPx()
    val cellWidth = segmentWidth - gap

    val gradient = Brush.horizontalGradient(
        colors = listOf(
            accentColor,
            primaryColor,
            accentColor
        )
    )

    for (i in 0 until halfSegments) {
        // Map center to low frequency (bass) and edges to highs
        val spectrumIdx = (i * spectrum.size / halfSegments).coerceIn(0, (spectrum.size - 1).coerceAtLeast(0))
        val mag = if (isPlaying && spectrum.isNotEmpty()) spectrum[spectrumIdx].coerceIn(0.04f, 1f) else 0.04f
        val cellHeight = (canvasHeight * 0.38f * mag).coerceAtLeast(4.dp.toPx())

        // Right side
        val rightX = centerX + i * segmentWidth + gap / 2f
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(rightX, centerY - cellHeight / 2f),
            size = Size(cellWidth, cellHeight),
            cornerRadius = CornerRadius(cellWidth / 2f, cellWidth / 2f)
        )

        // Left side (symmetric)
        val leftX = centerX - (i + 1) * segmentWidth + gap / 2f
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(leftX, centerY - cellHeight / 2f),
            size = Size(cellWidth, cellHeight),
            cornerRadius = CornerRadius(cellWidth / 2f, cellWidth / 2f)
        )
    }

    // Central glowing anchor diode
    val centerEnergy = if (isPlaying && spectrum.isNotEmpty()) spectrum[0].coerceIn(0.04f, 1f) else 0.04f
    drawCircle(
        color = accentColor,
        radius = (6.dp.toPx() + centerEnergy * 8.dp.toPx()),
        center = Offset(centerX, centerY)
    )
}
