// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class GoToPageEntryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-37")
    fun theShortcutBarGoToPageEntryOpensTheDialogAndRunsTheJump() {
        SettingsSeeder.seed(Preferences.secondBarEnabledKey, true)

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL))
        ).use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            navigation.tapShortcutBarAction(TestEnvironment.targetContext.getString(R.string.go_to_page))
            navigation.enterGoToPage(9)
            reader.awaitPageIndex(8)
            reader.awaitMotionSettled()

            assertEquals("the shortcut bar jump lands on page nine", 8 as Int?, reader.readState().page)
        }
    }
}
