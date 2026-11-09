// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity

object ViewSettings {

    const val MAX_SEARCH_SWIPES = 12
    const val MAX_BACK_PRESSES = 5

    fun openFromReader() {
        ViewActionsDialog.openAndTap(R.string.settings)
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    fun openPage(pageTitleRes: Int) {
        rowWithText(pageTitleRes).click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapRow(titleRes: Int) {
        rowWithText(titleRes).click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun returnToReader() {
        repeat(MAX_BACK_PRESSES) {
            if (TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null) return
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.uiDevice.waitForIdle()
        }
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun rowWithText(titleRes: Int): UiObject2 {
        val label = ViewUi.string(titleRes)
        return Await.untilNotNull("the settings row \"$label\" is on screen") {
            TestEnvironment.uiDevice.findObject(By.text(label)) ?: scrollToward(label)
        }
    }

    private fun scrollToward(label: String): UiObject2? {
        runCatching {
            val list = UiScrollable(UiSelector().scrollable(true))
            list.setMaxSearchSwipes(MAX_SEARCH_SWIPES)
            list.scrollIntoView(UiSelector().text(label))
        }
        TestEnvironment.uiDevice.waitForIdle()
        return TestEnvironment.uiDevice.findObject(By.text(label))
    }
}
