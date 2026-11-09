// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Rule
import org.junit.rules.RuleChain
import java.io.File

abstract class SettingsTestCase {

    protected val suite = SuiteRules.standard()
    protected val cleanup = SettingsCleanupRule()
    protected val airplaneMode = AirplaneModeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(airplaneMode)
        .around(suite)
        .around(cleanup)

    protected fun launchSettings(): ActivityScenario<SettingsActivity> =
        ActivityScenario.launch<SettingsActivity>(SettingsScreen.intent()).also {
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
        }

    protected fun launchSettingsOn(page: SettingsPage): ActivityScenario<SettingsActivity> =
        ActivityScenario.launch<SettingsActivity>(SettingsScreen.pageIntent(page)).also {
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitTitle(SettingsStrings.pageTitle(page))
        }

    protected fun openPageFromRoot(page: SettingsPage) {
        SettingsScreen.awaitRootList()
        SettingsScreen.openPage(page)
    }

    protected fun backToRoot() {
        SettingsScreen.back()
        SettingsScreen.awaitTitle(SettingsStrings.of(com.gitlab.mudlej.MjPdfReader.R.string.settings))
        SettingsScreen.awaitRootList()
    }

    protected fun leaveSettings() {
        repeat(BACK_PRESS_BUDGET) {
            if (SettingsScreen.activityOrNull() == null) return
            SettingsScreen.back()
        }
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    protected fun testDirectory(): File = suite.testDirectory.directory()

    protected val reader = ReaderRobot()

    protected fun launchReader(
        file: File,
        incognito: Boolean = false,
    ): ActivityScenario<MainActivity> {
        val intent = ReaderRobot.viewIntent(file)
        if (incognito) intent.putExtra(PDF.incognitoKey, true)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        reader.awaitFullQualityRender()
        return scenario
    }

    protected fun swipeToPageAtLeast(pageIndex: Int): ReaderState {
        repeat(SWIPE_BUDGET) {
            val state = reader.readState()
            if ((state.page ?: 0) >= pageIndex) return state
            reader.swipeForward()
            reader.awaitMotionSettled()
        }
        return reader.awaitState(
            "the reader reaches page index $pageIndex",
            Await.LONG_TIMEOUT_MS,
        ) { (it.page ?: 0) >= pageIndex }
    }

    protected fun tapPrimaryToolbarAction() {
        onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    protected fun toggleSwitch(title: String, expected: Boolean) {
        SettingsScreen.tapRow(title)
        SettingsScreen.awaitChecked(title, expected)
    }

    protected fun openChoiceDialog(rowTitle: String) {
        SettingsScreen.tapRow(rowTitle)
        SettingsDialog.awaitChoiceList()
    }

    protected fun waitForIdle() {
        TestEnvironment.waitForIdleSync()
    }

    protected fun openSettingsFromHome() {
        val home = HomeRobot()
        home.launchFromLauncher(Await.LONG_TIMEOUT_MS)
        home.openOverflowMenu()
        home.tapMenuRow(com.gitlab.mudlej.MjPdfReader.R.id.settingsRow)
        SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
    }

    protected companion object {
        const val BACK_PRESS_BUDGET = 6
        const val SWIPE_BUDGET = 12
    }
}
