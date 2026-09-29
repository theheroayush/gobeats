package com.example.gobeats.core.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val rawText: String,
    val source: String = "local"
) {
    companion object {
        val EMPTY = LyricsData(
            isSynced = false,
            lines = emptyList(),
            rawText = "",
            source = "none"
        )
    }
}

object LrcParser {
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val HEADER_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*\]$""")

    fun parse(content: String, source: String = "local"): LyricsData {
        if (content.isBlank()) return LyricsData.EMPTY

        val timedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()
        var foundAnyTimestamp = false

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || HEADER_TAG_REGEX.matches(line)) {
                return@forEach
            }

            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isNotEmpty()) {
                foundAnyTimestamp = true
                val textOnly = TIMESTAMP_REGEX.replace(line, "").trim()
                if (textOnly.isNotEmpty() || matches.size == 1) {
                    for (match in matches) {
                        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                        val fractionStr = match.groupValues[3]
                        val ms = when (fractionStr.length) {
                            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                            3 -> fractionStr.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        val timeMs = (minutes * 60 + seconds) * 1000 + ms
                        timedLines.add(LyricLine(timeMs, textOnly))
                    }
                }
            } else {
                plainLines.add(line)
            }
        }

        return if (foundAnyTimestamp && timedLines.isNotEmpty()) {
            val sorted = timedLines.sortedBy { it.timeMs }
            LyricsData(
                isSynced = true,
                lines = sorted,
                rawText = content,
                source = source
            )
        } else {
            val fallbackLines = plainLines.map { LyricLine(-1L, it) }
            LyricsData(
                isSynced = false,
                lines = fallbackLines,
                rawText = content,
                source = source
            )
        }
    }

    fun getActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lines.indices) {
            val line = lines[i]
            if (line.timeMs in 0..currentPositionMs) {
                activeIndex = i
            } else if (line.timeMs > currentPositionMs) {
                break
            }
        }
        return activeIndex
    }
}
