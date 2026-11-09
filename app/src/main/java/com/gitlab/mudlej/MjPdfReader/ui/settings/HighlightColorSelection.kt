// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette

internal data class HighlightColorRow(val color: HighlightPalette, var enabled: Boolean)

internal class HighlightColorSelection(stored: List<Int>) {
    private val selected = stored.mapNotNull(HighlightPalette::fromColor).toSet()
    val rows = HighlightPalette.selectable.map { HighlightColorRow(it, it in selected) }
    val canApply: Boolean get() = rows.count { it.enabled } == Preferences.highlightColorsCount
    val appliedColors: List<HighlightPalette> get() = rows.filter { it.enabled }.map { it.color }

    fun toggle(row: HighlightColorRow): Boolean {
        val target = !row.enabled
        if (target && rows.count { it.enabled } >= Preferences.highlightColorsCount) return false
        row.enabled = target
        return true
    }

    fun reset() {
        rows.forEach { it.enabled = it.color in HighlightPalette.defaultSelection }
    }
}
