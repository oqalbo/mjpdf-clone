// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import android.content.DialogInterface
import android.widget.LinearLayout
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.BackupExportOptions
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showBackupExportOptionsDialog(
    context: Context,
    onExport: (BackupExportOptions) -> Unit,
) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    fun checkbox(checked: Boolean) = MaterialCheckBox(context).apply {
        isClickable = false
        isFocusable = false
        isChecked = checked
    }

    fun title(textRes: Int) = TextView(context).apply {
        text = context.getString(textRes)
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        }
    }

    val selection = BackupExportSelection()
    val settingsCheckbox = checkbox(checked = selection.settings)
    val historyCheckbox = checkbox(checked = selection.history)
    val passwordsCheckbox = checkbox(checked = selection.passwords)

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
    }
    val settingsRow = container.selectionDialogRow(listOf(settingsCheckbox, title(R.string.backup_export_option_settings)))
    val historyRow = container.selectionDialogRow(listOf(historyCheckbox, title(R.string.backup_export_option_history)))
    val passwordsRow = container.selectionDialogRow(listOf(passwordsCheckbox, title(R.string.include_saved_passwords)))
    container.addView(settingsRow)
    container.addView(historyRow)
    container.addView(passwordsRow)

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.backup_export_title)
        .setView(container)
        .setPositiveButton(R.string.backup_export_action) { _, _ ->
            onExport(selection.result())
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.show()

    val exportButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
    fun refresh() {
        val historyOn = selection.history
        settingsCheckbox.isChecked = selection.settings
        historyCheckbox.isChecked = selection.history
        passwordsCheckbox.isChecked = selection.passwords
        passwordsCheckbox.isEnabled = selection.passwordsEnabled
        passwordsRow.alpha = if (historyOn) 1f else 0.5f
        exportButton.isEnabled = selection.canExport
    }
    refresh()

    settingsRow.setOnClickListener {
        selection.toggleSettings()
        refresh()
    }
    historyRow.setOnClickListener {
        selection.toggleHistory()
        refresh()
    }
    passwordsRow.setOnClickListener {
        selection.togglePasswords()
        passwordsCheckbox.isChecked = selection.passwords
    }
}
