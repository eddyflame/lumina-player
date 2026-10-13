@file:Suppress("DEPRECATION")

package org.lumina.player.core.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Equalizer frequency preset configuration.
 */
data class EqPreset(
    val id: String,
    val nameResId: Int,
    val bandLevels: List<Short> // Relative dB gain in milliBels (mB, e.g. -1000 to +1000)
)

/**
 * DSP audio effects engine controller (Equalizer, Bass Boost, Virtualizer).
 *
 * Prevents popping and distortion by:
 * 1. Keeping hardware AudioEffect instances enabled during strength adjustments
 *    (preventing AudioFlinger filter detachment / re-attachment DC-offset pops).
 * 2. Smoothly interpolating large strength transitions over ~60ms.
 */
@Suppress("DEPRECATION")
class AudioDspController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var isEnabled = true
    private var bassBoostStrength: Short = 0
    private var virtualizerStrength: Short = 0
    private var currentBandLevels = mutableListOf<Short>()
    private var currentAudioSessionId: Int = 0

    // Callback when user alters settings so they can be persisted
    var onPreferencesChanged: ((enabled: Boolean, bassBoost: Short, virtualizer: Short) -> Unit)? = null

    // Smooth ramp coroutine infrastructure
    private val rampScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bassRampJob: Job? = null
    private var virtualizerRampJob: Job? = null

    // Current live strength being applied to the hardware effect
    @Volatile private var liveBassStrength: Short = 0
    @Volatile private var liveVirtualizerStrength: Short = 0

    fun initSettings(enabled: Boolean, bassBoost: Short, virtualizer: Short) {
        isEnabled = enabled
        bassBoostStrength = bassBoost
        virtualizerStrength = virtualizer
        liveBassStrength = bassBoost
        liveVirtualizerStrength = virtualizer
    }

    /**
     * Attaches audio effects when ExoPlayer's AudioSessionId initializes or changes.
     * Prevents redundant release/recreation on identical sessionId to eliminate pops/crackles during seeking.
     */
    @Synchronized
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId

        releaseInternal()

        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply {
                val numBands = numberOfBands.toInt()
                if (currentBandLevels.size == numBands) {
                    for (i in 0 until numBands) {
                        setBandLevel(i.toShort(), currentBandLevels[i])
                    }
                } else {
                    currentBandLevels.clear()
                    for (i in 0 until numBands) {
                        currentBandLevels.add(getBandLevel(i.toShort()))
                    }
                }
                // Only enable when non-flat to prevent filter-insertion pops and clipping
                enabled = isEnabled && currentBandLevels.any { it != 0.toShort() }
            }
        }

        runCatching {
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(bassBoostStrength)
                }
                liveBassStrength = bassBoostStrength
                // Only enable when strength > 0 to prevent HAL volume intercept and pipeline insertion pops
                enabled = isEnabled && bassBoostStrength > 0
            }
        }

        runCatching {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(virtualizerStrength)
                }
                liveVirtualizerStrength = virtualizerStrength
                // Only enable when strength > 0 to prevent HAL volume intercept and pipeline insertion pops
                enabled = isEnabled && virtualizerStrength > 0
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        equalizer?.enabled = enabled && currentBandLevels.any { it != 0.toShort() }
        bassBoost?.enabled = enabled && bassBoostStrength > 0
        virtualizer?.enabled = enabled && virtualizerStrength > 0
        onPreferencesChanged?.invoke(enabled, bassBoostStrength, virtualizerStrength)
    }

    fun isEnabled(): Boolean = isEnabled

    fun getNumberOfBands(): Int = equalizer?.numberOfBands?.toInt() ?: 5

    fun getBandCenterFreq(band: Short): Int = equalizer?.getCenterFreq(band)?.div(1000) ?: 1000

    fun getBandLevelRange(): Pair<Short, Short> {
        val range = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
        return Pair(range[0], range[1])
    }

    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: currentBandLevels.getOrNull(band.toInt()) ?: 0
    }

    fun setBandLevel(band: Short, level: Short) {
        val bandIndex = band.toInt()
        while (currentBandLevels.size <= bandIndex) {
            currentBandLevels.add(0)
        }
        currentBandLevels[bandIndex] = level
        runCatching {
            equalizer?.setBandLevel(band, level)
            equalizer?.enabled = isEnabled && currentBandLevels.any { it != 0.toShort() }
        }
    }

    /**
     * Sets bass boost strength with smooth ramping to prevent audible pops.
     * Effect remains enabled (hardware passthrough when strength is 0) to prevent
     * AudioFlinger filter disconnection pops.
     */
    fun setBassBoost(strength: Short) {
        val targetStrength = strength.coerceIn(0, 1000)
        bassBoostStrength = targetStrength
        onPreferencesChanged?.invoke(isEnabled, bassBoostStrength, virtualizerStrength)

        val bb = bassBoost ?: return

        if (!bb.enabled && isEnabled && targetStrength > 0) {
            runCatching { bb.enabled = true }
        }

        val currentLive = liveBassStrength.toInt()
        val delta = Math.abs(targetStrength.toInt() - currentLive)

        // Small changes can be applied directly without audible artifacts
        if (delta < 80) {
            bassRampJob?.cancel()
            liveBassStrength = targetStrength
            runCatching { bb.setStrength(targetStrength) }
            return
        }

        // Large jump: smooth ramp over ~60ms to prevent pop
        bassRampJob?.cancel()
        bassRampJob = rampScope.launch {
            val startStrength = liveBassStrength.toInt()
            val endStrength = targetStrength.toInt()
            val steps = 5
            val stepDelayMs = 12L

            for (step in 1..steps) {
                if (!isActive) return@launch
                val interpolated = startStrength + ((endStrength - startStrength) * step) / steps
                val clampedStrength = interpolated.coerceIn(0, 1000).toShort()
                liveBassStrength = clampedStrength
                runCatching { bb.setStrength(clampedStrength) }
                delay(stepDelayMs)
            }

            liveBassStrength = targetStrength
            runCatching { bb.setStrength(targetStrength) }
        }
    }

    fun getBassBoost(): Short = bassBoostStrength

    /**
     * Sets virtualizer strength with smooth ramping to prevent audible pops.
     * Effect remains enabled (hardware passthrough when strength is 0) to prevent
     * AudioFlinger filter disconnection pops.
     */
    fun setVirtualizer(strength: Short) {
        val targetStrength = strength.coerceIn(0, 1000)
        virtualizerStrength = targetStrength
        onPreferencesChanged?.invoke(isEnabled, bassBoostStrength, virtualizerStrength)

        val virt = virtualizer ?: return

        if (!virt.enabled && isEnabled && targetStrength > 0) {
            runCatching { virt.enabled = true }
        }

        val currentLive = liveVirtualizerStrength.toInt()
        val delta = Math.abs(targetStrength.toInt() - currentLive)

        // Small changes can be applied directly without audible artifacts
        if (delta < 80) {
            virtualizerRampJob?.cancel()
            liveVirtualizerStrength = targetStrength
            runCatching { virt.setStrength(targetStrength) }
            return
        }

        // Large jump: smooth ramp over ~60ms to prevent pop
        virtualizerRampJob?.cancel()
        virtualizerRampJob = rampScope.launch {
            val startStrength = liveVirtualizerStrength.toInt()
            val endStrength = targetStrength.toInt()
            val steps = 5
            val stepDelayMs = 12L

            for (step in 1..steps) {
                if (!isActive) return@launch
                val interpolated = startStrength + ((endStrength - startStrength) * step) / steps
                val clampedStrength = interpolated.coerceIn(0, 1000).toShort()
                liveVirtualizerStrength = clampedStrength
                runCatching { virt.setStrength(clampedStrength) }
                delay(stepDelayMs)
            }

            liveVirtualizerStrength = targetStrength
            runCatching { virt.setStrength(targetStrength) }
        }
    }

    fun getVirtualizer(): Short = virtualizerStrength

    fun applyPreset(presetLevels: List<Short>) {
        presetLevels.forEachIndexed { index, level ->
            setBandLevel(index.toShort(), level)
        }
    }

    private fun releaseInternal() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { bassBoost?.release() }
        bassBoost = null
        runCatching { virtualizer?.release() }
        virtualizer = null
    }

    @Synchronized
    fun release() {
        bassRampJob?.cancel()
        virtualizerRampJob?.cancel()
        currentAudioSessionId = 0
        releaseInternal()
    }
}
