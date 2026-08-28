package com.example.bilingualsubviewer

import android.text.BidiFormatter
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics

object BidiUtils {

    private val formatter = BidiFormatter.getInstance()
    private val direction: TextDirectionHeuristic = TextDirectionHeuristics.FIRSTSTRONG_LTR

    /**
     * Apply bidi isolation to each subtitle line independently.
     * German/Latin lines stay LTR; Persian/Arabic lines become RTL.
     * The line separator is handled manually so no negative split limit is used.
     */
    fun format(text: String): String {
        if (text.isEmpty()) return text

        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val output = StringBuilder(normalized.length + 16)
        var start = 0

        while (start <= normalized.length) {
            val end = normalized.indexOf('\n', start)
            val lineEnd = if (end >= 0) end else normalized.length
            val line = normalized.substring(start, lineEnd)

            if (line.isBlank()) {
                output.append(line)
            } else {
                output.append(formatter.unicodeWrap(line, direction))
            }

            if (end < 0) break
            output.append('\n')
            start = end + 1
        }

        return output.toString()
    }
}
