// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
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
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
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
class IntroCompletionTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val intro = IntroRobot()
    private val about = AboutRobot()
    private val whatsNew = WhatsNewRobot()

    @Test
    @TestId("ABOUT-09")
    @TestDuration(DurationCategory.SHORT)
    fun skipFinishesTheIntroAndLandsOnHome() {
        home.startFromLauncher()
        intro.awaitShown()

        intro.tapSkip()

        dismissPostIntroWhatsNew()
        assertIntroIsGone()
        assertHomeIsUsable()
    }

    @Test
    @TestId("ABOUT-10")
    fun getStartedOnTheLastPageFinishesTheIntro() {
        home.startFromLauncher()
        intro.awaitShown()

        intro.advanceTo(IntroRobot.EXPECTED_PAGE_COUNT - 1)
        intro.awaitPage(
            IntroRobot.EXPECTED_PAGE_COUNT - 1,
            IntroRobot.pageTitle(IntroRobot.EXPECTED_PAGE_COUNT - 1),
        )
        assertEquals(
            "the primary button must read Get started before the finishing tap",
            string(R.string.intro_get_started),
            intro.nextButtonText(),
        )

        intro.tapNext()

        dismissPostIntroWhatsNew()
        assertIntroIsGone()
        assertHomeIsUsable()
    }

    @Test
    @TestId("ABOUT-11")
    @TestDuration(DurationCategory.SHORT)
    fun systemBackDuringTheIntroExitsTheIntroToHome() {
        home.startFromLauncher()
        intro.awaitShown()

        intro.advanceTo(2)
        intro.awaitPage(2, IntroRobot.pageTitle(2))

        intro.pressBack()

        dismissPostIntroWhatsNew()
        assertIntroIsGone()
        assertHomeIsUsable()
    }

    @Test
    @TestId("ABOUT-12")
    fun theIntroDoesNotAppearOnTheSecondLaunch() {
        ActivityWatcher().use { firstLaunch ->
            home.startFromLauncher()
            intro.awaitShown()
            assertTrue(
                "the intro must be observed on the first launch, or the second launch proves nothing",
                firstLaunch.reached(MainIntroActivity::class.java, Stage.RESUMED),
            )
            intro.tapSkip()
            dismissPostIntroWhatsNew()
        }

        Onboarding.finishAppTask()

        ActivityWatcher().use { secondLaunch ->
            home.startFromLauncher(Await.LONG_TIMEOUT_MS)
            Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
            home.awaitRendered(Await.LONG_TIMEOUT_MS)
            Onboarding.assertNothingResumedFor("no intro screen appears on the second launch") {
                TestEnvironment.resumedActivityOfType(MainIntroActivity::class.java) != null
            }

            secondLaunch.assertNeverReached(MainIntroActivity::class.java, Stage.RESUMED)
            assertHomeIsUsable()

            assertFalse("firstInstall must still be false", Onboarding.firstInstall())
            assertFalse("showFeaturesDialog must have been consumed", Onboarding.showFeaturesDialog())

            replayIntroFromAbout()
            assertTrue(
                "the watcher must be able to see the intro resume, or its silence above means nothing",
                secondLaunch.reached(MainIntroActivity::class.java, Stage.RESUMED),
            )
        }
    }

    @Test
    @TestId("ABOUT-13")
    fun abandoningTheIntroStillCountsAsSeen() {
        ActivityWatcher().use { firstLaunch ->
            home.startFromLauncher()
            intro.awaitShown()
            assertTrue(
                "the intro must be observed on the first launch, or the second launch proves nothing",
                firstLaunch.reached(MainIntroActivity::class.java, Stage.RESUMED),
            )
        }

        assertFalse(
            "firstInstall is written before the intro is launched, so abandoning it still counts as seen",
            Onboarding.firstInstall(),
        )
        assertTrue(
            "the release notes flag must still be armed, because the intro never returned to consume it",
            Onboarding.showFeaturesDialog(),
        )

        Onboarding.finishAppTask()

        ActivityWatcher().use { secondLaunch ->
            home.startFromLauncher(Await.LONG_TIMEOUT_MS)
            secondLaunch.awaitReached(HomeActivity::class.java, Stage.RESUMED, Await.LONG_TIMEOUT_MS)
            Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
            whatsNew.awaitShown()

            secondLaunch.assertNeverReached(MainIntroActivity::class.java, Stage.RESUMED)

            whatsNew.pressUp()
            Screens.awaitTopIs(HomeActivity::class.java)
            home.awaitRendered(Await.LONG_TIMEOUT_MS)

            replayIntroFromAbout()
            assertTrue(
                "the watcher must be able to see the intro resume, or its silence above means nothing",
                secondLaunch.reached(MainIntroActivity::class.java, Stage.RESUMED),
            )
        }
    }

    private fun replayIntroFromAbout() {
        home.openOverflowMenu()
        home.tapMenuRow(R.id.aboutRow)
        Screens.awaitResumed(AboutActivity::class.java)
        about.awaitShown()
        about.openRow(R.id.replayIntroRow)
        Screens.awaitResumed(MainIntroActivity::class.java)
        intro.awaitShown()
        intro.tapSkip()
        Screens.awaitTopIs(AboutActivity::class.java)
    }

    private fun dismissPostIntroWhatsNew() {
        Screens.awaitResumed(WhatsNewActivity::class.java, Await.LONG_TIMEOUT_MS)
        whatsNew.awaitShown()
        whatsNew.pressUp()
        Screens.awaitTopIs(HomeActivity::class.java)
    }

    private fun assertIntroIsGone() {
        Screens.awaitGone(MainIntroActivity::class.java)
        assertFalse(
            "the intro must be finished and out of the task once it is done",
            Screens.isInTask(MainIntroActivity::class.java),
        )
    }

    private fun assertHomeIsUsable() {
        Screens.awaitTopIs(HomeActivity::class.java)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        assertEquals(
            "home must offer its three tabs once onboarding is out of the way",
            listOf(
                string(R.string.home_tab_recent),
                string(R.string.home_tab_library),
                string(R.string.home_tab_folders),
            ),
            home.tabTexts(),
        )
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
