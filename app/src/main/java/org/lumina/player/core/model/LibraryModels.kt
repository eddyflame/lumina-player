package org.lumina.player.core.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int = 0,
    val firstSongUri: String = ""
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int = 0
)

data class MusicFolder(
    val path: String,
    val name: String,
    val songCount: Int,
    val totalSize: Long = 0L
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isSystemPlaylist: Boolean = false
) {
    companion object {
        const val ID_FAVORITES = -1L
        const val ID_RECENT = -2L
        const val ID_MOST_PLAYED = -3L
    }
}
