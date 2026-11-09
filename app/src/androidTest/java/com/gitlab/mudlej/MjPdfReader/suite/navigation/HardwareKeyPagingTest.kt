// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HardwareKeyPagingTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-18")
    fun theVolumeKeysPageWhenTheSettingIsOn() {
        SettingsSeeder.seed(Preferences.turnPageByVolumeButtonsKey, true)

        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)
            val volumeBefore = NavigationAudio.primeAllMidway()

            NavigationKeys.pressVolumeDown()
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()
            assertEquals("volume down turns forward", 1 as Int?, reader.readState().page)

            NavigationKeys.pressVolumeUp()
            reader.awaitPageIndex(0)
            reader.awaitMotionSettled()
            assertEquals("volume up turns back", 0 as Int?, reader.readState().page)

            NavigationAudio.assertAllLevelsStay(volumeBefore)
        }
    }

    @Test
    @TestId("NAV-19")
    fun theVolumeKeysDoNotPageByDefault() {
        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            NavigationKeys.pressVolumeDownThroughShell(2)
            reader.awaitMotionSettled()

            assertEquals("the default must not page on a volume key", 0 as Int?, reader.readState().page)
        }
    }

    @Test
    @TestId("NAV-20")
    @TestDuration(DurationCategory.NORMAL)
    fun volumePagingHoldsAtTheLastPage() {
        SettingsSeeder.seed(Preferences.turnPageByVolumeButtonsKey, true)

        launchNormal().use {
            val ready = navigation.awaitReady()
            val pageCount = ready.pageCount ?: throw AssertionError("The reader reports no page count")

            navigation.jumpToPage(pageCount)
            reader.awaitPageIndex(pageCount - 1)

            NavigationKeys.pressVolumeDown(OVERRUN_PRESSES)
            reader.awaitMotionSettled()

            assertEquals("the reader stays on the last page", (pageCount - 1) as Int?, reader.readState().page)
            val hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, pageCount - 1)
        }
    }

    @Test
    @TestId("NAV-21")
    fun theMouseForwardAndBackButtonsTurnPages() {
        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            val forwardHandled = NavigationKeys.mouseForwardButton(navigation.activity())
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()
            assertEquals("the mouse forward button turns forward", 1 as Int?, reader.readState().page)

            val backHandled = NavigationKeys.mouseBackButton(navigation.activity())
            reader.awaitPageIndex(0)
            reader.awaitMotionSettled()
            assertEquals("the mouse back button turns back", 0 as Int?, reader.readState().page)

            assertTrue("the forward button event must be consumed", forwardHandled)
            assertTrue("the back button event must be consumed", backHandled)
        }
    }

    @Test
    @TestId("NAV-22")
    @TestDuration(DurationCategory.NORMAL)
    fun theMouseBackButtonDoesNotLeaveTheReader() {
        launchNormal().use {
            navigation.awaitReady()
            navigation.jumpToPage(3)
            reader.awaitPageIndex(2)

            val handled = NavigationKeys.mouseBackKey(navigation.activity())
            reader.awaitPageIndex(1)
            reader.awaitMotionSettled()

            assertTrue("the mouse back key must be consumed by the pager", handled)
            assertEquals("the mouse back key turns back one page", 1 as Int?, reader.readState().page)
            Screens.assertTopIs(MainActivity::class.java)
            reader.awaitShown()
            Snackbars.assertNoneWithin()
        }
    }

    @Test
    @TestId("NAV-23")
    fun mousePagingOffLeavesTheButtonsAlone() {
        SettingsSeeder.seed(Preferences.turnPageByMouseButtonsKey, false)

        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            val handled = NavigationKeys.mouseForwardButton(navigation.activity())
            reader.awaitMotionSettled()

            assertEquals("no page turn while mouse paging is off", 0 as Int?, reader.readState().page)
            assertFalse("the event must be passed on rather than consumed", handled)
        }
    }

    @Test
    @TestId("NAV-53")
    fun doubleBackToExitRequiresConfirmationOnlyWhenEnabled() {
        SettingsSeeder.seed(Preferences.doubleTapToExitEnabledKey, true)

        launchNormal().use {
            navigation.awaitReady()

            TestEnvironment.uiDevice.pressBack()

            Screens.awaitTopIs(MainActivity::class.java)
            Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.press_back_again))

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitGone(MainActivity::class.java)
        }

        SettingsSeeder.remove(Preferences.doubleTapToExitEnabledKey)
        launchNormal().use {
            navigation.awaitReady()

            TestEnvironment.uiDevice.pressBack()

            Screens.awaitGone(MainActivity::class.java)
        }
    }

    private fun launchNormal(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)))

    private companion object {
        const val OVERRUN_PRESSES = 5
    }
}
