// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.SegmentedButtonStyler
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.google.android.material.button.MaterialButtonToggleGroup

object ThemeChoiceStrip {

    fun bind(
        group: MaterialButtonToggleGroup,
        selectedMode: String,
        onModeSelected: (String) -> Unit,
    ) {
        group.clearOnButtonCheckedListeners()
        group.check(selectedMode.toButtonId())
        SegmentedButtonStyler.style(group)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            SegmentedButtonStyler.style(group)
            onModeSelected(checkedId.toThemeMode())
        }
    }

    private fun String.toButtonId(): Int {
        return when (this) {
            Preferences.themeDark -> R.id.theme_choice_dark
            Preferences.themeLight -> R.id.theme_choice_light
            else -> R.id.theme_choice_system
        }
    }

    private fun Int.toThemeMode(): String {
        return when (this) {
            R.id.theme_choice_dark -> Preferences.themeDark
            R.id.theme_choice_light -> Preferences.themeLight
            else -> Preferences.themeSystem
        }
    }
}
