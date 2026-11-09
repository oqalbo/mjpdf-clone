// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import com.gitlab.mudlej.MjPdfReader.data.BackupExportOptions

internal class BackupExportSelection {
    var settings = true
        private set
    var history = true
        private set
    var passwords = false
        private set
    val passwordsEnabled: Boolean get() = history
    val canExport: Boolean get() = settings || history

    fun toggleSettings() { settings = !settings }

    fun toggleHistory() {
        history = !history
        if (!history) passwords = false
    }

    fun togglePasswords() {
        if (history) passwords = !passwords
    }

    fun result() = BackupExportOptions(settings, history, history && passwords)
}
