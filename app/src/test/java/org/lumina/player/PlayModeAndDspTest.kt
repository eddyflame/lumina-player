package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lumina.player.core.dsp.AudioDspController
import org.lumina.player.core.model.PlayMode

class PlayModeAndDspTest {

    @Test
    fun testPlayModeCycle() {
        assertEquals(PlayMode.REPEAT_ALL, PlayMode.SEQUENTIAL.next())
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.REPEAT_ALL.next())
        assertEquals(PlayMode.SHUFFLE, PlayMode.REPEAT_ONE.next())
        assertEquals(PlayMode.SEQUENTIAL, PlayMode.SHUFFLE.next())
    }

    @Test
    fun testAudioDspControllerClampingAndInit() {
        val dsp = AudioDspController()
        dsp.initSettings(enabled = true, bassBoost = 500, virtualizer = 300)

        assertEquals(500.toShort(), dsp.getBassBoost())
        assertEquals(300.toShort(), dsp.getVirtualizer())
        assertTrue(dsp.isEnabled())

        var callbackNotified = false
        var savedBass: Short = 0
        dsp.onPreferencesChanged = { _, bass, _ ->
            callbackNotified = true
            savedBass = bass
        }

        // Test clamping to 0..1000
        dsp.setBassBoost(1500.toShort())
        assertEquals(1000.toShort(), dsp.getBassBoost())
        assertTrue(callbackNotified)
        assertEquals(1000.toShort(), savedBass)

        dsp.setBassBoost((-100).toShort())
        assertEquals(0.toShort(), dsp.getBassBoost())
        assertEquals(0.toShort(), savedBass)

        dsp.setVirtualizer(2000.toShort())
        assertEquals(1000.toShort(), dsp.getVirtualizer())

        dsp.setVirtualizer((-50).toShort())
        assertEquals(0.toShort(), dsp.getVirtualizer())

        dsp.setEnabled(false)
        assertFalse(dsp.isEnabled())
    }

    @Test
    fun testVisualizerProcessorAntiPopRamp() {
        val proc = org.lumina.player.core.dsp.VisualizerAudioProcessor(barCount = 24)
        proc.triggerSoftRamp()
        // Ensure triggerSoftRamp arms without exceptions
        proc.resetSpectrum()
        assertEquals(24, proc.spectrumFlow.value.size)
    }

    @Test
    fun testVisualizerProcessorPcmRampScaling() {
        val proc = org.lumina.player.core.dsp.VisualizerAudioProcessor(barCount = 24)
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            44100,
            2,
            androidx.media3.common.C.ENCODING_PCM_16BIT
        )
        proc.configure(format)
        proc.flush()

        // Provide 3000 frames of PCM 16-bit audio with max amplitude (30000)
        // 44.1kHz: ~882 frames of silence padding + ~1543 frames of Hann ramp + post-ramp bit-perfect
        val frameCount = 3000
        val bytesPerFrame = 4 // 2 channels * 2 bytes
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frameCount) {
            byteBuffer.putShort(30000.toShort()) // L
            byteBuffer.putShort(30000.toShort()) // R
        }
        byteBuffer.flip()

        proc.queueInput(byteBuffer)
        val outputBuffer = proc.output
        assertTrue(outputBuffer.hasRemaining())

        val firstSampleL = outputBuffer.short
        val firstSampleR = outputBuffer.short
        // First sample in silence padding must be pure 0 to prevent DAC pop
        assertEquals(0, firstSampleL.toInt())
        assertEquals(0, firstSampleR.toInt())

        // Sample at frame 300 (inside 20ms silence padding): must be pure 0
        outputBuffer.position(300 * bytesPerFrame)
        val silenceSample = outputBuffer.short
        assertEquals(0, silenceSample.toInt())

        // Read sample at frame 1650 (middle of Hann ramp): should be smoothly scaled up
        outputBuffer.position(1650 * bytesPerFrame)
        val midSample = outputBuffer.short
        assertTrue("Mid sample ($midSample) should be greater than 0 and less than 30000", midSample in 1..29999)

        // Read sample at frame 2600 (after ramp): must be original amplitude (bit-perfect)
        outputBuffer.position(2600 * bytesPerFrame)
        val fullSample = outputBuffer.short
        assertEquals(30000, fullSample.toInt())
    }

    @Test
    fun testFlacContinuousPlaybackDoesNotReArmRampOnInterBufferDelay() {
        val proc = org.lumina.player.core.dsp.VisualizerAudioProcessor(barCount = 24)
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            44100,
            2,
            androidx.media3.common.C.ENCODING_PCM_16BIT
        )
        proc.configure(format)
        proc.flush()

        val frameCount = 3000
        val bytesPerFrame = 4 // 2 channels * 2 bytes
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frameCount) {
            byteBuffer.putShort(30000.toShort()) // L
            byteBuffer.putShort(30000.toShort()) // R
        }
        byteBuffer.flip()

        // 1. Initial buffer: silence and Hann ramp are applied and exhausted
        proc.queueInput(byteBuffer)
        val output1 = proc.output
        assertTrue(output1.hasRemaining())

        // 2. Simulate subsequent FLAC block (e.g. 4096 samples, typically decoded ~93-105ms after previous)
        // Ensure that during continuous stream, subsequent buffers arrive 100% bit-perfect without
        // false-positive silence padding or ramp re-arming (which causes audio flutter/jitter).
        val flacBlockFrames = 4096
        val flacBuffer = java.nio.ByteBuffer.allocateDirect(flacBlockFrames * bytesPerFrame)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until flacBlockFrames) {
            flacBuffer.putShort(30000.toShort())
            flacBuffer.putShort(30000.toShort())
        }
        flacBuffer.flip()

        proc.queueInput(flacBuffer)
        val output2 = proc.output
        assertTrue(output2.hasRemaining())

        // The very first sample of the subsequent FLAC block MUST retain original amplitude (30000), NOT muted to 0
        val sampleL = output2.short
        val sampleR = output2.short
        assertEquals(30000, sampleL.toInt())
        assertEquals(30000, sampleR.toInt())

        // Check middle of block
        output2.position(1000 * bytesPerFrame)
        assertEquals(30000, output2.short.toInt())
    }
}

