package com.example.bilingualsubviewer

import android.text.BidiFormatter
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics

object BidiUtils {

    private val formatter = BidiFormatter.getInstance()

    fun format(text: String): String {

        if (text.isBlank()) return text

        val direction: TextDirectionHeuristic =
            if (containsPersianOrArabic(text)) {
                TextDirectionHeuristics.RTL
            } else {
                TextDirectionHeuristics.LTR
            }

        return formatter.unicodeWrap(text, direction)
    }

    private fun containsPersianOrArabic(text: String): Boolean {
        return text.any {
            (it in '\u0600'..'\u06FF') ||
            (it in '\u0750'..'\u077F') ||
            (it in '\u08A0'..'\u08FF') ||
            (it in '\uFB50'..'\uFDFF') ||
            (it in '\uFE70'..'\uFEFF')
        }
    }
}
