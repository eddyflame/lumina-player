package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.AudioTechnicalInfo

class AudioTechnicalInfoTest {

    @Test
    fun testSampleRateDisplay() {
        val cdQuality = AudioTechnicalInfo(sampleRate = 44100)
        assertEquals("44.1 kHz", cdQuality.sampleRateDisplay)

        val studioHiRes = AudioTechnicalInfo(sampleRate = 96000)
        assertEquals("96.0 kHz", studioHiRes.sampleRateDisplay)

        val masterHiRes = AudioTechnicalInfo(sampleRate = 192000)
        assertEquals("192.0 kHz", masterHiRes.sampleRateDisplay)

        val dsd64 = AudioTechnicalInfo(sampleRate = 2822400)
        assertEquals("2.8 MHz", dsd64.sampleRateDisplay)

        val dsd128 = AudioTechnicalInfo(sampleRate = 5644800)
        assertEquals("5.6 MHz", dsd128.sampleRateDisplay)

        val subKhz = AudioTechnicalInfo(sampleRate = 800)
        assertEquals("800 Hz", subKhz.sampleRateDisplay)
    }

    @Test
    fun testBitDepthDisplay() {
        val pcm16 = AudioTechnicalInfo(format = AudioFormatType.FLAC, bitDepth = 16)
        assertEquals("16-bit", pcm16.bitDepthDisplay)

        val pcm24 = AudioTechnicalInfo(format = AudioFormatType.FLAC, bitDepth = 24)
        assertEquals("24-bit", pcm24.bitDepthDisplay)

        val dsd = AudioTechnicalInfo(format = AudioFormatType.DSD, bitDepth = 1)
        assertEquals("1-bit Direct Stream", dsd.bitDepthDisplay)
    }

    @Test
    fun testFormattedFileSize() {
        val tenMegabytes = AudioTechnicalInfo(fileSizeBytes = 10L * 1024L * 1024L)
        assertEquals("10.00 MB", tenMegabytes.formattedFileSize)

        val halfMegabyte = AudioTechnicalInfo(fileSizeBytes = 512L * 1024L)
        assertEquals("0.50 MB", halfMegabyte.formattedFileSize)
    }
}
