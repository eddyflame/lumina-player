package org.lumina.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.PlayMode
import org.lumina.player.core.model.Song
import org.lumina.player.ui.components.EqualizerDialog
import org.lumina.player.ui.components.QueueBottomSheet
import org.lumina.player.ui.screens.FullPlayerScreen
import org.lumina.player.ui.screens.MainLibraryScreen
import org.lumina.player.ui.settings.SettingsBottomSheet
import org.lumina.player.ui.theme.LuminaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LuminaPlayerApp(
                activity = this,
                intent = intent
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAudioIntent(intent)
    }

    private fun handleAudioIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        val app = LuminaApp.instance
        val uriString = uri.toString()
        val title = uri.lastPathSegment ?: "External Audio"
        val tempSong = Song(
            id = uriString.hashCode().toLong(),
            uri = uriString,
            title = title,
            artist = "External Audio",
            album = "Shared",
            audioFormat = AudioFormatType.fromExtension(title)
        )
        app.playerController.playSong(tempSong)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminaPlayerApp(
    activity: MainActivity,
    intent: Intent?
) {
    val app = LuminaApp.instance
    val coroutineScope = rememberCoroutineScope()

    val themeSettings by app.themePreferences.themeSettings.collectAsState()
    val audioSettings by app.audioPreferences.settings.collectAsState()

    val songs by app.repository.songsFlow.collectAsState(initial = emptyList())
    val albums by app.repository.albumsFlow.collectAsState(initial = emptyList())
    val artists by app.repository.artistsFlow.collectAsState(initial = emptyList())
    val folders by app.repository.foldersFlow.collectAsState(initial = emptyList())
    val playlists by app.repository.playlistsFlow.collectAsState(initial = emptyList())
    val favorites by app.repository.favoritesFlow.collectAsState(initial = emptyList())
    val recentlyPlayed by app.repository.recentlyPlayedFlow.collectAsState(initial = emptyList())
    val mostPlayed by app.repository.mostPlayedFlow.collectAsState(initial = emptyList())

    val currentSong by app.playerController.currentSong.collectAsState()
    val isPlaying by app.playerController.isPlaying.collectAsState()
    val currentPositionMs by app.playerController.currentPositionMs.collectAsState()
    val durationMs by app.playerController.durationMs.collectAsState()
    val queue by app.playerController.queue.collectAsState()
    val queueIndex by app.playerController.queueIndex.collectAsState()
    val playMode by app.playerController.playMode.collectAsState()
    val lyrics by app.playerController.currentLyrics.collectAsState()
    val lyricsOffsetMs by app.playerController.lyricsOffsetMs.collectAsState()
    val techInfo by app.playerController.currentTechInfo.collectAsState()
    val sleepTimerRemainingSeconds by app.playerController.sleepTimerRemainingSeconds.collectAsState()
    val playbackSpeed by app.playerController.playbackSpeed.collectAsState()
    val currentArtwork by app.playerController.currentArtwork.collectAsState()

    var isFullPlayerOpen by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isEqualizerOpen by remember { mutableStateOf(false) }

    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Audio & Lock Screen permission request launcher
    val permissionsToRequest = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            grants[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioGranted) {
            coroutineScope.launch {
                app.repository.rescan(audioSettings.filterShortAudio)
            }
        }
    }

    // Initial media library scan and permission check
    LaunchedEffect(Unit) {
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            app.repository.rescan(audioSettings.filterShortAudio)
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        // Handle audio opened externally via VIEW intent
        intent?.data?.let { uri ->
            val uriString = uri.toString()
            val title = uri.lastPathSegment ?: "External Audio"
            val tempSong = Song(
                id = uriString.hashCode().toLong(),
                uri = uriString,
                title = title,
                artist = "External Audio",
                album = "Shared",
                audioFormat = AudioFormatType.fromExtension(title)
            )
            app.playerController.playSong(tempSong)
            isFullPlayerOpen = true
        }
    }

    // When library scan completes, restore last played song, queue, and playback position
    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            app.playerController.restoreLastPlaybackState(songs)
        }
    }

    // Intercept back button: collapse full-screen player if open
    BackHandler(enabled = isFullPlayerOpen) {
        isFullPlayerOpen = false
    }

    LuminaTheme(
        themeMode = themeSettings.themeMode,
        darkThemeStyle = themeSettings.darkThemeStyle,
        dynamicColor = themeSettings.dynamicColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main music library screen
            MainLibraryScreen(
                songs = songs,
                albums = albums,
                artists = artists,
                folders = folders,
                playlists = playlists,
                favorites = favorites,
                recentlyPlayed = recentlyPlayed,
                mostPlayed = mostPlayed,
                currentSong = currentSong,
                isPlaying = isPlaying,
                durationMs = durationMs,
                currentPositionProvider = { app.playerController.currentPositionMs.value },
                artwork = currentArtwork,
                onSongClick = { song, songList ->
                    app.playerController.playSong(song, songList)
                },
                onTogglePlayPause = { app.playerController.togglePlayPause() },
                onSkipNext = { app.playerController.skipNext() },
                onSkipPrevious = { app.playerController.skipPrevious() },
                onOpenQueue = { isQueueOpen = true },
                onOpenMiniPlayer = { isFullPlayerOpen = true },
                onOpenSettings = { isSettingsOpen = true },
                onOpenEqualizer = { isEqualizerOpen = true },
                onShuffleAll = { songsToShuffle ->
                    if (songsToShuffle.isNotEmpty()) {
                        val shuffled = songsToShuffle.shuffled()
                        app.playerController.setPlayMode(PlayMode.SHUFFLE)
                        app.playerController.playSong(shuffled.first(), shuffled)
                    }
                },
                onCreatePlaylist = { name -> app.repository.createPlaylist(name) },
                onRenamePlaylist = { id, newName -> app.repository.renamePlaylist(id, newName) },
                onPlaylistClick = { playlist ->
                    coroutineScope.launch {
                        val pSongs = app.repository.getPlaylistSongs(playlist.id)
                        if (pSongs.isNotEmpty()) {
                            app.playerController.playSong(pSongs.first(), pSongs)
                        }
                    }
                },
                onDeletePlaylist = { playlist ->
                    app.repository.deletePlaylist(playlist.id)
                },
                onGetPlaylistSongs = { id -> app.repository.getPlaylistSongs(id) },
                onAddSongToPlaylist = { id, uri -> app.repository.addSongToPlaylist(id, uri) },
                onAddSongsToPlaylist = { id, uris -> app.repository.addSongsToPlaylist(id, uris) },
                onRemoveSongFromPlaylist = { id, uri -> app.repository.removeSongFromPlaylist(id, uri) },
                onRemoveSongsFromPlaylist = { id, uris -> app.repository.removeSongsFromPlaylist(id, uris) },
                onToggleFavorite = { uri -> app.playerController.toggleFavorite(uri) },
                onSetFavorites = { uris, isFav -> app.playerController.setFavorites(uris, isFav) },
                onResetPlayCount = { uris -> app.repository.resetPlayCount(uris) }
            )

            // Fullscreen vinyl & lyrics player screen
            AnimatedVisibility(
                visible = isFullPlayerOpen && currentSong != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.fillMaxSize()
            ) {
                currentSong?.let { song ->
                    FullPlayerScreen(
                        currentSong = song,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        playMode = playMode,
                        lyrics = lyrics,
                        lyricsOffsetMs = lyricsOffsetMs,
                        techInfo = techInfo,
                        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                        playbackSpeed = playbackSpeed,
                        dspController = app.playerController.getDspController(),
                        artwork = currentArtwork,
                        onTogglePlayPause = { app.playerController.togglePlayPause() },
                        onSkipNext = { app.playerController.skipNext() },
                        onSkipPrevious = { app.playerController.skipPrevious() },
                        onSeekTo = { pos -> app.playerController.seekTo(pos) },
                        onToggleFavorite = { app.playerController.toggleFavorite(song.uri) },
                        onCyclePlayMode = {
                            val nextMode = playMode.next()
                            app.playerController.setPlayMode(nextMode)
                        },
                        onOpenQueue = { isQueueOpen = true },
                        onAdjustLyricsOffset = { delta -> app.playerController.adjustLyricsOffset(delta) },
                        onResetLyricsOffset = { app.playerController.resetLyricsOffset() },
                        onSetSleepTimer = { min -> app.playerController.setSleepTimer(min) },
                        onSetSleepTimerEndOfTrack = { app.playerController.setSleepTimerEndOfTrack() },
                        onCancelSleepTimer = { app.playerController.cancelSleepTimer() },
                        onSetPlaybackSpeed = { spd -> app.playerController.setPlaybackSpeed(spd) },
                        onSaveTags = { title, artist, album, year, genre ->
                            app.repository.updateSongTags(song.uri, title, artist, album, year, genre)
                        },
                        onCollapse = { isFullPlayerOpen = false }
                    )
                }
            }

            // Now playing queue bottom sheet
            if (isQueueOpen) {
                QueueBottomSheet(
                    queue = queue,
                    currentIndex = queueIndex,
                    sheetState = queueSheetState,
                    onDismiss = { isQueueOpen = false },
                    onSongClick = { index, _ ->
                        app.playerController.playQueueIndex(index)
                    },
                    onRemoveSong = { idx -> app.playerController.removeFromQueue(idx) },
                    onClearQueue = {
                        app.playerController.clearQueue()
                        isQueueOpen = false
                        isFullPlayerOpen = false
                    }
                )
            }

            // Settings bottom sheet
            if (isSettingsOpen) {
                SettingsBottomSheet(
                    themeSettings = themeSettings,
                    filterShortAudio = audioSettings.filterShortAudio,
                    sheetState = settingsSheetState,
                    onDismiss = { isSettingsOpen = false },
                    onSetThemeMode = { mode -> app.themePreferences.setThemeMode(mode) },
                    onSetDarkThemeStyle = { style -> app.themePreferences.setDarkThemeStyle(style) },
                    onSetDynamicColor = { dyn -> app.themePreferences.setDynamicColor(dyn) },
                    onSetFilterShortAudio = { filter ->
                        app.audioPreferences.setFilterShortAudio(filter)
                        coroutineScope.launch {
                            app.repository.rescan(filter)
                        }
                    },
                    onRescanLibrary = {
                        coroutineScope.launch {
                            app.repository.rescan(audioSettings.filterShortAudio)
                        }
                    },
                    onOpenEqualizer = { isEqualizerOpen = true }
                )
            }

            // Equalizer and DSP audio effects dialog
            if (isEqualizerOpen) {
                EqualizerDialog(
                    dspController = app.playerController.getDspController(),
                    playbackSpeed = playbackSpeed,
                    onSetPlaybackSpeed = { app.playerController.setPlaybackSpeed(it) },
                    onDismiss = { isEqualizerOpen = false }
                )
            }
        }
    }
}
