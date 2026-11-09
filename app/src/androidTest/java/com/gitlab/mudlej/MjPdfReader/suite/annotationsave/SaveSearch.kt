// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchActivity

object SaveSearch {

    const val RESULT_ROW_ID = "resultText"

    fun runSearchOnCurrentDocument(query: String) {
        SaveActions.runFromActionsDialog(R.string.search)

        val robot = SearchRobot()
        robot.awaitSearchDialog(Await.LONG_TIMEOUT_MS)
        robot.typeQuery(query)
        robot.submit()

        openResultsList()
        Screens.awaitResumed(SearchActivity::class.java, Await.LONG_TIMEOUT_MS)

        SearchIdle.awaitFinished(Await.VERY_LONG_TIMEOUT_MS)
        clickFirstResult()

        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        TestEnvironment.waitForIdleSync()
    }

    private fun openResultsList() {
        val device = TestEnvironment.uiDevice
        val label = TestEnvironment.targetContext.getString(R.string.results)
        val banner = device.wait(Until.findObject(By.text(label)), Await.LONG_TIMEOUT_MS)
            ?: throw ConditionTimeoutException(
                "The reader never showed the \"$label\" search banner, visible text was " +
                    device.findObjects(By.pkg(TestEnvironment.targetPackage)).mapNotNull { it.text }
            )
        banner.click()
        device.waitForIdle()
    }

    private fun clickFirstResult() {
        val device = TestEnvironment.uiDevice
        val row = device.wait(
            Until.findObject(By.res(TestEnvironment.targetPackage, RESULT_ROW_ID)),
            Await.LONG_TIMEOUT_MS,
        ) ?: throw ConditionTimeoutException(
            "The search produced no result row to open, visible text was " +
                device.findObjects(By.pkg(TestEnvironment.targetPackage)).mapNotNull { it.text }
        )
        row.click()
        device.waitForIdle()
    }
}
