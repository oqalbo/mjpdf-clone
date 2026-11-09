// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.orderedFullScreenOverlayActionIds
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.orderedFullScreenOverlayActions
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.selectedFullScreenOverlayActionIds

fun showFullScreenButtonsPreferenceDialog(
    context: Context,
    preferences: Preferences,
    onSaved: () -> Unit,
) {
    val rows = fullScreenButtonRows(preferences)
    showActionSelectionPreferenceDialog(
        context = context,
        titleRes = R.string.fullscreen_buttons,
        rows = rows,
        defaultRows = defaultFullScreenButtonRows(),
    ) {
        preferences.setFullScreenOverlayActions(selectedFullScreenOverlayActionIds(rows.enabledActionIds()))
        preferences.setFullScreenOverlayActionOrder(orderedFullScreenOverlayActionIds(rows.map { it.action.id }))
        onSaved()
    }
}

private fun fullScreenButtonRows(preferences: Preferences): MutableList<ActionSelectionRow> {
    val selectedIds = selectedFullScreenOverlayActionIds(preferences.getFullScreenOverlayActions())
    val orderedActions = orderedFullScreenOverlayActions(preferences.getFullScreenOverlayActionOrder())
    return orderedActions.map { action ->
        ActionSelectionRow(
            action,
            enabled = selectedIds.contains(action.id),
            locked = ConfigurableAction.requiredFullScreenOverlayActionIds.contains(action.id),
        )
    }.toMutableList()
}

private fun defaultFullScreenButtonRows(): List<ActionSelectionRow> {
    return ConfigurableAction.defaultFullScreenOverlayOrder.map { action ->
        ActionSelectionRow(
            action,
            enabled = ConfigurableAction.defaultFullScreenOverlayActionIds.contains(action.id),
            locked = ConfigurableAction.requiredFullScreenOverlayActionIds.contains(action.id),
        )
    }
}
