package org.lumina.player.core.model

import java.io.File

/**
 * 音频编码格式类型
 */
enum class AudioFormatType(val displayName: String, val isLossless: Boolean, val isHiRes: Boolean = false) {
    MP3("MP3", isLossless = false),
    AAC("AAC", isLossless = false),
    OGG("OGG", isLossless = false),
    OPUS("Opus", isLossless = false),
    WMA("WMA", isLossless = false),
    FLAC("FLAC", isLossless = true, isHiRes = true),
    ALAC("ALAC", isLossless = true, isHiRes = true),
    WAV("WAV", isLossless = true, isHiRes = true),
    AIFF("AIFF", isLossless = true, isHiRes = true),
    APE("APE", isLossless = true, isHiRes = true),
    WV("WavPack", isLossless = true, isHiRes = true),
    DSD("DSD", isLossless = true, isHiRes = true),
    UNKNOWN("AUDIO", isLossless = false);

    companion object {
        fun fromExtension(path: String): AudioFormatType {
            val ext = path.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp3" -> MP3
                "aac" -> AAC
                "m4a" -> AAC // 可以后续通过解码元数据细分为 ALAC 或 AAC
                "ogg" -> OGG
                "opus" -> OPUS
                "wma" -> WMA
                "flac" -> FLAC
                "wav" -> WAV
                "aiff", "aif" -> AIFF
                "ape" -> APE
                "wv" -> WV
                "dsf", "dff" -> DSD
                else -> UNKNOWN
            }
        }
    }
}

/**
 * 完整技术参数详情（用于发烧友参数展示面板）
 */
data class AudioTechnicalInfo(
    val format: AudioFormatType = AudioFormatType.UNKNOWN,
    val codecName: String = "",
    val bitDepth: Int = 16,
    val sampleRate: Int = 44100,
    val bitrateKbps: Int = 320,
    val channels: Int = 2,
    val channelConfiguration: String = "Stereo",
    val containerFormat: String = "",
    val filePath: String = "",
    val fileSizeBytes: Long = 0L,
    val isHiRes: Boolean = false
) {
    val sampleRateDisplay: String
        get() = when {
            sampleRate >= 1000000 -> String.format("%.1f MHz", sampleRate / 1000000.0)
            sampleRate >= 1000 -> String.format("%.1f kHz", sampleRate / 1000.0)
            else -> "$sampleRate Hz"
        }

    val bitDepthDisplay: String
        get() = if (format == AudioFormatType.DSD) "1-bit Direct Stream" else "$bitDepth-bit"

    val formattedFileSize: String
        get() {
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            return String.format("%.2f MB", mb)
        }
}
