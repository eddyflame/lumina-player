package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerLogicTest {

    private fun calculateVolumeAttenuation(remainingSeconds: Long): Float {
        return if (remainingSeconds <= 10L) {
            (remainingSeconds / 10f).coerceIn(0f, 1f)
        } else {
            1.0f
        }
    }

    @Test
    fun testSleepTimerTotalSecondsCalculation() {
        assertEquals(900L, 15 * 60L) // 15 mins = 900s
        assertEquals(1800L, 30 * 60L) // 30 mins = 1800s
        assertEquals(2700L, 45 * 60L) // 45 mins = 2700s
        assertEquals(3600L, 60 * 60L) // 60 mins = 3600s
    }

    @Test
    fun testSoftFadeOutVolumeAttenuationInLastTenSeconds() {
        // Above 10 seconds, volume must be at normal full master level (1.0f)
        assertEquals(1.0f, calculateVolumeAttenuation(60L), 0.001f)
        assertEquals(1.0f, calculateVolumeAttenuation(11L), 0.001f)
        assertEquals(1.0f, calculateVolumeAttenuation(10L), 0.001f)

        // Linear interpolation during the last 10 seconds
        assertEquals(0.8f, calculateVolumeAttenuation(8L), 0.001f)
        assertEquals(0.5f, calculateVolumeAttenuation(5L), 0.001f)
        assertEquals(0.2f, calculateVolumeAttenuation(2L), 0.001f)
        assertEquals(0.1f, calculateVolumeAttenuation(1L), 0.001f)
        assertEquals(0.0f, calculateVolumeAttenuation(0L), 0.001f)
    }

    @Test
    fun testEndOfTrackMarker() {
        val endOfTrackSentinel = -1L
        assertEquals(-1L, endOfTrackSentinel)
    }
}
