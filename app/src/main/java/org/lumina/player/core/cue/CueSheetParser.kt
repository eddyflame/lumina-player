package org.lumina.player.core.cue

import org.lumina.player.core.model.AudioFormatType
import org.lumina.player.core.model.Song
import java.io.File

/**
 * 纯 Kotlin 高性能 CUE Sheet 状态机解析器
 *
 * 将整轨音频（如单文件 FLAC / APE + .cue）在内存中解析为精确的独立虚拟分轨曲目
 */
object CueSheetParser {

    data class CueTrack(
        val trackNumber: Int,
        val title: String,
        val performer: String,
        val startMs: Long,
        val endMs: Long = 0L
    )

    data class CueSheet(
        val albumTitle: String,
        val albumPerformer: String,
        val audioFileName: String,
        val tracks: List<CueTrack>
    )

    /**
     * 解析 CUE 文本内容
     */
    fun parse(cueContent: String): CueSheet {
        var albumTitle = ""
        var albumPerformer = ""
        var audioFileName = ""
        val tracks = mutableListOf<CueTrack>()

        var currentTrackNumber = 0
        var currentTrackTitle = ""
        var currentTrackPerformer = ""
        var currentTrackIndexMs = 0L

        val lines = cueContent.lines()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("PERFORMER ", ignoreCase = true) -> {
                    val value = extractQuotedOrRest(trimmed.substring(10))
                    if (currentTrackNumber == 0) {
                        albumPerformer = value
                    } else {
                        currentTrackPerformer = value
                    }
                }
                trimmed.startsWith("TITLE ", ignoreCase = true) -> {
                    val value = extractQuotedOrRest(trimmed.substring(6))
                    if (currentTrackNumber == 0) {
                        albumTitle = value
                    } else {
                        currentTrackTitle = value
                    }
                }
                trimmed.startsWith("FILE ", ignoreCase = true) -> {
                    audioFileName = extractQuotedOrRest(trimmed.substring(5).substringBeforeLast(" "))
                }
                trimmed.startsWith("TRACK ", ignoreCase = true) -> {
                    // 保存上一首曲目
                    if (currentTrackNumber > 0) {
                        tracks.add(
                            CueTrack(
                                trackNumber = currentTrackNumber,
                                title = currentTrackTitle.ifBlank { "Track $currentTrackNumber" },
                                performer = currentTrackPerformer.ifBlank { albumPerformer },
                                startMs = currentTrackIndexMs
                            )
                        )
                    }
                    val parts = trimmed.split("\\s+".toRegex())
                    currentTrackNumber = parts.getOrNull(1)?.toIntOrNull() ?: (tracks.size + 1)
                    currentTrackTitle = ""
                    currentTrackPerformer = albumPerformer
                    currentTrackIndexMs = 0L
                }
                trimmed.startsWith("INDEX 01 ", ignoreCase = true) -> {
                    val timeStr = trimmed.substring(9).trim()
                    currentTrackIndexMs = parseCueTimestamp(timeStr)
                }
            }
        }

        // 保存最后一首
        if (currentTrackNumber > 0) {
            tracks.add(
                CueTrack(
                    trackNumber = currentTrackNumber,
                    title = currentTrackTitle.ifBlank { "Track $currentTrackNumber" },
                    performer = currentTrackPerformer.ifBlank { albumPerformer },
                    startMs = currentTrackIndexMs
                )
            )
        }

        // 计算每首歌曲的 endMs
        val resolvedTracks = tracks.mapIndexed { index, track ->
            val nextTrack = tracks.getOrNull(index + 1)
            val endMs = nextTrack?.startMs ?: 0L
            track.copy(endMs = endMs)
        }

        return CueSheet(
            albumTitle = albumTitle,
            albumPerformer = albumPerformer,
            audioFileName = audioFileName,
            tracks = resolvedTracks
        )
    }

    /**
     * 将 CUE 分轨转为虚拟曲目列表并绑定物理音频文件
     */
    fun createVirtualSongs(cueSheet: CueSheet, audioFile: File, totalAudioDurationMs: Long): List<Song> {
        val baseFormat = AudioFormatType.fromExtension(audioFile.name)
        val baseUri = "file://${audioFile.absolutePath}"

        return cueSheet.tracks.mapIndexed { index, track ->
            val actualEndMs = if (track.endMs > 0L) track.endMs else totalAudioDurationMs
            val duration = (actualEndMs - track.startMs).coerceAtLeast(0L)

            Song(
                id = audioFile.absolutePath.hashCode().toLong() * 1000L + track.trackNumber,
                uri = "$baseUri#cue_${track.trackNumber}",
                title = track.title,
                artist = track.performer.ifBlank { cueSheet.albumPerformer }.ifBlank { "Unknown Artist" },
                album = cueSheet.albumTitle.ifBlank { audioFile.nameWithoutExtension },
                albumId = cueSheet.albumTitle.hashCode().toLong(),
                durationMs = duration,
                filePath = audioFile.absolutePath,
                fileSize = audioFile.length(),
                trackNumber = track.trackNumber,
                audioFormat = baseFormat,
                isVirtualCueTrack = true,
                cueStartMs = track.startMs,
                cueEndMs = actualEndMs
            )
        }
    }

    /**
     * 解析 mm:ss:ff 时间戳（75帧 = 1秒）
     */
    private fun parseCueTimestamp(timeStr: String): Long {
        val parts = timeStr.split(":")
        if (parts.size != 3) return 0L
        val min = parts[0].toLongOrNull() ?: 0L
        val sec = parts[1].toLongOrNull() ?: 0L
        val frame = parts[2].toLongOrNull() ?: 0L
        val frameMs = (frame * 1000L) / 75L
        return (min * 60L * 1000L) + (sec * 1000L) + frameMs
    }

    private fun extractQuotedOrRest(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed.trim('"')
        }
    }
}
