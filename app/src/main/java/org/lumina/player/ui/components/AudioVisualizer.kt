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
            animation = tween(durationMillis = 8500, easing = LinearEasing),
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

// Curated high-luminance neon palette matching modern luxury ambient audio players
private val NeonCyan = Color(0xFF00F0FF)
private val ElectricBlue = Color(0xFF3B82F6)
private val DeepViolet = Color(0xFF7C3AED)
private val VividMagenta = Color(0xFFD946EF)
private val WarmAmber = Color(0xFFF59E0B)

/**
 * Appends a mathematically smooth Catmull-Rom cubic spline path through the given list of control points.
 */
private fun appendSpline(path: Path, points: List<Offset>, isReverse: Boolean = false) {
    val pts = if (isReverse) points.asReversed() else points
    if (pts.isEmpty()) return
    if (!isReverse) {
        path.moveTo(pts[0].x, pts[0].y)
    } else {
        path.lineTo(pts[0].x, pts[0].y)
    }
    val n = pts.size
    for (i in 0 until n - 1) {
        val p0 = pts[(i - 1).coerceAtLeast(0)]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[(i + 2).coerceAtMost(n - 1)]

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
}

/**
 * Appends a mathematically smooth closed Catmull-Rom cubic spline through a circular list of points.
 */
private fun appendClosedSpline(path: Path, points: List<Offset>) {
    val n = points.size
    if (n < 3) return
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.close()
}

/**
 * 1. Fluid Aurora Waves: Multi-layer volumetric Bezier fluid ribbon with luminous gradient fill and ambient glow.
 * Anchored nodes prevent horizontal crawling; smooth cubic splines eliminate twitching.
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
    val maxLift = height * 0.28f
    val baseThickness = 34.dp.toPx()
    val maxThickness = 82.dp.toPx()
    val idleFloat = height * 0.035f

    // 7 horizontal anchor nodes across the screen width (fixed X positions, zero crawling)
    val nodeFractions = floatArrayOf(0.00f, 0.16f, 0.35f, 0.50f, 0.65f, 0.84f, 1.00f)
    val nodeEnergies = FloatArray(7)

    if (isPlaying && spectrum.isNotEmpty()) {
        val s = spectrum.size
        val bassAvg = (0 until min(3, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
        nodeEnergies[0] = bassAvg * 0.40f
        nodeEnergies[1] = (0 until min(4, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
        nodeEnergies[2] = (3 until min(8, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
        nodeEnergies[3] = (7 until min(14, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
        nodeEnergies[4] = (12 until min(18, s)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
        nodeEnergies[5] = (17 until s).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
        nodeEnergies[6] = nodeEnergies[5] * 0.40f
    } else {
        for (i in 0..6) nodeEnergies[i] = 0.05f
    }

    val overallEnergy = nodeEnergies.average().toFloat().coerceIn(0.04f, 1f)
    val bassEnergy = nodeEnergies[1]
    val trebleEnergy = nodeEnergies[5]

    // 1. Ambient Background Radial Glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                DeepViolet.copy(alpha = 0.32f * overallEnergy),
                NeonCyan.copy(alpha = 0.14f * overallEnergy),
                Color.Transparent
            ),
            center = Offset(width * 0.5f, centerY),
            radius = width * 0.65f
        ),
        radius = width * 0.65f,
        center = Offset(width * 0.5f, centerY)
    )

    // 2. Secondary Translucent Weaving Veil (Back Ribbon)
    val secTopPoints = ArrayList<Offset>(7)
    val secBottomPoints = ArrayList<Offset>(7)
    val secBaseThickness = 22.dp.toPx()
    val secMaxThickness = 56.dp.toPx()

    for (i in 0..6) {
        val x = width * nodeFractions[i]
        val energy = nodeEnergies[i]
        val idle = cos(phase * 0.9f + i * 0.75f).toFloat() * (idleFloat * 0.8f)
        val lift = -energy * maxLift * 0.65f
        val nodeCenterY = centerY + 16.dp.toPx() + idle + lift
        val thickness = secBaseThickness + energy * secMaxThickness

        secTopPoints.add(Offset(x, nodeCenterY - thickness * 0.5f))
        secBottomPoints.add(Offset(x, nodeCenterY + thickness * 0.5f))
    }

    val secRibbonPath = Path()
    appendSpline(secRibbonPath, secTopPoints, isReverse = false)
    secRibbonPath.lineTo(secBottomPoints.last().x, secBottomPoints.last().y)
    appendSpline(secRibbonPath, secBottomPoints, isReverse = true)
    secRibbonPath.close()

    val secBrush = Brush.horizontalGradient(
        colors = listOf(
            NeonCyan.copy(alpha = 0.30f),
            ElectricBlue.copy(alpha = 0.38f),
            DeepViolet.copy(alpha = 0.36f),
            VividMagenta.copy(alpha = 0.34f),
            WarmAmber.copy(alpha = 0.30f)
        ),
        startX = 0f,
        endX = width
    )
    drawPath(path = secRibbonPath, brush = secBrush)

    // 3. Primary Volumetric Aurora Ribbon (Main Ribbon)
    val topPoints = ArrayList<Offset>(7)
    val bottomPoints = ArrayList<Offset>(7)

    for (i in 0..6) {
        val x = width * nodeFractions[i]
        val energy = nodeEnergies[i]
        val idle = sin(phase + i * 0.85f).toFloat() * idleFloat
        val lift = -energy * maxLift
        val nodeCenterY = centerY + idle + lift
        val thickness = baseThickness + energy * maxThickness

        topPoints.add(Offset(x, nodeCenterY - thickness * 0.5f))
        bottomPoints.add(Offset(x, nodeCenterY + thickness * 0.5f))
    }

    val primaryRibbonPath = Path()
    appendSpline(primaryRibbonPath, topPoints, isReverse = false)
    primaryRibbonPath.lineTo(bottomPoints.last().x, bottomPoints.last().y)
    appendSpline(primaryRibbonPath, bottomPoints, isReverse = true)
    primaryRibbonPath.close()

    val primaryBrush = Brush.horizontalGradient(
        colors = listOf(
            NeonCyan.copy(alpha = 0.88f),
            ElectricBlue.copy(alpha = 0.82f),
            DeepViolet.copy(alpha = 0.90f),
            VividMagenta.copy(alpha = 0.86f),
            WarmAmber.copy(alpha = 0.84f)
        ),
        startX = 0f,
        endX = width
    )
    drawPath(path = primaryRibbonPath, brush = primaryBrush)

    // 4. Luminous Crest Highlight Filament (Top Edge Glow)
    val crestPath = Path()
    appendSpline(crestPath, topPoints, isReverse = false)
    drawPath(
        path = crestPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFE0F7FA).copy(alpha = 0.95f),
                Color(0xFF67E8F9),
                Color(0xFFF3E8FF),
                Color(0xFFFEF08A).copy(alpha = 0.95f)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )

    // 5. Floating Ambient Stardust Particles
    val particleCount = 14
    for (i in 0 until particleCount) {
        val fx = 0.06f + (i.toFloat() / (particleCount - 1)) * 0.88f
        val px = width * fx
        val pIdle = sin(phase * 0.65f + i * 1.35f).toFloat() * (height * 0.28f)
        val py = centerY + pIdle
        val pRadius = (2.2.dp.toPx() + (i % 3) * 1.2.dp.toPx()) * (1f + trebleEnergy * 0.5f)
        val pAlpha = (0.35f + (i % 3) * 0.18f + bassEnergy * 0.25f).coerceIn(0.15f, 0.95f)
        val pColor = if (i % 2 == 0) NeonCyan.copy(alpha = pAlpha) else HiResGold.copy(alpha = pAlpha)

        drawCircle(
            color = pColor,
            radius = pRadius,
            center = Offset(px, py)
        )
    }
}

/**
 * 2. Breathing Neon Halo: Organic celestial eclipse ring with multi-layer concentric glow.
 */
private fun DrawScope.drawNeonHalo(
    spectrum: FloatArray,
    isPlaying: Boolean,
    phase: Float,
    primaryColor: Color,
    accentColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = min(size.width, size.height) * 0.28f
    val maxDeltaR = baseRadius * 0.45f

    val bassMagnitude = if (isPlaying && spectrum.isNotEmpty()) {
        (0 until min(4, spectrum.size)).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    val trebleMagnitude = if (isPlaying && spectrum.size >= 16) {
        (12 until spectrum.size).map { spectrum[it] }.average().toFloat().coerceIn(0.04f, 1f)
    } else 0.04f

    // 1. Center pulsing core glow
    val coreRadius = baseRadius * (0.40f + bassMagnitude * 0.25f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                DeepViolet.copy(alpha = 0.40f + bassMagnitude * 0.35f),
                NeonCyan.copy(alpha = 0.18f),
                Color.Transparent
            ),
            center = center,
            radius = coreRadius * 2.2f
        ),
        radius = coreRadius * 2.2f,
        center = center
    )

    // 2. Inner concentric breathing ring
    drawCircle(
        color = NeonCyan.copy(alpha = 0.30f + bassMagnitude * 0.30f),
        radius = baseRadius * 0.68f + bassMagnitude * 12.dp.toPx(),
        center = center,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    // 3. Smooth closed cubic spline halo (16 control nodes around circle)
    val ringPointCount = 16
    val ringPoints = ArrayList<Offset>(ringPointCount)

    for (i in 0 until ringPointCount) {
        val angle = (2 * PI * i / ringPointCount + phase * 0.25).toFloat()
        val spectrumIdx = (i * spectrum.size / ringPointCount).coerceIn(0, (spectrum.size - 1).coerceAtLeast(0))
        val mag = if (isPlaying && spectrum.isNotEmpty()) spectrum[spectrumIdx].coerceIn(0.04f, 1f) else 0.04f
        val r = baseRadius + mag * maxDeltaR
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        ringPoints.add(Offset(x, y))
    }

    val haloPath = Path()
    appendClosedSpline(haloPath, ringPoints)

    val haloGradient = Brush.sweepGradient(
        colors = listOf(
            NeonCyan,
            DeepViolet,
            VividMagenta,
            WarmAmber,
            NeonCyan
        ),
        center = center
    )

    // Diffuse outer halo glow
    drawPath(
        path = haloPath,
        brush = haloGradient,
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.26f
    )

    // Crisp neon core halo
    drawPath(
        path = haloPath,
        brush = haloGradient,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )

    // 4. Outer Ethereal Shimmer Ring
    drawCircle(
        color = VividMagenta.copy(alpha = 0.22f + trebleMagnitude * 0.35f),
        radius = baseRadius * 1.32f + trebleMagnitude * 16.dp.toPx(),
        center = center,
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
    )

    // 5. Orbiting luminous stardust beads around peak angles
    for (i in 0 until 8) {
        val angle = (2 * PI * i / 8 + phase * 0.35).toFloat()
        val r = baseRadius * 1.15f + sin(phase + i).toFloat() * 10.dp.toPx()
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        val beadAlpha = (0.45f + trebleMagnitude * 0.45f).coerceIn(0.2f, 1f)
        drawCircle(
            color = if (i % 2 == 0) NeonCyan.copy(alpha = beadAlpha) else WarmAmber.copy(alpha = beadAlpha),
            radius = (3.dp.toPx() + trebleMagnitude * 2.dp.toPx()),
            center = Offset(x, y)
        )
    }
}

/**
 * 3. Floating Crystal Spectrum: Modern symmetrical crystal pillars with ambient glow and rounded glass styling.
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

    val usableWidth = totalWidth * 0.88f
    val barWidth = (usableWidth / barCount) * 0.52f
    val gap = (usableWidth - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
    val startX = (totalWidth - usableWidth) / 2f

    val pillarBrush = Brush.verticalGradient(
        colors = listOf(
            NeonCyan,
            DeepViolet,
            VividMagenta,
            WarmAmber
        ),
        startY = centerY - canvasHeight * 0.38f,
        endY = centerY + canvasHeight * 0.38f
    )

    for (i in 0 until barCount) {
        val magnitude = if (isPlaying && i < spectrum.size) {
            spectrum[i].coerceIn(0.04f, 1.0f)
        } else {
            0.04f
        }

        val halfHeight = (canvasHeight * 0.38f * magnitude).coerceAtLeast(4.dp.toPx())
        val x = startX + i * (barWidth + gap)
        val top = centerY - halfHeight
        val bottom = centerY + halfHeight

        // Background diffuse ambient glow behind active pillars
        if (magnitude > 0.15f) {
            val glowWidth = barWidth * 2.2f
            val glowTop = top - 6.dp.toPx()
            val glowBottom = bottom + 6.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.25f * magnitude),
                        VividMagenta.copy(alpha = 0.20f * magnitude),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(x - (glowWidth - barWidth) / 2f, glowTop),
                size = Size(glowWidth, glowBottom - glowTop),
                cornerRadius = CornerRadius(glowWidth / 2f, glowWidth / 2f)
            )
        }

        // Symmetrical rounded crystal pillar
        drawRoundRect(
            brush = pillarBrush,
            topLeft = Offset(x, top),
            size = Size(barWidth, bottom - top),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        // Floating luminous peak beads on top and bottom
        if (magnitude > 0.25f) {
            drawCircle(
                color = if (i % 2 == 0) NeonCyan else WarmAmber,
                radius = barWidth * 0.55f,
                center = Offset(x + barWidth / 2f, top - 5.dp.toPx())
            )
            drawCircle(
                color = VividMagenta.copy(alpha = 0.8f),
                radius = barWidth * 0.45f,
                center = Offset(x + barWidth / 2f, bottom + 5.dp.toPx())
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

    // Ambient frosted backplate tube
    val barWidth = totalWidth * 0.90f
    val barHeight = 48.dp.toPx()
    drawRoundRect(
        color = Color(0x14FFFFFF),
        topLeft = Offset(centerX - barWidth / 2f, centerY - barHeight / 2f),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
    )

    // Center ambient backlight glow
    val centerEnergy = if (isPlaying && spectrum.isNotEmpty()) spectrum[0].coerceIn(0.04f, 1f) else 0.04f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                NeonCyan.copy(alpha = 0.35f * centerEnergy),
                DeepViolet.copy(alpha = 0.15f * centerEnergy),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = totalWidth * 0.35f
        ),
        radius = totalWidth * 0.35f,
        center = Offset(centerX, centerY)
    )

    // 16 segments expanding to left and 16 to right
    val halfSegments = 16
    val usableHalfWidth = (barWidth - 24.dp.toPx()) / 2f
    val segmentWidth = usableHalfWidth / halfSegments
    val gap = 3.dp.toPx()
    val cellWidth = segmentWidth - gap

    val stripGradient = Brush.horizontalGradient(
        colors = listOf(
            WarmAmber,
            VividMagenta,
            DeepViolet,
            NeonCyan,
            DeepViolet,
            VividMagenta,
            WarmAmber
        ),
        startX = centerX - usableHalfWidth,
        endX = centerX + usableHalfWidth
    )

    for (i in 0 until halfSegments) {
        val spectrumIdx = (i * spectrum.size / halfSegments).coerceIn(0, (spectrum.size - 1).coerceAtLeast(0))
        val mag = if (isPlaying && spectrum.isNotEmpty()) spectrum[spectrumIdx].coerceIn(0.04f, 1f) else 0.04f
        val cellHeight = (canvasHeight * 0.32f * mag).coerceAtLeast(6.dp.toPx())

        // Right side
        val rightX = centerX + 8.dp.toPx() + i * segmentWidth
        drawRoundRect(
            brush = stripGradient,
            topLeft = Offset(rightX, centerY - cellHeight / 2f),
            size = Size(cellWidth, cellHeight),
            cornerRadius = CornerRadius(cellWidth / 2f, cellWidth / 2f)
        )

        // Left side (symmetric)
        val leftX = centerX - 8.dp.toPx() - (i + 1) * segmentWidth + gap
        drawRoundRect(
            brush = stripGradient,
            topLeft = Offset(leftX, centerY - cellHeight / 2f),
            size = Size(cellWidth, cellHeight),
            cornerRadius = CornerRadius(cellWidth / 2f, cellWidth / 2f)
        )
    }

    // Central glowing anchor diode
    drawCircle(
        color = NeonCyan,
        radius = (6.dp.toPx() + centerEnergy * 6.dp.toPx()),
        center = Offset(centerX, centerY)
    )
    drawCircle(
        color = Color.White,
        radius = 3.dp.toPx(),
        center = Offset(centerX, centerY)
    )
}
