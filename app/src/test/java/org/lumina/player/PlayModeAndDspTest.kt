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

        // Provide 100 frames of PCM 16-bit audio with max amplitude (30000)
        val frameCount = 100
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
        // First sample should be scaled down to 0 or near 0 by Hann window
        assertEquals(0, firstSampleL.toInt())
        assertEquals(0, firstSampleR.toInt())

        // Read sample at frame 50: should be noticeably scaled up but less than 30000
        outputBuffer.position(50 * bytesPerFrame)
        val midSample = outputBuffer.short
        assertTrue("Mid sample ($midSample) should be greater than 0 and less than 30000", midSample in 1..29999)
    }
}
