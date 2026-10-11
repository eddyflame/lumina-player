package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lumina.player.core.cue.CueSheetParser
import org.lumina.player.core.model.AudioFormatType
import java.io.File

class CueSheetParserTest {

    private val sampleCue = """
        PERFORMER "Pink Floyd"
        TITLE "The Dark Side of the Moon"
        FILE "DarkSide.flac" WAVE
          TRACK 01 AUDIO
            TITLE "Speak to Me"
            PERFORMER "Pink Floyd"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "Breathe"
            PERFORMER "Pink Floyd"
            INDEX 01 01:07:00
          TRACK 03 AUDIO
            TITLE "On the Run"
            PERFORMER "Pink Floyd"
            INDEX 01 03:50:35
    """.trimIndent()

    @Test
    fun testParseCueSheet() {
        val cueSheet = CueSheetParser.parse(sampleCue)
        assertEquals("Pink Floyd", cueSheet.albumPerformer)
        assertEquals("The Dark Side of the Moon", cueSheet.albumTitle)
        assertEquals(3, cueSheet.tracks.size)

        val track1 = cueSheet.tracks[0]
        assertEquals(1, track1.trackNumber)
        assertEquals("Speak to Me", track1.title)
        assertEquals(0L, track1.startMs)
        assertEquals(67000L, track1.endMs) // 01:07:00 = 67000ms

        val track2 = cueSheet.tracks[1]
        assertEquals(2, track2.trackNumber)
        assertEquals("Breathe", track2.title)
        assertEquals(67000L, track2.startMs)
    }

    @Test
    fun testCreateVirtualSongs() {
        val cueSheet = CueSheetParser.parse(sampleCue)
        val dummyFile = File("/music/DarkSide.flac")
        val songs = CueSheetParser.createVirtualSongs(cueSheet, dummyFile, 300000L)

        assertEquals(3, songs.size)
        assertEquals("Speak to Me", songs[0].title)
        assertEquals(true, songs[0].isVirtualCueTrack)
        assertEquals(AudioFormatType.FLAC, songs[0].audioFormat)
        assertEquals(67000L, songs[0].durationMs)
    }
}
