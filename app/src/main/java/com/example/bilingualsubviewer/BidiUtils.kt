package com.example.bilingualsubviewer

import android.text.BidiFormatter
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics

object BidiUtils {

    private val formatter = BidiFormatter.getInstance()

    /**
     * Formats each subtitle line independently.
     *
     * A subtitle may contain a German line followed by a Persian line.
     * Applying RTL to the whole subtitle block makes the German line RTL as well.
     * FIRSTSTRONG_LTR lets Android determine the direction of each individual line
     * from its first strong directional character, while keeping mixed text stable.
     */
    fun format(text: String): String {
        if (text.isBlank()) return text

        return text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n', limit = -1)
            .joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val direction: TextDirectionHeuristic =
                        TextDirectionHeuristics.FIRSTSTRONG_LTR
                    formatter.unicodeWrap(line, direction)
                }
            }
    }
}
