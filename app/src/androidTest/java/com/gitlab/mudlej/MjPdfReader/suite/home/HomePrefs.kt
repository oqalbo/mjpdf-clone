// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSortOrder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.ui.home.ListFilter

object HomePrefs {

    fun homeTab(): String? = string(Preferences.homeTabKey)

    fun seedFilter(filter: ListFilter) = SettingsSeeder.seed(Preferences.listFilterKey, filter.name)

    fun seedSort(sort: HomeSortOrder) = SettingsSeeder.seed(Preferences.homeSortKey, sort.name)

    fun seedViewMode(mode: HomeViewMode) = SettingsSeeder.seed(Preferences.homeViewModeKey, mode.name)

    fun awaitString(key: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference $key reads \"$expected\"",
            timeoutMs,
            read = { string(key) },
        ) { it == expected }
    }

    fun awaitBoolean(key: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference $key reads $expected",
            timeoutMs,
            read = { SettingsSeeder.preferences.all[key] },
        ) { it == expected }
    }

    private fun string(key: String): String? = SettingsSeeder.preferences.all[key] as? String
}
