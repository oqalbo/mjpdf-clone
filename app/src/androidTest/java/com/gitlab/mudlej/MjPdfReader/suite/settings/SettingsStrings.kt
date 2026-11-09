// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage

object SettingsStrings {

    fun of(@StringRes resId: Int): String = TestEnvironment.targetContext.getString(resId)

    fun of(@StringRes resId: Int, vararg args: Any): String =
        TestEnvironment.targetContext.getString(resId, *args)

    fun plural(@PluralsRes resId: Int, quantity: Int, vararg args: Any): String =
        TestEnvironment.targetContext.resources.getQuantityString(resId, quantity, *args)

    fun pageTitle(page: SettingsPage): String = of(page.titleRes)

    fun pageSummary(page: SettingsPage): String = of(page.summaryRes)

    fun overriddenBy(@StringRes reasonRes: Int): String = of(R.string.overridden_by, of(reasonRes))

    fun hideDelayLabel(millis: Int): String = plural(R.plurals.hide_delay_seconds, millis / 1000, millis / 1000)

    fun titleLinesLabel(lines: Int): String = plural(R.plurals.home_title_lines, lines, lines)

    fun currentValue(value: Int): String = of(R.string.settings_current_value, value)
}
