// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R

enum class HighlightPalette(
    val colorValue: Int,
    @StringRes val labelRes: Int,
    @StringRes val nameRes: Int,
) {
    YELLOW(0xFFFFF176.toInt(), R.string.highlight_yellow, R.string.color_yellow),
    ORANGE(0xFFFFB74D.toInt(), R.string.highlight_orange, R.string.color_orange),
    PINK_RED(0xFFF06292.toInt(), R.string.highlight_pink_red, R.string.color_pink_red),
    BLUE(0xFF64B5F6.toInt(), R.string.highlight_blue, R.string.color_blue),
    GREEN(0xFF81C784.toInt(), R.string.highlight_green, R.string.color_green),
    PURPLE(0xFFB39DDB.toInt(), R.string.highlight_purple, R.string.color_purple),
    TEAL(0xFF4DB6AC.toInt(), R.string.highlight_teal, R.string.color_teal),
    BROWN(0xFFA1887F.toInt(), R.string.highlight_brown, R.string.color_brown),
    CYAN(0xFF4DD0E1.toInt(), R.string.highlight_cyan, R.string.color_cyan),
    RED(0xFFE57373.toInt(), R.string.highlight_red, R.string.color_red),
    GRAY(0xFFBDBDBD.toInt(), R.string.highlight_gray, R.string.color_gray);

    companion object {
        val defaultSelection = listOf(YELLOW, ORANGE, GREEN, BLUE, PINK_RED, PURPLE)

        val noteHighlight = GRAY

        val selectable = entries - GRAY

        fun fromName(value: String): HighlightPalette? = entries.firstOrNull { it.name == value }

        fun fromColor(colorValue: Int): HighlightPalette? = entries.firstOrNull { it.colorValue == colorValue }
    }
}
