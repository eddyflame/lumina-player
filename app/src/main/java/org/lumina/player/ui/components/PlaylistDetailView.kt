package org.lumina.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.lumina.player.R
import org.lumina.player.core.model.Song
import org.lumina.player.ui.theme.HiResGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    title: String,
    subtitle: String? = null,
    songs: List<Song>,
    currentSong: Song?,
    isCustomPlaylist: Boolean = false,
    customPlaylistId: Long? = null,
    availableLibrarySongs: List<Song> = emptyList(),
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAddSongToPlaylist: (Long, String) -> Unit = { _, _ -> },
    onAddSongsToPlaylist: (Long, List<String>) -> Unit = { _, _ -> },
    onRemoveSongFromPlaylist: (Long, String) -> Unit = { _, _ -> },
    onRemoveSongsFromPlaylist: (Long, List<String>) -> Unit = { _, _ -> },
    onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember(title) { mutableStateOf(title) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveBatchConfirmDialog by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var currentDisplayTitle by remember(title) { mutableStateOf(title) }

    BackHandler {
        if (isBatchMode) {
            isBatchMode = false
            selectedUris = emptySet()
        } else {
            onBack()
        }
    }

    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }
    val totalMinutes = totalDurationMs / 1000 / 60

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Navigation App Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isBatchMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
            tonalElevation = if (isBatchMode) 4.dp else 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBatchMode) {
                    IconButton(onClick = {
                        isBatchMode = false
                        selectedUris = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                    Text(
                        text = stringResource(R.string.selected_count, selectedUris.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = {
                        selectedUris = if (selectedUris.size == songs.size) {
                            emptySet()
                        } else {
                            songs.map { it.uri }.toSet()
                        }
                    }) {
                        Text(
                            text = if (selectedUris.size == songs.size) {
                                stringResource(R.string.deselect_all)
                            } else {
                                stringResource(R.string.select_all)
                            }
                        )
                    }
                    IconButton(
                        onClick = { showRemoveBatchConfirmDialog = true },
                        enabled = selectedUris.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = if (selectedUris.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                    Text(
                        text = currentDisplayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    if (isCustomPlaylist && customPlaylistId != null) {
                        IconButton(onClick = { showAddSongsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_songs),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            isBatchMode = true
                            selectedUris = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.batch_edit)
                            )
                        }
                        Box {
                            IconButton(onClick = { showHeaderMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = showHeaderMenu,
                                onDismissRequest = { showHeaderMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename_playlist)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        newPlaylistName = currentDisplayTitle
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_songs)) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showAddSongsSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.batch_edit)) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        isBatchMode = true
                                        selectedUris = emptySet()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.delete_playlist),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Playlist Header Card
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = currentDisplayTitle,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isCustomPlaylist && customPlaylistId != null) {
                                                IconButton(
                                                    onClick = {
                                                        newPlaylistName = currentDisplayTitle
                                                        showRenameDialog = true
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = stringResource(R.string.rename),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (!subtitle.isNullOrBlank()) {
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${songs.size} 首歌曲 · 约 $totalMinutes 分钟",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Play All, Shuffle & Add action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { if (songs.isNotEmpty()) onPlayAll(songs) },
                                        enabled = songs.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.play_all), fontSize = 13.sp)
                                    }

                                    FilledTonalButton(
                                        onClick = { if (songs.isNotEmpty()) onShuffleAll(songs) },
                                        enabled = songs.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.shuffle), fontSize = 13.sp)
                                    }

                                    if (isCustomPlaylist && customPlaylistId != null) {
                                        OutlinedButton(
                                            onClick = { showAddSongsSheet = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.add), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty state
                if (songs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.empty_playlist),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCustomPlaylist && customPlaylistId != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showAddSongsSheet = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.add_songs))
                                }
                            }
                        }
                    }
                } else {
                    // Song Items in this playlist
                    itemsIndexed(songs, key = { _, song -> song.uri }) { index, song ->
                        val isCurrent = song.uri == currentSong?.uri
                        val isSelected = selectedUris.contains(song.uri)

                        Surface(
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else -> Color.Transparent
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isBatchMode) {
                                        selectedUris = if (isSelected) {
                                            selectedUris - song.uri
                                        } else {
                                            selectedUris + song.uri
                                        }
                                    } else {
                                        onSongClick(song, songs)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBatchMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedUris = if (checked) {
                                                selectedUris + song.uri
                                            } else {
                                                selectedUris - song.uri
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                } else {
                                    // Song track index or play icon
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                // Title & Artist
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

                                // If custom playlist and not in batch mode, provide quick delete button
                                if (isCustomPlaylist && customPlaylistId != null && !isBatchMode) {
                                    IconButton(
                                        onClick = { onRemoveSongFromPlaylist(customPlaylistId, song.uri) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.remove_from_playlist),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Floating Bottom Action Bar in Batch Mode
            androidx.compose.animation.AnimatedVisibility(
                visible = isBatchMode && selectedUris.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.selected_count, selectedUris.size),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val selectedSongs = songs.filter { selectedUris.contains(it.uri) }
                                    if (selectedSongs.isNotEmpty()) {
                                        onPlayAll(selectedSongs)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.play), fontSize = 12.sp)
                            }

                            Button(
                                onClick = { showRemoveBatchConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.remove_selected, selectedUris.size), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for adding songs from library into this custom playlist
    if (showAddSongsSheet && customPlaylistId != null) {
        val existingUris = remember(songs) { songs.map { it.uri }.toSet() }
        AddSongsToPlaylistSheet(
            librarySongs = availableLibrarySongs,
            existingSongUris = existingUris,
            onAddSingleSong = { song -> onAddSongToPlaylist(customPlaylistId, song.uri) },
            onAddMultipleSongs = { uris -> onAddSongsToPlaylist(customPlaylistId, uris) },
            onDismiss = { showAddSongsSheet = false }
        )
    }

    // Rename Playlist Dialog
    if (showRenameDialog && customPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.rename_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPlaylistName.trim()
                        if (trimmed.isNotBlank()) {
                            currentDisplayTitle = trimmed
                            onRenamePlaylist(customPlaylistId, trimmed)
                            showRenameDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Playlist Confirmation Dialog
    if (showDeleteConfirmDialog && customPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.confirm_delete_playlist, currentDisplayTitle),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePlaylist(customPlaylistId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Batch Remove Confirmation Dialog
    if (showRemoveBatchConfirmDialog && customPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showRemoveBatchConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.remove_from_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.confirm_remove_songs, selectedUris.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveSongsFromPlaylist(customPlaylistId, selectedUris.toList())
                        selectedUris = emptySet()
                        isBatchMode = false
                        showRemoveBatchConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveBatchConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Bottom sheet allowing single or multi-select addition of library songs into custom playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsToPlaylistSheet(
    librarySongs: List<Song>,
    existingSongUris: Set<String>,
    onAddSingleSong: (Song) -> Unit,
    onAddMultipleSongs: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var addedUris by remember { mutableStateOf(existingSongUris) }
    var selectedToBatchAdd by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(librarySongs, searchQuery) {
        if (searchQuery.isBlank()) librarySongs else {
            librarySongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val unaddedFiltered = remember(filtered, addedUris) {
        filtered.filter { !addedUris.contains(it.uri) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_songs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unaddedFiltered.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedToBatchAdd = if (selectedToBatchAdd.size == unaddedFiltered.size) {
                                    emptySet()
                                } else {
                                    unaddedFiltered.map { it.uri }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedToBatchAdd.size == unaddedFiltered.size) {
                                    stringResource(R.string.deselect_all)
                                } else {
                                    stringResource(R.string.select_all)
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedToBatchAdd.isNotEmpty()) {
                                onAddMultipleSongs(selectedToBatchAdd.toList())
                                addedUris = addedUris + selectedToBatchAdd
                                selectedToBatchAdd = emptySet()
                            }
                            onDismiss()
                        }
                    ) {
                        Text(
                            if (selectedToBatchAdd.isNotEmpty()) {
                                stringResource(R.string.add_selected, selectedToBatchAdd.size)
                            } else {
                                stringResource(R.string.save)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                items(filtered, key = { it.uri }) { song ->
                    val isAlreadyAdded = addedUris.contains(song.uri)
                    val isSelected = selectedToBatchAdd.contains(song.uri)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAlreadyAdded) {
                                selectedToBatchAdd = if (isSelected) {
                                    selectedToBatchAdd - song.uri
                                } else {
                                    selectedToBatchAdd + song.uri
                                }
                            },
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isAlreadyAdded) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedToBatchAdd = if (checked) {
                                            selectedToBatchAdd + song.uri
                                        } else {
                                            selectedToBatchAdd - song.uri
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.displayArtist} · ${song.displayAlbum}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isAlreadyAdded) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "已添加",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        onAddSingleSong(song)
                                        addedUris = addedUris + song.uri
                                        selectedToBatchAdd = selectedToBatchAdd - song.uri
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.add))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
