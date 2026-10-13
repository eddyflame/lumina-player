package org.lumina.player.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lumina.player.R
import org.lumina.player.core.lyrics.LyricLine
import org.lumina.player.core.model.AudioTechnicalInfo
import org.lumina.player.core.model.PlayMode
import org.lumina.player.core.model.Song
import org.lumina.player.ui.components.AudioInfoDialog
import org.lumina.player.ui.components.AudioVisualizer
import org.lumina.player.ui.components.EqualizerDialog
import org.lumina.player.ui.components.LyricsView
import org.lumina.player.ui.components.SleepTimerDialog
import org.lumina.player.ui.components.TagEditorDialog
import org.lumina.player.ui.theme.HiResGold
import org.lumina.player.ui.theme.LuminaAccent
import org.lumina.player.ui.theme.LuminaPrimary

/**
 * Display modes for the central artistic viewport of the full player.
 */
enum class PlayerArtViewMode {
    VINYL_DISC,
    LYRICS,
    VISUALIZER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    currentSong: Song,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playMode: PlayMode,
    lyrics: List<LyricLine>,
    lyricsOffsetMs: Long,
    techInfo: AudioTechnicalInfo,
    sleepTimerRemainingSeconds: Long?,
    playbackSpeed: Float,
    dspController: org.lumina.player.core.dsp.AudioDspController?,
    artwork: android.graphics.Bitmap? = null,
    visualizerSpectrum: FloatArray = remember { FloatArray(24) { 0.04f } },
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSaveTags: (String, String, String, Int, String) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(PlayerArtViewMode.VINYL_DISC) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAudioInfoDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showTagEditorDialog by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPositionMs by remember { mutableFloatStateOf(0f) }

    val displayPosition = if (isUserSeeking) userSeekPositionMs.toLong() else currentPositionMs
    val progressFraction = if (durationMs > 0) (displayPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Extract dynamic dominant artwork palette color asynchronously off the UI thread
    var dominantColor by remember { mutableStateOf(LuminaPrimary) }
    LaunchedEffect(artwork) {
        if (artwork != null) {
            withContext(Dispatchers.Default) {
                val palette = androidx.palette.graphics.Palette.from(artwork).generate()
                val rgb = palette.getVibrantColor(palette.getDominantColor(0xFF0284C7.toInt()))
                dominantColor = Color(rgb)
            }
        } else {
            dominantColor = LuminaPrimary
        }
    }

    // Continuous smooth vinyl turntable rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_rotate"
    )

    // Ambient dynamic gradient background with soft radial diffuse
    val ambientGradient = Brush.verticalGradient(
        colors = listOf(
            dominantColor.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* Consume background touch events */ },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 6.dp)
            ) {
                // Top navigation bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Top center playback title & Hi-Res audio badge
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.now_playing),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        currentSong.hiResBadgeText?.let { badge ->
                            Surface(
                                color = HiResGold.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HiResGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showEqualizerDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.equalizer)
                            )
                        }

                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.audio_info)) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showAudioInfoDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sleep_timer)) },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showSleepTimerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tag_editor)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showTagEditorDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle artistic content area (Vinyl / Lyrics / Visualizer)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = viewMode,
                        animationSpec = tween(350),
                        label = "art_mode_crossfade"
                    ) { mode ->
                        when (mode) {
                            PlayerArtViewMode.VINYL_DISC -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewMode = PlayerArtViewMode.LYRICS
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Vinyl turntable disc with authentic concentric grooves & sheen
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.78f)
                                            .aspectRatio(1f)
                                            .shadow(24.dp, shape = CircleShape)
                                            .clip(CircleShape)
                                            .graphicsLayer {
                                                rotationZ = if (isPlaying) vinylRotation else 0f
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Draw vinyl micro-grooves and light reflection
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val center = Offset(size.width / 2, size.height / 2)
                                            val radius = size.width / 2

                                             // Vinyl base plate
                                            drawCircle(color = Color(0xFF0F1115), radius = radius)

                                            // Concentric sound groove lines
                                            val grooveRadii = floatArrayOf(0.92f, 0.86f, 0.80f, 0.74f, 0.68f)
                                            for (factor in grooveRadii) {
                                                drawCircle(
                                                    color = Color(0x1AFFFFFF),
                                                    radius = radius * factor,
                                                    style = Stroke(width = 1.2f.dp.toPx())
                                                )
                                            }

                                            // Radial specular light sheen
                                            drawCircle(
                                                brush = Brush.sweepGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x22FFFFFF),
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        Color(0x22FFFFFF),
                                                        Color.Transparent
                                                    ),
                                                    center = center
                                                ),
                                                radius = radius
                                            )

                                            // Outer rim beveled edge
                                            drawCircle(
                                                color = Color(0x33FFFFFF),
                                                radius = radius,
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                        }

                                        // Center album label
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth(0.55f)
                                                .aspectRatio(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
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
                                                        modifier = Modifier.size(52.dp)
                                                    )
                                                }

                                                // Center metallic spindle hole
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFF0F172A),
                                                    border = BorderStroke(2.dp, Color(0xFF94A3B8)),
                                                    modifier = Modifier.size(20.dp)
                                                ) {}
                                            }
                                        }
                                    }
                                }
                            }
                            PlayerArtViewMode.LYRICS -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewMode = PlayerArtViewMode.VISUALIZER
                                        }
                                ) {
                                    LyricsView(
                                        lyrics = lyrics,
                                        currentPositionMs = currentPositionMs,
                                        offsetMs = lyricsOffsetMs,
                                        onSeekTo = onSeekTo,
                                        onAdjustOffset = onAdjustLyricsOffset,
                                        onResetOffset = onResetLyricsOffset
                                    )
                                }
                            }
                            PlayerArtViewMode.VISUALIZER -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AudioVisualizer(
                                        isPlaying = isPlaying,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Subtitle mode switcher pill (Vinyl / Lyrics / Visualizer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = { viewMode = PlayerArtViewMode.VINYL_DISC },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = "Vinyl",
                                    tint = if (viewMode == PlayerArtViewMode.VINYL_DISC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewMode = PlayerArtViewMode.LYRICS },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = "Lyrics",
                                    tint = if (viewMode == PlayerArtViewMode.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewMode = PlayerArtViewMode.VISUALIZER },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = stringResource(R.string.visualizer),
                                    tint = if (viewMode == PlayerArtViewMode.VISUALIZER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Track title, artist, and favorite button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentSong.displayArtist} · ${currentSong.displayAlbum}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (currentSong.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentSong.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Playback progress slider and time indicator
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            isUserSeeking = true
                            userSeekPositionMs = fraction * durationMs
                        },
                        onValueChangeFinished = {
                            isUserSeeking = false
                            onSeekTo(userSeekPositionMs.toLong())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val posSec = displayPosition / 1000
                        val durSec = durationMs / 1000
                        val posFormatted = String.format("%02d:%02d", posSec / 60, posSec % 60)
                        val durFormatted = String.format("%02d:%02d", durSec / 60, durSec % 60)

                        Text(text = posFormatted, style = MaterialTheme.typography.labelSmall)
                        Text(text = durFormatted, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Playback control buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback repeat/shuffle cycle mode
                    IconButton(onClick = onCyclePlayMode) {
                        val icon = when (playMode) {
                            PlayMode.SEQUENTIAL, PlayMode.REPEAT_ALL -> Icons.Default.Repeat
                            PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        }
                        val tint = if (playMode != PlayMode.SEQUENTIAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(imageVector = icon, contentDescription = "Play Mode", tint = tint)
                    }

                    // Previous song
                    IconButton(onClick = onSkipPrevious) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause prominent circular button
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next song
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Queue bottom sheet trigger
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Modal dialogs and sheets
    if (showAudioInfoDialog) {
        AudioInfoDialog(
            song = currentSong,
            techInfo = techInfo,
            onDismiss = { showAudioInfoDialog = false }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            dspController = dspController,
            playbackSpeed = playbackSpeed,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingSeconds = sleepTimerRemainingSeconds,
            onSetTimer = onSetSleepTimer,
            onSetEndOfTrack = onSetSleepTimerEndOfTrack,
            onCancelTimer = onCancelSleepTimer,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showTagEditorDialog) {
        TagEditorDialog(
            song = currentSong,
            onSave = { title, artist, album, year, genre ->
                onSaveTags(title, artist, album, year, genre)
            },
            onDismiss = { showTagEditorDialog = false }
        )
    }
}
