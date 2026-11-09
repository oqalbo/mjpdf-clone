// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import org.junit.Assert.*
import org.junit.Test
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction

class ActionSelectionMoveTest {
    private fun rows() = mutableListOf(
        ActionSelectionRow(ConfigurableAction.EXIT_FULLSCREEN, false, locked = true),
        ActionSelectionRow(ConfigurableAction.SEARCH, true),
        ActionSelectionRow(ConfigurableAction.SETTINGS, false),
        ActionSelectionRow(ConfigurableAction.ROTATE, true),
    )

    @Test fun lockedRowsStartEnabledAndRemainIncludedEvenIfDisabledLater() {
        val rows = rows()
        assertTrue(rows.first().enabled)
        rows.first().enabled = false
        assertEquals(setOf("exit_fullscreen", "search", "rotate"), rows.enabledActionIds())
    }

    @Test fun movingIntoOrOutOfLockedIndexLeavesOrderUnchanged() {
        val rows = rows()
        assertFalse(rows.move(0, 2))
        assertFalse(rows.move(2, 0))
        assertEquals(listOf("exit_fullscreen", "search", "settings", "rotate"), rows.actionIds())
    }

    @Test fun nonAdjacentMoveShiftsIntermediateRowsInBothDirections() {
        val rows = rows()
        assertTrue(rows.move(1, 3))
        assertEquals(listOf("exit_fullscreen", "settings", "rotate", "search"), rows.actionIds())
        assertTrue(rows.move(3, 1))
        assertEquals(listOf("exit_fullscreen", "search", "settings", "rotate"), rows.actionIds())
    }

    @Test fun invalidEndpointsDoNotModifyRows() {
        val rows = rows()
        assertFalse(rows.move(-1, 1))
        assertFalse(rows.move(1, rows.size))
        assertEquals(listOf("exit_fullscreen", "search", "settings", "rotate"), rows.actionIds())
    }
}
