// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class GridColumnsRule : TestWatcher() {

    override fun finished(description: Description) {
        TestEnvironment.defaultPreferences
            .edit()
            .putInt(Preferences.goToPageGridColumnsKey, BookmarksFixtures.GRID_DEFAULT_COLUMNS)
            .commit()
    }
}
