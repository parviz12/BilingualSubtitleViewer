package com.example.bilingualsubviewer

import android.view.View

object BidiUtils {

    fun applyDirection(
        view: View,
        text: String
    ) {

        val hasRtl = text.any {

            val direction =
                Character.getDirectionality(it)

            direction ==
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                    direction ==
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        }

        view.textDirection =
            if (hasRtl) {
                View.TEXT_DIRECTION_ANY_RTL
            } else {
                View.TEXT_DIRECTION_LTR
            }
    }
}
