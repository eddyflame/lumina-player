package org.lumina.player.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.lumina.player.core.model.Album
import org.lumina.player.core.model.Artist
import org.lumina.player.core.model.MusicFolder
import org.lumina.player.core.model.Playlist
import org.lumina.player.core.model.Song
import org.lumina.player.data.db.MusicDatabase
import org.lumina.player.data.scanner.MediaStoreScanner
import java.io.File

class MusicRepository(
    private val database: MusicDatabase,
    private val scanner: MediaStoreScanner
) {
    val songsFlow: Flow<List<Song>> = database.songsFlow
    val playlistsFlow: Flow<List<Playlist>> = database.playlistsFlow

    val albumsFlow: Flow<List<Album>> = database.songsFlow.map { songs ->
        songs.groupBy { it.album.ifBlank { "Unknown Album" } }
            .map { (albumTitle, songList) ->
                val firstSong = songList.first()
                Album(
                    id = firstSong.albumId.takeIf { it != 0L } ?: albumTitle.hashCode().toLong(),
                    title = albumTitle,
                    artist = firstSong.artist.ifBlank { "Various Artists" },
                    songCount = songList.size,
                    year = songList.map { it.year }.filter { it > 0 }.maxOrNull() ?: 0,
                    firstSongUri = firstSong.uri
                )
            }
            .sortedBy { it.title }
    }

    val artistsFlow: Flow<List<Artist>> = database.songsFlow.map { songs ->
        songs.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .map { (artistName, songList) ->
                val albumCount = songList.map { it.album }.distinct().size
                Artist(
                    name = artistName,
                    songCount = songList.size,
                    albumCount = albumCount
                )
            }
            .sortedBy { it.name }
    }

    val foldersFlow: Flow<List<MusicFolder>> = database.songsFlow.map { songs ->
        songs.filter { it.filePath.isNotBlank() }
            .groupBy { File(it.filePath).parent ?: "Root" }
            .map { (folderPath, songList) ->
                val name = File(folderPath).name.ifBlank { folderPath }
                val totalBytes = songList.sumOf { it.fileSize }
                MusicFolder(
                    path = folderPath,
                    name = name,
                    songCount = songList.size,
                    totalSize = totalBytes
                )
            }
            .sortedBy { it.name }
    }

    val favoritesFlow: Flow<List<Song>> = database.songsFlow.map { songs ->
        songs.filter { it.isFavorite }
    }

    val recentlyPlayedFlow: Flow<List<Song>> = database.songsFlow.map { songs ->
        songs.filter { it.lastPlayedTime > 0L }
            .sortedByDescending { it.lastPlayedTime }
    }

    val mostPlayedFlow: Flow<List<Song>> = database.songsFlow.map { songs ->
        songs.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
    }

    suspend fun rescan(filterShortAudio: Boolean) {
        val scanned = scanner.scanMediaStore(filterShortAudio)
        database.syncScannedSongs(scanned)
    }

    suspend fun scanDirectory(dir: File) {
        val scanned = scanner.scanDirectory(dir)
        database.syncScannedSongs(scanned)
    }

    fun toggleFavorite(uri: String) = database.toggleFavorite(uri)
    fun recordPlay(uri: String) = database.recordPlay(uri)
    fun updateSongTags(uri: String, title: String, artist: String, album: String, year: Int, genre: String) =
        database.updateSongTags(uri, title, artist, album, year, genre)

    fun createPlaylist(name: String) = database.createPlaylist(name)
    fun deletePlaylist(id: Long) = database.deletePlaylist(id)
    fun addSongToPlaylist(playlistId: Long, songUri: String) = database.addSongToPlaylist(playlistId, songUri)
    fun removeSongFromPlaylist(playlistId: Long, songUri: String) = database.removeSongFromPlaylist(playlistId, songUri)
    suspend fun getPlaylistSongs(playlistId: Long) = database.getPlaylistSongs(playlistId)
}
