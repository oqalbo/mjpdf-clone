// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.filteredShortcutBarActionIds
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.orderedShortcutBarActions
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.selectedShortcutBarActionIds

fun showShortcutBarButtonsPreferenceDialog(
    context: Context,
    preferences: Preferences,
    onSaved: () -> Unit,
) {
    val rows = shortcutBarButtonRows(preferences)
    showActionSelectionPreferenceDialog(
        context = context,
        titleRes = R.string.shortcut_bar_buttons,
        rows = rows,
        defaultRows = defaultShortcutBarButtonRows(),
    ) {
        preferences.setShortcutBarActions(selectedShortcutBarActionIds(rows.enabledActionIds()))
        preferences.setShortcutBarActionOrder(filteredShortcutBarActionIds(rows.actionIds()))
        onSaved()
    }
}

private fun shortcutBarButtonRows(preferences: Preferences): MutableList<ActionSelectionRow> {
    val selectedIds = selectedShortcutBarActionIds(preferences.getShortcutBarActions())
    val orderedActions = orderedShortcutBarActions(preferences.getShortcutBarActionOrder())
    return orderedActions.map { action ->
        ActionSelectionRow(
            action,
            enabled = selectedIds.contains(action.id),
        )
    }.toMutableList()
}

private fun defaultShortcutBarButtonRows(): List<ActionSelectionRow> {
    return ConfigurableAction.defaultShortcutBarOrder.map { action ->
        ActionSelectionRow(
            action,
            enabled = ConfigurableAction.defaultShortcutBarActionIds.contains(action.id),
        )
    }
}
