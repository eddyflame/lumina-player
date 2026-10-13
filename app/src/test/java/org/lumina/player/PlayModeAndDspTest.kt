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
}
