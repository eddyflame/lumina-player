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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import org.lumina.player.R
import org.lumina.player.ui.theme.LuminaAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Visualizer animation style enum (maintained for backward compatibility).
 */
enum class VisualizerStyle(val displayNameRes: Int) {
    AURORA_WAVES(R.string.visualizer_aurora)
}

/**
 * High-performance, single signature real-time audio rhythm visualizer.
 * Matches the design mockup: multi-layer translucent aurora silk ribbons with luminous crest filaments
 * and gentle organic breathing.
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
    // Serene, continuous phase angle for organic ribbon breathing
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_phase")
    val phaseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_drift"
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
            // Read frameTick to trigger 60/120Hz continuous drawing
            val _tick = frameTick

            // Exponential fluid damping towards target spectrum on each rendered frame (eliminates all jitter)
            val lerpFactor = 0.07f
            for (i in smoothedSpectrum.indices) {
                val target = if (isPlaying && i < spectrumData.size) spectrumData[i] else 0.04f
                smoothedSpectrum[i] += (target - smoothedSpectrum[i]) * lerpFactor
            }

            drawAuroraSilkRibbons(
                spectrum = smoothedSpectrum,
                isPlaying = isPlaying,
                phase = phaseAngle
            )
        }
    }
}

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
 * Signature Mockup Effect: 3 interlaced translucent silk ribbons (Amber, Purple, Cyan)
 * with glowing crest filaments and atmospheric backdrop blooms.
 */
private fun DrawScope.drawAuroraSilkRibbons(
    spectrum: FloatArray,
    isPlaying: Boolean,
    phase: Float
) {
    val width = size.width
    val height = size.height
    val centerY = height * 0.50f

    // 11 horizontal anchor nodes across the screen width (fixed X positions, zero crawling)
    val nodeCount = 11
    val nodeX = FloatArray(nodeCount) { i -> (i / 10f) * width }

    // Window function that smoothly tapers ribbons to 0 at the screen borders (x=0 and x=W)
    val window = FloatArray(nodeCount) { i ->
        val frac = i / 10f
        Math.pow(sin(frac * PI).coerceAtLeast(0.0), 0.85).toFloat()
    }

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

    // 1. Atmospheric Ambient Blooms (matching the mockup backdrop)
    // Upper-right deep violet/magenta atmospheric bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF581C87).copy(alpha = 0.32f * (0.5f + midEnergy * 0.5f)),
                Color(0xFF1E1B4B).copy(alpha = 0.12f),
                Color.Transparent
            ),
            center = Offset(width * 0.72f, centerY - 35.dp.toPx()),
            radius = width * 0.48f
        ),
        radius = width * 0.48f,
        center = Offset(width * 0.72f, centerY - 35.dp.toPx())
    )

    // Lower-left deep teal/cyan atmospheric bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF0E7490).copy(alpha = 0.28f * (0.5f + bassEnergy * 0.5f)),
                Color(0xFF082F49).copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = Offset(width * 0.30f, centerY + 28.dp.toPx()),
            radius = width * 0.44f
        ),
        radius = width * 0.44f,
        center = Offset(width * 0.30f, centerY + 28.dp.toPx())
    )

    // 2. RIBBON 1: Golden-Amber Under-Ribbon (Bottom loop dipping on the left, reactive to bass)
    val baseAmberOffsetsDp = floatArrayOf(0f, 8f, 26f, 38f, 30f, 12f, -6f, -16f, -10f, -4f, 0f)
    val amberTopPts = ArrayList<Offset>(nodeCount)
    val amberBottomPts = ArrayList<Offset>(nodeCount)

    for (i in 0 until nodeCount) {
        val w = window[i]
        val dynamicThickness = (14.dp.toPx() + bassEnergy * 8.dp.toPx()) * w
        val audioDip = (bassEnergy * 14.dp.toPx()) * w
        val breath = sin(phase * 0.9f + i * 0.4f).toFloat() * 3.dp.toPx() * w
        val yMid = centerY + (baseAmberOffsetsDp[i].dp.toPx() + audioDip + breath) * w

        amberTopPts.add(Offset(nodeX[i], yMid - dynamicThickness * 0.45f))
        amberBottomPts.add(Offset(nodeX[i], yMid + dynamicThickness * 0.55f))
    }

    val amberRibbonPath = Path()
    appendSpline(amberRibbonPath, amberTopPts, isReverse = false)
    amberRibbonPath.lineTo(amberBottomPts.last().x, amberBottomPts.last().y)
    appendSpline(amberRibbonPath, amberBottomPts, isReverse = true)
    amberRibbonPath.close()

    drawPath(
        path = amberRibbonPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x00F59E0B),
                Color(0xFFF59E0B).copy(alpha = 0.42f),
                Color(0xFFFBBF24).copy(alpha = 0.40f),
                Color(0xFFD97706).copy(alpha = 0.35f),
                Color(0xFFA855F7).copy(alpha = 0.22f),
                Color.Transparent
            ),
            startX = 0f,
            endX = width
        )
    )

    // Golden glowing bottom filament
    val amberFilamentPath = Path()
    appendSpline(amberFilamentPath, amberBottomPts, isReverse = false)
    drawPath(
        path = amberFilamentPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x00FBBF24),
                Color(0xFFFEF08A),
                Color(0xFFFBBF24),
                Color(0xFFF59E0B),
                Color(0x00A855F7)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.25f
    )
    drawPath(
        path = amberFilamentPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x00FBBF24),
                Color(0xFFFFFBEB),
                Color(0xFFFEF08A),
                Color(0xFFFBBF24),
                Color(0x00A855F7)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.92f
    )

    // 3. RIBBON 2: Purple-Magenta Silk Ribbon (Middle layer, rising on the right, reactive to mids)
    val basePurpleOffsetsDp = floatArrayOf(0f, 4f, 14f, 22f, 10f, -14f, -34f, -44f, -28f, -10f, 0f)
    val purpleTopPts = ArrayList<Offset>(nodeCount)
    val purpleBottomPts = ArrayList<Offset>(nodeCount)

    for (i in 0 until nodeCount) {
        val w = window[i]
        val dynamicThickness = (18.dp.toPx() + midEnergy * 10.dp.toPx()) * w
        val audioRise = (-midEnergy * 16.dp.toPx()) * w
        val breath = cos(phase * 0.85f + i * 0.45f).toFloat() * 3.5.dp.toPx() * w
        val yMid = centerY + (basePurpleOffsetsDp[i].dp.toPx() + audioRise + breath) * w

        purpleTopPts.add(Offset(nodeX[i], yMid - dynamicThickness * 0.5f))
        purpleBottomPts.add(Offset(nodeX[i], yMid + dynamicThickness * 0.5f))
    }

    val purpleRibbonPath = Path()
    appendSpline(purpleRibbonPath, purpleTopPts, isReverse = false)
    purpleRibbonPath.lineTo(purpleBottomPts.last().x, purpleBottomPts.last().y)
    appendSpline(purpleRibbonPath, purpleBottomPts, isReverse = true)
    purpleRibbonPath.close()

    drawPath(
        path = purpleRibbonPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x004F46E5),
                Color(0xFF4F46E5).copy(alpha = 0.32f),
                Color(0xFF8B5CF6).copy(alpha = 0.46f),
                Color(0xFFC084FC).copy(alpha = 0.48f),
                Color(0xFFD946EF).copy(alpha = 0.42f),
                Color(0xFFF472B6).copy(alpha = 0.26f),
                Color.Transparent
            ),
            startX = 0f,
            endX = width
        )
    )

    // Purple-Magenta glowing crest filament
    val purpleFilamentPath = Path()
    appendSpline(purpleFilamentPath, purpleTopPts, isReverse = false)
    drawPath(
        path = purpleFilamentPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x008B5CF6),
                Color(0xFFDDD6FE),
                Color(0xFFE879F9),
                Color(0xFFF472B6),
                Color(0x00F472B6)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.28f
    )
    drawPath(
        path = purpleFilamentPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x008B5CF6),
                Color(0xFFFDF4FF),
                Color(0xFFE879F9),
                Color(0xFFF472B6),
                Color(0x00F472B6)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.94f
    )

    // 4. RIBBON 3: Electric Cyan-Teal Luminous Ribbon (Foreground, high arching cyan veil, reactive to highs)
    val baseCyanOffsetsDp = floatArrayOf(0f, 2f, 8f, 10f, -4f, -22f, -38f, -34f, -20f, -6f, 0f)
    val cyanTopPts = ArrayList<Offset>(nodeCount)
    val cyanBottomPts = ArrayList<Offset>(nodeCount)

    for (i in 0 until nodeCount) {
        val w = window[i]
        val dynamicThickness = (22.dp.toPx() + trebleEnergy * 12.dp.toPx()) * w
        val audioRise = (-trebleEnergy * 14.dp.toPx()) * w
        val breath = sin(phase * 1.05f + i * 0.5f).toFloat() * 4.dp.toPx() * w
        val yMid = centerY + (baseCyanOffsetsDp[i].dp.toPx() + audioRise + breath) * w

        cyanTopPts.add(Offset(nodeX[i], yMid - dynamicThickness * 0.55f))
        cyanBottomPts.add(Offset(nodeX[i], yMid + dynamicThickness * 0.45f))
    }

    val cyanRibbonPath = Path()
    appendSpline(cyanRibbonPath, cyanTopPts, isReverse = false)
    cyanRibbonPath.lineTo(cyanBottomPts.last().x, cyanBottomPts.last().y)
    appendSpline(cyanRibbonPath, cyanBottomPts, isReverse = true)
    cyanRibbonPath.close()

    drawPath(
        path = cyanRibbonPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x0000F5D4),
                Color(0xFF00F5D4).copy(alpha = 0.48f),
                Color(0xFF06B6D4).copy(alpha = 0.46f),
                Color(0xFF38BDF8).copy(alpha = 0.42f),
                Color(0xFF6366F1).copy(alpha = 0.35f),
                Color(0xFF818CF8).copy(alpha = 0.22f),
                Color.Transparent
            ),
            startX = 0f,
            endX = width
        )
    )

    // Cyan glowing crest filament
    val cyanFilamentPath = Path()
    appendSpline(cyanFilamentPath, cyanTopPts, isReverse = false)
    drawPath(
        path = cyanFilamentPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x0000F5D4),
                Color(0xFFFFFFFF),
                Color(0xFF67E8F9),
                Color(0xFFA5F3FC),
                Color(0x006366F1)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.32f
    )
    drawPath(
        path = cyanFilamentPath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0x0000F5D4),
                Color(0xFFFFFFFF),
                Color(0xFF67E8F9),
                Color(0xFFA5F3FC),
                Color(0x006366F1)
            ),
            startX = 0f,
            endX = width
        ),
        style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
        alpha = 0.98f
    )
}
