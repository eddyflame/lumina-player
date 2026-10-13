package org.lumina.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.lumina.player.R
import org.lumina.player.core.model.Album
import org.lumina.player.core.model.Artist
import org.lumina.player.core.model.MusicFolder
import org.lumina.player.core.model.Playlist
import org.lumina.player.core.model.Song
import org.lumina.player.ui.components.AddToPlaylistDialog
import org.lumina.player.ui.components.MiniPlayer
import org.lumina.player.ui.components.PlaylistDetailView
import org.lumina.player.ui.theme.HiResGold
import java.io.File

enum class SortMode {
    TITLE, ARTIST, DATE, DURATION
}

data class DetailViewState(
    val title: String,
    val subtitle: String? = null,
    val isCustomPlaylist: Boolean = false,
    val customPlaylistId: Long? = null,
    val songs: List<Song> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLibraryScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    folders: List<MusicFolder>,
    playlists: List<Playlist>,
    favorites: List<Song>,
    recentlyPlayed: List<Song>,
    mostPlayed: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    durationMs: Long,
    currentPositionMs: Long = 0L,
    currentPositionProvider: (() -> Long)? = null,
    artwork: android.graphics.Bitmap? = null,
    onSongClick: (Song, List<Song>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMiniPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {},
    onDeletePlaylist: (Playlist) -> Unit = {},
    onGetPlaylistSongs: suspend (Long) -> List<Song> = { emptyList() },
    onAddSongToPlaylist: (Long, String) -> Unit = { _, _ -> },
    onRemoveSongFromPlaylist: (Long, String) -> Unit = { _, _ -> },
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.TITLE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Active detail view (for viewing songs inside playlists, albums, artists, folders)
    var activeDetailState by remember { mutableStateOf<DetailViewState?>(null) }
    var songForPlaylistSelection by remember { mutableStateOf<Song?>(null) }

    val filteredSongs = remember(songs, searchQuery, sortMode) {
        val filtered = if (searchQuery.isBlank()) songs else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortMode) {
            SortMode.TITLE -> filtered.sortedBy { it.title }
            SortMode.ARTIST -> filtered.sortedBy { it.artist }
            SortMode.DATE -> filtered.sortedByDescending { it.dateAdded }
            SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    val tabs = listOf(
        stringResource(R.string.tab_songs),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_folders),
        stringResource(R.string.tab_playlists)
    )

    // Intercept back button when detail view is active
    BackHandler(enabled = activeDetailState != null) {
        activeDetailState = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // Only show default topBar when not in detail view
            if (activeDetailState == null) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    // Header title and global action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearchActive = false
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                                IconButton(onClick = onOpenEqualizer) {
                                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.equalizer))
                                }
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_title)) },
                                            onClick = {
                                                sortMode = SortMode.TITLE
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_artist)) },
                                            onClick = {
                                                sortMode = SortMode.ARTIST
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_date)) },
                                            onClick = {
                                                sortMode = SortMode.DATE
                                                showSortMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_duration)) },
                                            onClick = {
                                                sortMode = SortMode.DURATION
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                                IconButton(onClick = onOpenSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        }
                    }

                    // Top category tab bar
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            MiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                currentPositionProvider = currentPositionProvider,
                artwork = artwork,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onOpenQueue = onOpenQueue,
                onClick = onOpenMiniPlayer
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeDetailState != null) {
                val detail = activeDetailState!!
                // Recompute songs for smart playlists reactively
                val detailSongs = when {
                    detail.isCustomPlaylist -> detail.songs
                    detail.title == stringResource(R.string.favorites) -> favorites
                    detail.title == stringResource(R.string.recent_played) -> recentlyPlayed
                    detail.title == stringResource(R.string.most_played) -> mostPlayed
                    else -> detail.songs
                }

                PlaylistDetailView(
                    title = detail.title,
                    subtitle = detail.subtitle,
                    songs = detailSongs,
                    currentSong = currentSong,
                    isCustomPlaylist = detail.isCustomPlaylist,
                    customPlaylistId = detail.customPlaylistId,
                    availableLibrarySongs = songs,
                    onBack = { activeDetailState = null },
                    onSongClick = onSongClick,
                    onPlayAll = { plSongs ->
                        if (plSongs.isNotEmpty()) {
                            onSongClick(plSongs.first(), plSongs)
                        }
                    },
                    onShuffleAll = { plSongs ->
                        if (plSongs.isNotEmpty()) {
                            onShuffleAll(plSongs)
                        }
                    },
                    onAddSongToPlaylist = { plId, sUri ->
                        onAddSongToPlaylist(plId, sUri)
                        coroutineScope.launch {
                            val refreshed = onGetPlaylistSongs(plId)
                            activeDetailState = detail.copy(songs = refreshed)
                        }
                    },
                    onRemoveSongFromPlaylist = { plId, sUri ->
                        onRemoveSongFromPlaylist(plId, sUri)
                        coroutineScope.launch {
                            val refreshed = onGetPlaylistSongs(plId)
                            activeDetailState = detail.copy(songs = refreshed)
                        }
                    }
                )
            } else {
                when (selectedTab) {
                    0 -> SongsTab(
                        songs = filteredSongs,
                        currentSong = currentSong,
                        onSongClick = { song -> onSongClick(song, filteredSongs) },
                        onShuffleAll = {
                            if (filteredSongs.isNotEmpty()) {
                                onShuffleAll(filteredSongs)
                            }
                        },
                        onOpenAddToPlaylist = { song -> songForPlaylistSelection = song },
                        onToggleFavorite = onToggleFavorite
                    )
                    1 -> AlbumsTab(
                        albums = albums,
                        onAlbumClick = { album ->
                            val albumSongs = songs.filter { it.album == album.title }
                            activeDetailState = DetailViewState(
                                title = album.title,
                                subtitle = album.artist,
                                songs = albumSongs
                            )
                        }
                    )
                    2 -> ArtistsTab(
                        artists = artists,
                        onArtistClick = { artist ->
                            val artistSongs = songs.filter { it.artist == artist.name }
                            activeDetailState = DetailViewState(
                                title = artist.name,
                                songs = artistSongs
                            )
                        }
                    )
                    3 -> FoldersTab(
                        folders = folders,
                        onFolderClick = { folder ->
                            val folderSongs = songs.filter { File(it.filePath).parent == folder.path }
                            activeDetailState = DetailViewState(
                                title = folder.name,
                                subtitle = folder.path,
                                songs = folderSongs
                            )
                        }
                    )
                    4 -> PlaylistsTab(
                        playlists = playlists,
                        favoritesCount = favorites.size,
                        recentCount = recentlyPlayed.size,
                        mostPlayedCount = mostPlayed.size,
                        onOpenFavorites = {
                            activeDetailState = DetailViewState(
                                title = "我喜欢的音乐",
                                songs = favorites
                            )
                        },
                        onOpenRecent = {
                            activeDetailState = DetailViewState(
                                title = "最近播放",
                                songs = recentlyPlayed
                            )
                        },
                        onOpenMostPlayed = {
                            activeDetailState = DetailViewState(
                                title = "最常播放",
                                songs = mostPlayed
                            )
                        },
                        onPlayFavorites = {
                            if (favorites.isNotEmpty()) onSongClick(favorites.first(), favorites)
                        },
                        onPlayRecent = {
                            if (recentlyPlayed.isNotEmpty()) onSongClick(recentlyPlayed.first(), recentlyPlayed)
                        },
                        onPlayMostPlayed = {
                            if (mostPlayed.isNotEmpty()) onSongClick(mostPlayed.first(), mostPlayed)
                        },
                        onCreatePlaylistClick = { showNewPlaylistDialog = true },
                        onPlaylistClick = { playlist ->
                            coroutineScope.launch {
                                val pSongs = onGetPlaylistSongs(playlist.id)
                                activeDetailState = DetailViewState(
                                    title = playlist.name,
                                    isCustomPlaylist = true,
                                    customPlaylistId = playlist.id,
                                    songs = pSongs
                                )
                            }
                        },
                        onPlaylistPlayClick = onPlaylistClick,
                        onDeletePlaylist = onDeletePlaylist
                    )
                }
            }
        }
    }

    // Dialog for creating a new playlist
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showNewPlaylistDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog for adding a song to a custom playlist
    if (songForPlaylistSelection != null) {
        val song = songForPlaylistSelection!!
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onSelectPlaylist = { playlistId ->
                onAddSongToPlaylist(playlistId, song.uri)
                songForPlaylistSelection = null
            },
            onCreateNewPlaylist = {
                songForPlaylistSelection = null
                showNewPlaylistDialog = true
            },
            onDismiss = { songForPlaylistSelection = null }
        )
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onShuffleAll: () -> Unit,
    onOpenAddToPlaylist: (Song) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {}
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_search_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Header track count and Shuffle All button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} tracks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onShuffleAll) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.shuffle))
                }
            }
        }

        items(songs, key = { it.uri }) { song ->
            val isCurrent = song.uri == currentSong?.uri
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSongClick(song) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            song.hiResBadgeText?.let { badge ->
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
                            text = "${song.displayArtist} · ${song.displayAlbum}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = song.durationFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Song options menu (Add to playlist, favorite)
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_to_playlist)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenAddToPlaylist(song)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (song.isFavorite) "取消收藏" else stringResource(R.string.favorites)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (song.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleFavorite(song.uri)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.id }) { album ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlbumClick(album) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.songCount} tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${artist.songCount} tracks · ${artist.albumCount} albums",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersTab(
    folders: List<MusicFolder>,
    onFolderClick: (MusicFolder) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folder) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${folder.songCount} tracks · ${folder.path}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    favoritesCount: Int,
    recentCount: Int,
    mostPlayedCount: Int,
    onOpenFavorites: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenMostPlayed: () -> Unit,
    onPlayFavorites: () -> Unit,
    onPlayRecent: () -> Unit,
    onPlayMostPlayed: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit = {},
    onPlaylistPlayClick: (Playlist) -> Unit = {},
    onDeletePlaylist: (Playlist) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Smart playlist quick access section
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                SmartPlaylistCard(
                    title = stringResource(R.string.favorites),
                    count = favoritesCount,
                    icon = Icons.Default.Favorite,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onOpenFavorites,
                    onPlayClick = onPlayFavorites
                )
                Spacer(modifier = Modifier.height(8.dp))
                SmartPlaylistCard(
                    title = stringResource(R.string.recent_played),
                    count = recentCount,
                    icon = Icons.Default.History,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenRecent,
                    onPlayClick = onPlayRecent
                )
                Spacer(modifier = Modifier.height(8.dp))
                SmartPlaylistCard(
                    title = stringResource(R.string.most_played),
                    count = mostPlayedCount,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = HiResGold,
                    onClick = onOpenMostPlayed,
                    onPlayClick = onPlayMostPlayed
                )
            }
        }

        // Custom playlists section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Playlists (${playlists.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onCreatePlaylistClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.new_playlist))
                }
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onPlaylistClick(playlist) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${playlist.songCount} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onPlaylistPlayClick(playlist) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { onDeletePlaylist(playlist) }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "$count tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlayClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
