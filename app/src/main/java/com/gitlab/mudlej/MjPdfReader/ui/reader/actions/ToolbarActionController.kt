// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.view.Menu
import android.view.MenuItem
import com.gitlab.mudlej.MjPdfReader.R

class ToolbarActionController(
    private val actionResolver: ConfigurableActionResolver,
    private val primaryActionId: () -> String,
    private val secondaryActionId: () -> String,
) {

    fun update(menu: Menu) {
        configureButton(menu.findItem(R.id.toolbarPrimaryActionOption), primaryActionId())
        configureButton(menu.findItem(R.id.toolbarSecondaryActionOption), secondaryActionId())
        menu.findItem(R.id.readerActionsOption)?.isVisible = true
    }

    fun handle(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toolbarPrimaryActionOption -> actionResolver.perform(primaryActionId())
            R.id.toolbarSecondaryActionOption -> actionResolver.perform(secondaryActionId())
            else -> false
        }
    }

    private fun configureButton(menuItem: MenuItem?, actionId: String) {
        val action = actionResolver.action(actionId)
        if (menuItem == null || action == null || !action.visible) {
            menuItem?.isVisible = false
            return
        }

        menuItem.isVisible = true
        menuItem.setTitle(action.titleRes)
        menuItem.setIcon(action.iconRes)
    }
}
