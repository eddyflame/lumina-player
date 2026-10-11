package org.lumina.player.core.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

/**
 * 均衡器预设
 */
data class EqPreset(
    val id: String,
    val nameResId: Int,
    val bandLevels: List<Short> // 相对 dB 增益 (毫贝尔 mB, 例如 -1000 到 +1000)
)

/**
 * DSP 音效引擎控制器
 */
class AudioDspController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var isEnabled = true
    private var bassBoostStrength: Short = 0
    private var virtualizerStrength: Short = 0
    private var currentBandLevels = mutableListOf<Short>()

    /**
     * 当 ExoPlayer 的 AudioSessionId 发生变化或初始化时附加音效
     */
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0) return

        release()

        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEnabled
                // 恢复之前的频段设置
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
            }

            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isEnabled
                if (strengthSupported) {
                    setStrength(bassBoostStrength)
                }
            }

            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = isEnabled
                if (strengthSupported) {
                    setStrength(virtualizerStrength)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
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
        }
    }

    fun setBassBoost(strength: Short) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.setStrength(bassBoostStrength)
        }
    }

    fun getBassBoost(): Short = bassBoostStrength

    fun setVirtualizer(strength: Short) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        runCatching {
            virtualizer?.setStrength(virtualizerStrength)
        }
    }

    fun getVirtualizer(): Short = virtualizerStrength

    fun applyPreset(presetLevels: List<Short>) {
        presetLevels.forEachIndexed { index, level ->
            setBandLevel(index.toShort(), level)
        }
    }

    fun release() {
        runCatching {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            virtualizer?.release()
            virtualizer = null
        }
    }
}
