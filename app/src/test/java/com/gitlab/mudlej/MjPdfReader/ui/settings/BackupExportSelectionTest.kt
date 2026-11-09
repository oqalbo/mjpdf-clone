// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import org.junit.Assert.*
import org.junit.Test

class BackupExportSelectionTest {
    @Test fun turningHistoryOffClearsPasswordsAndDisablesTheirRow() {
        val selection = BackupExportSelection()
        selection.togglePasswords()
        assertTrue(selection.result().includePasswords)
        selection.toggleHistory()
        assertFalse(selection.passwords)
        assertFalse(selection.passwordsEnabled)
        assertFalse(selection.result().includeHistory)
        selection.togglePasswords()
        assertFalse(selection.result().includePasswords)
        selection.toggleHistory()
        assertTrue(selection.passwordsEnabled)
        assertFalse(selection.result().includePasswords)
    }

    @Test fun exportRequiresSettingsOrHistory() {
        val selection = BackupExportSelection()
        selection.toggleHistory()
        assertTrue(selection.canExport)
        selection.toggleSettings()
        assertFalse(selection.canExport)
        selection.toggleHistory()
        assertTrue(selection.canExport)
        assertFalse(selection.result().includeSettings)
        assertTrue(selection.result().includeHistory)
    }

    @Test fun resultsIncludePasswordsOnlyWithHistoryAcrossToggleSequences() {
        for (sequence in 0 until 64) {
            val selection = BackupExportSelection()
            repeat(6) { step ->
                if (sequence and (1 shl step) == 0) selection.toggleHistory() else selection.togglePasswords()
                assertEquals(selection.history && selection.passwords, selection.result().includePasswords)
            }
        }
    }
}
