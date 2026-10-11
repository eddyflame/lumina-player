package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lumina.player.core.model.AudioFormatType

class AudioFormatTypeTest {

    @Test
    fun testFormatDetection() {
        assertEquals(AudioFormatType.FLAC, AudioFormatType.fromExtension("song.flac"))
        assertEquals(AudioFormatType.APE, AudioFormatType.fromExtension("/storage/album/track.ape"))
        assertEquals(AudioFormatType.DSD, AudioFormatType.fromExtension("sample.dsf"))
        assertEquals(AudioFormatType.DSD, AudioFormatType.fromExtension("sample.dff"))
        assertEquals(AudioFormatType.WAV, AudioFormatType.fromExtension("audio.wav"))
        assertEquals(AudioFormatType.MP3, AudioFormatType.fromExtension("music.mp3"))
        assertEquals(AudioFormatType.AAC, AudioFormatType.fromExtension("music.m4a"))
        assertEquals(AudioFormatType.OPUS, AudioFormatType.fromExtension("voice.opus"))
        assertEquals(AudioFormatType.OGG, AudioFormatType.fromExtension("audio.ogg"))
        assertEquals(AudioFormatType.WV, AudioFormatType.fromExtension("track.wv"))
    }

    @Test
    fun testLosslessAndHiResFlags() {
        assertTrue(AudioFormatType.FLAC.isLossless)
        assertTrue(AudioFormatType.FLAC.isHiRes)

        assertTrue(AudioFormatType.DSD.isLossless)
        assertTrue(AudioFormatType.DSD.isHiRes)

        assertTrue(AudioFormatType.APE.isLossless)
        assertTrue(AudioFormatType.APE.isHiRes)

        assertTrue(AudioFormatType.WAV.isLossless)
        assertTrue(AudioFormatType.WAV.isHiRes)

        assertEquals(false, AudioFormatType.MP3.isLossless)
        assertEquals(false, AudioFormatType.MP3.isHiRes)
    }
}
