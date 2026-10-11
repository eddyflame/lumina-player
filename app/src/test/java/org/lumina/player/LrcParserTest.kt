package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lumina.player.core.lyrics.LrcParser

class LrcParserTest {

    private val sampleLrc = """
        [ti:Bohemian Rhapsody]
        [ar:Queen]
        [00:00.00]Is this the real life?
        [00:03.20]Is this just fantasy?
        [00:08.50]Caught in a landslide
        [00:12.80]No escape from reality
    """.trimIndent()

    @Test
    fun testParseLrc() {
        val lines = LrcParser.parse(sampleLrc)
        assertEquals(4, lines.size)

        assertEquals(0L, lines[0].startMs)
        assertEquals("Is this the real life?", lines[0].content)

        assertEquals(3200L, lines[1].startMs)
        assertEquals("Is this just fantasy?", lines[1].content)

        assertEquals(8500L, lines[2].startMs)
        assertEquals(12800L, lines[3].startMs)
    }

    @Test
    fun testFindCurrentIndex() {
        val lines = LrcParser.parse(sampleLrc)

        // Before first line
        assertEquals(-1, LrcParser.findCurrentIndex(lines, -100L))

        // Exact match first line
        assertEquals(0, LrcParser.findCurrentIndex(lines, 0L))

        // Between first and second line
        assertEquals(0, LrcParser.findCurrentIndex(lines, 2000L))

        // At second line
        assertEquals(1, LrcParser.findCurrentIndex(lines, 3200L))

        // After last line
        assertEquals(3, LrcParser.findCurrentIndex(lines, 20000L))
    }

    @Test
    fun testFindCurrentIndexWithOffset() {
        val lines = LrcParser.parse(sampleLrc)

        // Position 3000ms with +500ms offset -> effective position 3500ms (should be line 1: 3200ms)
        assertEquals(1, LrcParser.findCurrentIndex(lines, 3000L, offsetMs = 500L))
    }
}
