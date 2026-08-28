package com.example.bilingualsubviewer

object SubtitleParser {

    private val timeRegex = Regex(
        """(\d{2}):(\d{2}):(\d{2}),([0-9]{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),([0-9]{3})"""
    )

    fun parse(content: String): List<Subtitle> {
        val normalized = content
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        if (normalized.isBlank()) return emptyList()

        val result = mutableListOf<Subtitle>()
        var blockStart = 0
        var position = 0

        // Manual block scanner. This deliberately avoids String.split()/Regex.split()
        // because some Android/Kotlin runtime combinations can reach java.util.regex
        // with an invalid negative limit while processing large SRT files.
        while (blockStart <= normalized.length) {
            val separator = findBlankLine(normalized, blockStart)
            val blockEnd = if (separator >= 0) separator else normalized.length
            val block = normalized.substring(blockStart, blockEnd).trim()

            if (block.isNotEmpty()) {
                parseBlock(block, position + 1)?.let {
                    result.add(it)
                    position++
                }
            }

            if (separator < 0) break
            blockStart = skipBlankLines(normalized, separator)
        }

        return result
    }

    private fun findBlankLine(text: String, start: Int): Int {
        var i = start
        while (i + 1 < text.length) {
            if (text[i] == '\n' && text[i + 1] == '\n') return i
            i++
        }
        return -1
    }

    private fun skipBlankLines(text: String, separator: Int): Int {
        var i = separator
        while (i < text.length && text[i] == '\n') i++
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

    private fun parseBlock(block: String, fallbackIndex: Int): Subtitle? {
        val lines = block.lines()
        if (lines.isEmpty()) return null

        var timeLineIndex = -1
        var match: MatchResult? = null
        for (i in lines.indices) {
            val candidate = timeRegex.find(lines[i])
            if (candidate != null) {
                timeLineIndex = i
                match = candidate
                break
            }
        }

        val timeMatch = match ?: return null

        val start = parseTime(
            timeMatch.groupValues[1], timeMatch.groupValues[2],
            timeMatch.groupValues[3], timeMatch.groupValues[4]
        )
        val end = parseTime(
            timeMatch.groupValues[5], timeMatch.groupValues[6],
            timeMatch.groupValues[7], timeMatch.groupValues[8]
        )

        if (end < start) return null

        val textBuilder = StringBuilder()
        for (i in (timeLineIndex + 1) until lines.size) {
            if (i > timeLineIndex + 1) textBuilder.append('\n')
            textBuilder.append(lines[i])
        }
        val text = textBuilder.toString().trim()
        if (text.isEmpty()) return null

        val index = lines.firstOrNull()?.trim()?.toIntOrNull() ?: fallbackIndex
        return Subtitle(index, start, end, text)
    }

    private fun parseTime(hours: String, minutes: String, seconds: String, milliseconds: String): Long {
        return hours.toLong() * 3_600_000L +
            minutes.toLong() * 60_000L +
            seconds.toLong() * 1_000L +
            milliseconds.toLong()
    }
}
