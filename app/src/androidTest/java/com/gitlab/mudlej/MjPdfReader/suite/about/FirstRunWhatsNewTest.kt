// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.IntroRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.WhatsNewRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.RealOnboarding
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RealOnboarding
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class FirstRunWhatsNewTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val intro = IntroRobot()
    private val about = AboutRobot()
    private val whatsNew = WhatsNewRobot()

    @Test
    @TestId("ABOUT-18")
    @TestDuration(DurationCategory.SHORT)
    fun whatsNewFollowsTheIntroOnAFirstInstall() {
        home.startFromLauncher()
        intro.awaitShown()
        assertTrue(
            "the release notes flag must be armed while the intro is up",
            Onboarding.showFeaturesDialog(),
        )

        intro.tapSkip()

        Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
        whatsNew.awaitShown()

        Screens.assertTopIs(WhatsNewActivity::class.java)
        assertEquals(
            "the release notes action bar title",
            string(R.string.whats_new_title),
            Screens.titleOfResumed(),
        )
        assertFalse(
            "the release notes flag must be consumed once the screen has been launched",
            Onboarding.showFeaturesDialog(),
        )
    }

    @Test
    @TestId("ABOUT-19")
    fun whatsNewShowsExactlyOnceOnAFirstInstall() {
        ActivityWatcher().use { firstLaunch ->
            home.startFromLauncher()
            intro.awaitShown()
            intro.tapSkip()
            Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
            whatsNew.awaitShown()
            assertTrue(
                "the release notes must be observed on the first install, or the relaunch proves nothing",
                firstLaunch.reached(WhatsNewActivity::class.java, Stage.RESUMED),
            )
            whatsNew.pressUp()
            Screens.awaitTopIs(HomeActivity::class.java)
            home.awaitRendered(Await.LONG_TIMEOUT_MS)
        }

        Onboarding.finishAppTask()

        ActivityWatcher().use { relaunch ->
            home.startFromLauncher(Await.LONG_TIMEOUT_MS)
            Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
            home.awaitRendered(Await.LONG_TIMEOUT_MS)
            Onboarding.assertNothingResumedFor("no release notes screen appears on the relaunch") {
                TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) != null
            }

            relaunch.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)

            assertFalse("showFeaturesDialog must stay consumed", Onboarding.showFeaturesDialog())
            assertEquals(
                "lastSeenVersionCode must stay at the installed version",
                BuildConfig.VERSION_CODE,
                Onboarding.lastSeenVersionCode(),
            )

            openWhatsNewFromAbout()
            assertTrue(
                "the watcher must be able to see the release notes resume, or its silence above means nothing",
                relaunch.reached(WhatsNewActivity::class.java, Stage.RESUMED),
            )
        }
    }

    private fun openWhatsNewFromAbout() {
        home.openOverflowMenu()
        home.tapMenuRow(R.id.aboutRow)
        Screens.awaitResumed(AboutActivity::class.java)
        about.awaitShown()
        about.openRow(R.id.whatsNewRow)
        Screens.awaitResumed(WhatsNewActivity::class.java)
        whatsNew.awaitShown()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
