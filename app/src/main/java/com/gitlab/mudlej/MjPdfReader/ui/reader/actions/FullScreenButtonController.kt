// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.actions

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.LinearLayout
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.ui.reader.controls.AutoScrollManager
import com.gitlab.mudlej.MjPdfReader.ui.reader.controls.FullScreenOptionsManager
import com.google.android.material.button.MaterialButton

class FullScreenButtonController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val preferences: Preferences,
    private val actionResolver: ConfigurableActionResolver,
    private val fullScreenOptionsManager: FullScreenOptionsManager,
    private val autoScrollManager: AutoScrollManager,
    private val hideBrightnessControl: () -> Unit,
) {

    private val dynamicButtons = mutableMapOf<String, MaterialButton>()

    fun configure() {
        val orderedActions = orderedSelectedFullScreenOverlayActions(
            selectedIds = preferences.getFullScreenOverlayActions(),
            actionOrder = preferences.getFullScreenOverlayActionOrder(),
        )
        val selectedIds = orderedActions.map { it.id }.toSet()
        val fixedButtons = fixedButtons()

        fixedButtons.forEach { (action, button) ->
            button.visibility = if (selectedIds.contains(action.id)) View.VISIBLE else View.GONE
        }

        if (!selectedIds.contains(ConfigurableAction.BRIGHTNESS.id)) {
            hideBrightnessControl()
        }
        if (!selectedIds.contains(ConfigurableAction.AUTO_SCROLL.id)) {
            autoScrollManager.stop()
            autoScrollManager.hideControls()
        }

        val dynamicButtons = configureDynamicButtons(selectedIds)
        arrangeButtons(orderedActions, fixedButtons + dynamicButtons)
        fullScreenOptionsManager.refreshInfo()
    }

    private fun fixedButtons(): Map<ConfigurableAction, MaterialButton> {
        return mapOf(
            ConfigurableAction.EXIT_FULLSCREEN to binding.exitFullScreenButton,
            ConfigurableAction.ROTATE to binding.rotateScreenButton,
            ConfigurableAction.BRIGHTNESS to binding.brightnessButton,
            ConfigurableAction.AUTO_SCROLL to binding.autoScrollButton,
            ConfigurableAction.HORIZONTAL_LOCK to binding.toggleHorizontalSwipeButton,
            ConfigurableAction.ZOOM_LOCK to binding.toggleZoomLockButton,
            ConfigurableAction.SCREENSHOT to binding.screenshotButton,
            ConfigurableAction.TOGGLE_LABELS to binding.toggleLabelButton,
        )
    }

    private fun configureDynamicButtons(selectedIds: Set<String>): Map<ConfigurableAction, MaterialButton> {
        return ConfigurableAction.dynamicFullScreenOverlayActions.associateWith { action ->
            val button = dynamicButtons.getOrPut(action.id) {
                createActionButton(action)
            }
            val configuredAction = actionResolver.action(action)
            if (configuredAction != null) {
                configureActionButton(button, configuredAction)
            }
            button.visibility = if (selectedIds.contains(action.id) && configuredAction?.visible == true) {
                View.VISIBLE
            } else {
                View.GONE
            }
            button
        }
    }

    private fun arrangeButtons(
        orderedActions: List<ConfigurableAction>,
        buttons: Map<ConfigurableAction, MaterialButton>,
    ) {
        orderedActions.mapNotNull { buttons[it] }.forEach { button ->
            binding.fullScreenButtonsList.removeView(button)
            binding.fullScreenButtonsList.addView(button)
        }
    }

    private fun createActionButton(action: ConfigurableAction): MaterialButton {
        val button = MaterialButton(ContextThemeWrapper(context, R.style.ThemeOverlay_FullScreenOverlayButton)).apply {
            val margin = resources.getDimensionPixelSize(R.dimen.fs_button_vertical_margin)
            val padding = resources.getDimensionPixelSize(R.dimen.fs_button_padding)
            val paddingEnd = resources.getDimensionPixelSize(R.dimen.fs_button_padding_end)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, margin, 0, margin)
            }
            iconGravity = MaterialButton.ICON_GRAVITY_START
            iconSize = resources.getDimensionPixelSize(R.dimen.fs_button_size)
            isToggleCheckedStateOnClick = false
            setPaddingRelative(padding, padding, paddingEnd, padding)
            setOnClickListener { actionResolver.perform(action.id) }
        }
        binding.fullScreenButtonsList.addView(button)
        fullScreenOptionsManager.registerFullScreenButton(button, context.getString(action.titleRes))
        return button
    }

    private fun configureActionButton(button: MaterialButton, configuredAction: ConfiguredAction) {
        val title = context.getString(configuredAction.titleRes)
        button.text = title
        button.contentDescription = title
        button.setIconResource(configuredAction.iconRes)
        val checked = configuredAction.checked
        button.isCheckable = checked != null
        if (checked != null) {
            button.isChecked = checked
        }
        fullScreenOptionsManager.registerFullScreenButton(button, title)
    }
}
