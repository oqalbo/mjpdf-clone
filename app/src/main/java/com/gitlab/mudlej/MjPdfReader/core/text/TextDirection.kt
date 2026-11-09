// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.text

object TextDirection {

    private const val NONE = 0
    private const val LTR = 1
    private const val RTL = 2

    fun isRtlBaseDirection(text: String): Boolean {
        var first = NONE
        var ltrCount = 0
        var rtlCount = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> {
                    if (first == NONE) first = LTR
                    ltrCount++
                }
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> {
                    if (first == NONE) first = RTL
                    rtlCount++
                }
            }
        }
        return when (first) {
            RTL -> true
            LTR -> rtlCount > ltrCount
            else -> false
        }
    }
}
