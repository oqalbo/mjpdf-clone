// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction

object ActionLabels {

    val stateDependentActions: Set<ConfigurableAction> = setOf(
        ConfigurableAction.CROP_MARGINS,
        ConfigurableAction.DUAL_PAGE,
        ConfigurableAction.SWITCH_THEME,
        ConfigurableAction.BOOKMARK_PAGE,
        ConfigurableAction.INCOGNITO,
    )

    fun of(action: ConfigurableAction): String {
        if (action in stateDependentActions) {
            throw IllegalArgumentException(
                "${action.id} carries a different title depending on reader state, so reading it from the enum " +
                    "would silently accept either wording. Call the function that names the state instead: " +
                    "cropMargins, dualPage, switchTheme, bookmark or incognito."
            )
        }
        if (action == ConfigurableAction.NONE) {
            throw IllegalArgumentException(
                "ConfigurableAction.NONE resolves to no configured action at all, so it has no button label. " +
                    "A slot configured to NONE is asserted absent, not asserted by label."
            )
        }
        return string(action.titleRes)
    }

    fun cropMargins(cropped: Boolean): String =
        string(if (cropped) R.string.show_margins else R.string.crop_margins)

    fun dualPage(enabled: Boolean): String =
        string(if (enabled) R.string.dual_page_off_action else R.string.dual_page_mode_title)

    fun switchTheme(pdfDarkTheme: Boolean): String =
        string(if (pdfDarkTheme) R.string.switch_to_light_mode else R.string.switch_to_dark_mode)

    fun bookmark(currentPageBookmarked: Boolean): String =
        string(if (currentPageBookmarked) R.string.remove_bookmark else R.string.add_bookmark)

    fun incognito(active: Boolean): String =
        string(if (active) R.string.incognito_exit else R.string.incognito)

    fun row(action: ConfigurableAction): String = string(action.titleRes)

    fun toggleShortcuts(): String = string(R.string.toggle_shortcuts)

    fun exitFullscreen(): String = string(R.string.exit)

    fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
