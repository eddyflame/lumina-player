package org.lumina.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.lumina.player.R
import org.lumina.player.core.model.Song
import org.lumina.player.ui.theme.HiResGold
import kotlin.math.abs

/**
 * Floating bottom Mini Player component supporting smooth horizontal swipe transitions
 * with visual continuity between tracks and expandable full-screen player access.
 */
@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    durationMs: Long,
    currentPositionMs: Long = 0L,
    currentPositionProvider: (() -> Long)? = null,
    artwork: android.graphics.Bitmap? = null,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        if (currentSong == null) return@AnimatedVisibility

        val coroutineScope = rememberCoroutineScope()
        val offsetX = remember { Animatable(0f) }
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 54.dp.toPx() }
        var playerWidthPx by remember { mutableFloatStateOf(0f) }
        var isTransitioning by remember { mutableStateOf(false) }

        // Trigger two-phase continuous sliding track transition
        fun triggerTrackTransition(isNext: Boolean) {
            if (isTransitioning) return
            isTransitioning = true
            coroutineScope.launch {
                val width = if (playerWidthPx > 0f) playerWidthPx else with(density) { 360.dp.toPx() }
                val exitTarget = if (isNext) -width else width
                val enterSource = if (isNext) width else -width

                // Phase 1: Slide current player off-screen in swipe direction
                offsetX.animateTo(
                    targetValue = exitTarget,
                    animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
                )

                // Phase 2: Switch track while completely out of viewport
                if (isNext) onSkipNext() else onSkipPrevious()

                // Phase 3: Snap instantly to opposite off-screen edge
                offsetX.snapTo(enterSource)

                // Phase 4: Glide new player smoothly into center with natural spring bounce
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                isTransitioning = false
            }
        }

        val effectiveWidth = if (playerWidthPx > 0f) playerWidthPx else 1000f
        val normalizedOffset = (abs(offsetX.value) / effectiveWidth).coerceIn(0f, 1f)
        val cardAlpha = (1f - normalizedOffset * 0.35f).coerceIn(0.5f, 1f)
        val cardScale = (1f - normalizedOffset * 0.05f).coerceIn(0.92f, 1f)

        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { playerWidthPx = it.width.toFloat() }
                .graphicsLayer {
                    translationX = offsetX.value
                    alpha = cardAlpha
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isDrag = false
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!isDrag) {
                                    // Tap without dragging: open full player
                                    if (!change.isConsumed) {
                                        onClick()
                                    }
                                } else {
                                    // Released after drag gesture: check swipe threshold
                                    val current = offsetX.value
                                    if (current < -swipeThresholdPx) {
                                        triggerTrackTransition(isNext = true)
                                    } else if (current > swipeThresholdPx) {
                                        triggerTrackTransition(isNext = false)
                                    } else {
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                }
                                break
                            }

                            val dragX = change.position.x - down.position.x
                            val dragY = change.position.y - down.position.y

                            if (!isDrag && (abs(dragX) > touchSlop || abs(dragY) > touchSlop)) {
                                if (abs(dragX) > abs(dragY)) {
                                    isDrag = true
                                } else {
                                    // Vertical dominant gesture: abort horizontal swipe
                                    break
                                }
                            }

                            if (isDrag) {
                                change.consume()
                                val delta = change.position.x - change.previousPosition.x
                                coroutineScope.launch {
                                    offsetX.snapTo(offsetX.value + delta)
                                }
                            }
                        }

                        if (isDrag && !isTransitioning && offsetX.value != 0f) {
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top thin progress indicator
                LinearProgressIndicator(
                    progress = {
                        val pos = currentPositionProvider?.invoke() ?: currentPositionMs
                        if (durationMs > 0) (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album artwork thumbnail container
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artwork != null) {
                            Image(
                                bitmap = artwork.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Song title, artist, album, and Hi-Res badges
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            currentSong.hiResBadgeText?.let { badge ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = HiResGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HiResGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${currentSong.displayArtist} · ${currentSong.displayAlbum}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Playback control buttons
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.previous),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.next),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.queue),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
