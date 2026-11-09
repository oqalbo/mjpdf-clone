// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.IntroRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.RealOnboarding
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RealOnboarding
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IntroRotationTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val intro = IntroRobot()

    @Test
    @TestId("ABOUT-14")
    @TestDuration(DurationCategory.SHORT)
    fun rotationMidIntroKeepsThePageAndTheDotsInSync() {
        home.startFromLauncher()
        intro.awaitShown()

        intro.advanceTo(MIDDLE_PAGE)
        intro.awaitPage(MIDDLE_PAGE, IntroRobot.pageTitle(MIDDLE_PAGE))
        assertMiddlePageState()

        Rotation.toLandscape(MainIntroActivity::class.java)
        intro.awaitShown(Await.LONG_TIMEOUT_MS)
        assertMiddlePageState()

        Rotation.toPortrait(MainIntroActivity::class.java)
        intro.awaitShown(Await.LONG_TIMEOUT_MS)
        assertMiddlePageState()
    }

    @Test
    @TestId("ABOUT-15")
    fun rotationOnTheLastIntroPageKeepsTheGetStartedState() {
        home.startFromLauncher()
        intro.awaitShown()

        val last = IntroRobot.EXPECTED_PAGE_COUNT - 1
        intro.advanceTo(last)
        intro.awaitPage(last, IntroRobot.pageTitle(last))

        Rotation.toLandscape(MainIntroActivity::class.java)
        intro.awaitShown(Await.LONG_TIMEOUT_MS)

        intro.awaitPage(last, IntroRobot.pageTitle(last), Await.LONG_TIMEOUT_MS)
        assertEquals(
            "the last page must come back reading Get started, not Next",
            string(R.string.intro_get_started),
            intro.nextButtonText(),
        )
        assertEquals(
            "the last page must come back with Skip invisible, not visible",
            View.INVISIBLE,
            intro.skipButtonVisibility(),
        )

        intro.tapNext()
        Screens.awaitGone(MainIntroActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun assertMiddlePageState() {
        intro.awaitPage(MIDDLE_PAGE, IntroRobot.pageTitle(MIDDLE_PAGE), Await.LONG_TIMEOUT_MS)
        assertEquals(
            "the intro page hook must report the restored page",
            MIDDLE_PAGE,
            intro.describedPosition(),
        )
        intro.awaitActiveDot(MIDDLE_PAGE)
        assertEquals(
            "a restored middle page must keep Next as its primary button",
            string(R.string.intro_next),
            intro.nextButtonText(),
        )
        assertEquals(
            "a restored middle page must keep Skip visible",
            View.VISIBLE,
            intro.skipButtonVisibility(),
        )
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val MIDDLE_PAGE = 2
    }
}
