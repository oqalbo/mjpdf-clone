// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import org.junit.Assert.*
import org.junit.Test
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette

class HighlightColorSelectionTest {
    private val defaults = HighlightPalette.defaultSelection.map { it.colorValue }

    @Test fun capRefusesAnotherColorButAllowsRemovingASelectedColor() {
        val selection = HighlightColorSelection(defaults)
        val extra = selection.rows.single { it.color == HighlightPalette.TEAL }
        assertFalse(selection.toggle(extra))
        assertEquals(6, selection.appliedColors.size)
        assertTrue(selection.canApply)
        assertTrue(selection.toggle(selection.rows.first { it.enabled }))
        assertEquals(5, selection.appliedColors.size)
        assertFalse(selection.canApply)
        assertTrue(selection.toggle(extra))
        assertTrue(selection.canApply)
    }

    @Test fun unknownOrDuplicateStoredColorsLeaveApplyDisabled() {
        listOf(defaults.dropLast(1) + 0, defaults.dropLast(1) + defaults.first()).forEach { stored ->
            val selection = HighlightColorSelection(stored)
            assertEquals(5, selection.appliedColors.size)
            assertFalse(selection.canApply)
        }
    }

    @Test fun preferencesRepairMalformedStoredColorsBeforeCreatingSelection() {
        val preferences = Preferences(InMemorySharedPreferences(mapOf(
            Preferences.highlightColorsKey to "TEAL,TEAL,unknown",
        )))
        val selection = HighlightColorSelection(preferences.getHighlightColors())
        assertTrue(selection.canApply)
        assertEquals(listOf(HighlightPalette.YELLOW, HighlightPalette.ORANGE, HighlightPalette.PINK_RED,
            HighlightPalette.BLUE, HighlightPalette.GREEN, HighlightPalette.TEAL), selection.appliedColors)
    }

    @Test fun appliedColorsUsePaletteOrderRegardlessOfStoredOrder() {
        val selection = HighlightColorSelection(defaults.reversed())
        assertEquals(listOf(HighlightPalette.YELLOW, HighlightPalette.ORANGE, HighlightPalette.PINK_RED,
            HighlightPalette.BLUE, HighlightPalette.GREEN, HighlightPalette.PURPLE), selection.appliedColors)
    }

    @Test fun resetRestoresDefaultMembershipAndAllowsApply() {
        val selection = HighlightColorSelection(listOf(HighlightPalette.TEAL.colorValue))
        selection.reset()
        assertTrue(selection.canApply)
        assertEquals(HighlightPalette.defaultSelection.toSet(), selection.appliedColors.toSet())
    }
}
