package org.lumina.player.core.lyrics

data class LyricWord(
    val word: String,
    val startMs: Long,
    val durationMs: Long
)

data class LyricLine(
    val startMs: Long,
    val content: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList()
)

object LrcParser {

    private val TIME_REGEX = "\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]".toRegex()

    /**
     * Parses raw LRC format lyric text into structured LyricLine models.
     */
    fun parse(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val lines = lrcContent.lines()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[offset:")
            ) {
                continue
            }

            val matches = TIME_REGEX.findAll(line).toList()
            if (matches.isEmpty()) continue

            // Extract clean lyric text content
            val lastMatch = matches.last()
            val text = line.substring(lastMatch.range.last + 1).trim()

            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues.getOrNull(3) ?: "0"
                val ms = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val timeMs = min * 60_000L + sec * 1_000L + ms
                if (text.isNotBlank()) {
                    result.add(LyricLine(startMs = timeMs, content = text))
                }
            }
        }

        return result.sortedBy { it.startMs }
    }

    /**
     * Calculates active lyric line index using binary search based on playback position.
     */
    fun findCurrentIndex(lines: List<LyricLine>, currentPositionMs: Long, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val adjustedPosition = currentPositionMs + offsetMs
        if (adjustedPosition < lines.first().startMs) return -1

        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val line = lines[mid]
            if (line.startMs <= adjustedPosition) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return result
    }
}
