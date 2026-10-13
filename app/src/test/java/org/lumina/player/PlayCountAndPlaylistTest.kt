package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lumina.player.core.model.Song

class PlayCountAndPlaylistTest {

    private fun createDummySong(id: Long, playCount: Int): Song {
        return Song(
            id = id,
            uri = "content://media/external/audio/media/$id",
            title = "Track $id",
            artist = "Artist",
            album = "Album",
            playCount = playCount
        )
    }

    @Test
    fun testMostPlayedSortingAndFilter() {
        val songList = listOf(
            createDummySong(1, 0),
            createDummySong(2, 5),
            createDummySong(3, 20),
            createDummySong(4, 15),
            createDummySong(5, 0)
        )

        val mostPlayed = songList.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }

        assertEquals(3, mostPlayed.size)
        assertEquals(3L, mostPlayed[0].id) // 20 plays
        assertEquals(4L, mostPlayed[1].id) // 15 plays
        assertEquals(2L, mostPlayed[2].id) // 5 plays
    }

    @Test
    fun testTopTiersSubsets() {
        // Generate 150 songs with ascending play counts
        val allSongs = (1..150).map { createDummySong(it.toLong(), it) }
        val sortedMostPlayed = allSongs.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(100)

        assertEquals(100, sortedMostPlayed.size)
        assertEquals(10, sortedMostPlayed.take(10).size)
        assertEquals(50, sortedMostPlayed.take(50).size)
        assertEquals(100, sortedMostPlayed.take(100).size)

        // Verify top 10 is indeed highest played
        assertEquals(150, sortedMostPlayed.take(10).first().playCount)
        assertEquals(141, sortedMostPlayed.take(10).last().playCount)
    }

    @Test
    fun testPlayCountReset() {
        val original = createDummySong(10, 42)
        val resetSong = original.copy(playCount = 0, lastPlayedTime = 0L)

        assertEquals(0, resetSong.playCount)
        assertEquals(0L, resetSong.lastPlayedTime)
    }
}
