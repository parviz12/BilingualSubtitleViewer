package com.example.bilingualsubviewer

object SubtitleParser {

    private val timeRegex =
        Regex("""(\d{2}):(\d{2}):(\d{2}),([0-9]{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),([0-9]{3})""")

    fun parse(content: String): List<Subtitle> {
        val normalized = content
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()

        if (normalized.isEmpty()) return emptyList()

        // Do not use Regex.split() here. On some Android/Kotlin combinations
        // it can internally pass a negative limit to java.util.regex.Pattern.
        // Normalize blank-line separators to a simple character first.
        val separator = '\u0000'
        val blockText = normalized.replace(Regex("\\n[\\t ]*\\n+"), separator.toString())
        val blocks = blockText.split(separator).filter { it.isNotBlank() }

        val result = mutableListOf<Subtitle>()

        for ((position, block) in blocks.withIndex()) {
            val lines = block.lines()
            if (lines.isEmpty()) continue

            var timeLineIndex = -1
            for (i in lines.indices) {
                if (timeRegex.containsMatchIn(lines[i])) {
                    timeLineIndex = i
                    break
                }
            }

            if (timeLineIndex < 0) continue

            val match = timeRegex.find(lines[timeLineIndex]) ?: continue

            val start = parseTime(
                match.groupValues[1], match.groupValues[2],
                match.groupValues[3], match.groupValues[4]
            )
            val end = parseTime(
                match.groupValues[5], match.groupValues[6],
                match.groupValues[7], match.groupValues[8]
            )

            // Ignore malformed timestamps rather than allowing bad input to
            // crash the entire subtitle viewer.
            if (end < start) continue

            val text = lines
                .drop(timeLineIndex + 1)
                .joinToString("\n")
                .trim()

            if (text.isEmpty()) continue

            val index = lines.firstOrNull()
                ?.trim()
                ?.toIntOrNull()
                ?: (position + 1)

            result.add(
                Subtitle(
                    index = index,
                    startTime = start,
                    endTime = end,
                    text = text
                )
            )
        }

        return result
    }

    private fun parseTime(
        hours: String,
        minutes: String,
        seconds: String,
        milliseconds: String
    ): Long {
        return (
            hours.toLong() * 3_600_000L +
            minutes.toLong() * 60_000L +
            seconds.toLong() * 1_000L +
            milliseconds.toLong()
        )
    }
}
