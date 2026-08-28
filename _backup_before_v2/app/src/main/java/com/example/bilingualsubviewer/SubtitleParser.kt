package com.example.bilingualsubviewer

object SubtitleParser {

    fun parse(content: String): List<Subtitle> {

        val normalized = content
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val blocks = normalized
            .split(Regex("\n\\s*\n"))
            .filter { it.isNotBlank() }

        val result = mutableListOf<Subtitle>()

        for (block in blocks) {

            val lines = block.lines()

            if (lines.size < 3) {
                continue
            }

            val index = lines[0].trim().toIntOrNull()
                ?: continue

            val timing = lines[1].split("-->")

            if (timing.size != 2) {
                continue
            }

            val start = parseTime(timing[0].trim())
                ?: continue

            val end = parseTime(timing[1].trim())
                ?: continue

            val text = lines
                .drop(2)
                .joinToString("\n")
                .trim()

            if (text.isEmpty()) {
                continue
            }

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

    private fun parseTime(value: String): Long? {

        val normalized = value.replace(',', '.')

        val parts = normalized.split(":")

        if (parts.size != 3) {
            return null
        }

        val hours = parts[0].toLongOrNull()
            ?: return null

        val minutes = parts[1].toLongOrNull()
            ?: return null

        val secondsParts = parts[2].split(".")

        val seconds = secondsParts[0].toLongOrNull()
            ?: return null

        val milliseconds =
            if (secondsParts.size > 1) {
                secondsParts[1]
                    .padEnd(3, '0')
                    .take(3)
                    .toLongOrNull() ?: 0
            } else {
                0
            }

        return hours * 3_600_000L +
                minutes * 60_000L +
                seconds * 1_000L +
                milliseconds
    }
}
