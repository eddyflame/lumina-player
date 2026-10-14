package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lumina.player.core.dsp.EqPresets

class EqPresetTest {

    @Test
    fun testAllPresetsHaveFiveBands() {
        assertEquals(7, EqPresets.ALL.size)
        for (preset in EqPresets.ALL) {
            assertEquals("Preset ${preset.id} must have exactly 5 frequency bands", 5, preset.bandLevels.size)
        }
    }

    @Test
    fun testPresetLevelsWithinHardwareRange() {
        val minLevel: Short = -1500
        val maxLevel: Short = 1500

        for (preset in EqPresets.ALL) {
            for (level in preset.bandLevels) {
                assertTrue("Level $level in preset ${preset.id} exceeds min limit $minLevel", level >= minLevel)
                assertTrue("Level $level in preset ${preset.id} exceeds max limit $maxLevel", level <= maxLevel)
            }
        }
    }

    @Test
    fun testFlatPresetIsZeroGain() {
        val flat = EqPresets.FLAT
        assertEquals("flat", flat.id)
        assertTrue("Flat preset all bands must be zero", flat.bandLevels.all { it == 0.toShort() })
    }

    @Test
    fun testFindMatchingPresetExact() {
        for (preset in EqPresets.ALL) {
            val matched = EqPresets.findMatchingPreset(preset.bandLevels)
            assertNotNull("Should match preset ${preset.id}", matched)
            assertEquals(preset.id, matched?.id)
        }
    }

    @Test
    fun testFindMatchingPresetFuzzyWithinTolerance() {
        // Slightly perturbed Rock preset (+5 mB deviation on band 0)
        val rockPerturbed = listOf<Short>(405, 200, -100, 300, 500)
        val matched = EqPresets.findMatchingPreset(rockPerturbed, toleranceMb = 10)
        assertNotNull(matched)
        assertEquals(EqPresets.ROCK.id, matched?.id)

        // Exceeding tolerance should fail
        val rockDiverged = listOf<Short>(450, 200, -100, 300, 500)
        val unmatched = EqPresets.findMatchingPreset(rockDiverged, toleranceMb = 10)
        assertNull("Diverged curve should not match preset", unmatched)
    }

    @Test
    fun testCustomLevelsReturnNull() {
        val customCurve = listOf<Short>(1500, -1200, 800, -400, 1100)
        val match = EqPresets.findMatchingPreset(customCurve)
        assertNull("Custom curve must return null", match)
    }
}
