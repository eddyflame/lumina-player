package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.Song

class SongModelTest {

    private fun createSong(
        durationMs: Long = 180000L,
        artist: String = "Test Artist",
        album: String = "Test Album",
        format: AudioFormatType = AudioFormatType.MP3,
        bitDepth: Int = 16,
        sampleRate: Int = 44100,
        isVirtualCue: Boolean = false,
        cueStart: Long = 0L,
        cueEnd: Long = 0L
    ): Song {
        return Song(
            id = 1L,
            uri = "content://media/audio/1",
            title = "Test Song",
            artist = artist,
            album = album,
            durationMs = durationMs,
            audioFormat = format,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            isVirtualCueTrack = isVirtualCue,
            cueStartMs = cueStart,
            cueEndMs = cueEnd
        )
    }

    @Test
    fun testDurationFormattedStandard() {
        val song = createSong(durationMs = 195000L) // 3m 15s
        assertEquals("03:15", song.durationFormatted)
    }

    @Test
    fun testDurationFormattedHours() {
        val oneHour = createSong(durationMs = 3600000L) // 1h 00m 00s
        assertEquals("1:00:00", oneHour.durationFormatted)

        val longSymphony = createSong(durationMs = 5025000L) // 1h 23m 45s
        assertEquals("1:23:45", longSymphony.durationFormatted)
    }

    @Test
    fun testDurationFormattedZeroAndNegative() {
        val zeroSong = createSong(durationMs = 0L)
        assertEquals("00:00", zeroSong.durationFormatted)

        val negativeSong = createSong(durationMs = -5000L)
        assertEquals("00:00", negativeSong.durationFormatted)
    }

    @Test
    fun testDurationFormattedVirtualCue() {
        val cueSong = createSong(
            durationMs = 300000L,
            isVirtualCue = true,
            cueStart = 10000L,
            cueEnd = 50000L
        )
        // cue duration = 40000ms = 40s
        assertEquals("00:40", cueSong.durationFormatted)
    }

    @Test
    fun testDisplayArtistAndAlbumFallback() {
        val emptyArtist = createSong(artist = "")
        assertEquals("Unknown Artist", emptyArtist.displayArtist)

        val blankArtist = createSong(artist = "   ")
        assertEquals("Unknown Artist", blankArtist.displayArtist)

        val emptyAlbum = createSong(album = "")
        assertEquals("Unknown Album", emptyAlbum.displayAlbum)

        val normalSong = createSong(artist = "Queen", album = "A Night at the Opera")
        assertEquals("Queen", normalSong.displayArtist)
        assertEquals("A Night at the Opera", normalSong.displayAlbum)
    }

    @Test
    fun testIsHiResLogic() {
        // Standard lossy MP3 16-bit 44.1kHz -> not Hi-Res
        val standardMp3 = createSong(format = AudioFormatType.MP3, bitDepth = 16, sampleRate = 44100)
        assertFalse(standardMp3.isHiRes)

        // Lossless FLAC 16-bit 44.1kHz -> Hi-Res flag is true on format
        val flac = createSong(format = AudioFormatType.FLAC, bitDepth = 16, sampleRate = 44100)
        assertTrue(flac.isHiRes)

        // DSD format -> always Hi-Res
        val dsd = createSong(format = AudioFormatType.DSD, bitDepth = 1, sampleRate = 2822400)
        assertTrue(dsd.isHiRes)

        // 24-bit audio -> Hi-Res
        val hiBit = createSong(format = AudioFormatType.MP3, bitDepth = 24, sampleRate = 44100)
        assertTrue(hiBit.isHiRes)

        // 48kHz or higher sample rate -> Hi-Res
        val hiRate = createSong(format = AudioFormatType.AAC, bitDepth = 16, sampleRate = 48000)
        assertTrue(hiRate.isHiRes)
    }

    @Test
    fun testHiResBadgeText() {
        val dsd = createSong(format = AudioFormatType.DSD)
        assertEquals("DSD", dsd.hiResBadgeText)

        val bit24rate96 = createSong(format = AudioFormatType.FLAC, bitDepth = 24, sampleRate = 96000)
        assertEquals("24B/96k", bit24rate96.hiResBadgeText)

        val bit24rate192 = createSong(format = AudioFormatType.FLAC, bitDepth = 24, sampleRate = 192000)
        assertEquals("24B/192k", bit24rate192.hiResBadgeText)

        val bit24rate44 = createSong(format = AudioFormatType.WAV, bitDepth = 24, sampleRate = 44100)
        assertEquals("24BIT", bit24rate44.hiResBadgeText)

        val flac16 = createSong(format = AudioFormatType.FLAC, bitDepth = 16, sampleRate = 44100)
        assertEquals("FLAC", flac16.hiResBadgeText)

        val mp3 = createSong(format = AudioFormatType.MP3, bitDepth = 16, sampleRate = 44100)
        assertNull(mp3.hiResBadgeText)
    }
}
