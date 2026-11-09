// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.about

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.IntroRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.RealOnboarding
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
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
@TestDuration(DurationCategory.SHORT)
class FirstRunIntroTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val intro = IntroRobot()

    @Test
    @TestId("ABOUT-01")
    fun theIntroAppearsOnAGenuinelyCleanInstall() {
        assertFalse(
            "a genuinely clean install carries no firstInstall entry, so this test is not starting from one",
            Onboarding.contains(Preferences.firstInstallKey),
        )
        assertTrue("firstInstall must read its default of true before the launch", Onboarding.firstInstall())

        startAtFirstIntroPage()

        Screens.assertTopIs(MainIntroActivity::class.java)
        assertEquals(
            "the first intro page must greet the user",
            string(R.string.intro_welcome_title),
            intro.title(0),
        )
        assertEquals("the Skip button label", string(R.string.intro_skip), intro.skipButtonText())
        assertEquals("the Next button label", string(R.string.intro_next), intro.nextButtonText())
        assertEquals(
            "Skip must be visible on the first page",
            View.VISIBLE,
            intro.skipButtonVisibility(),
        )
        assertFalse(
            "the intro must hide the action bar so nothing but the tour is on screen",
            intro.isActionBarShowing(),
        )
    }

    @Test
    @TestId("ABOUT-02")
    fun theFirstInstallWritesTheThreeOnboardingPreferencesBeforeTheIntroOpens() {
        startAtFirstIntroPage()

        assertTrue(
            "firstInstall must be written, not merely read, or the intro can trigger again",
            Onboarding.contains(Preferences.firstInstallKey),
        )
        assertFalse("firstInstall must be armed to false", Onboarding.firstInstall())

        assertTrue(
            "showFeaturesDialog must be written so the release notes follow the intro",
            Onboarding.contains(Preferences.showFeaturesDialogKey),
        )
        assertTrue("showFeaturesDialog must be armed to true", Onboarding.showFeaturesDialog())

        assertTrue(
            "lastSeenVersionCode must be written so a first install is not read as an upgrade",
            Onboarding.contains(Preferences.lastSeenVersionCodeKey),
        )
        assertEquals(
            "lastSeenVersionCode must be stamped with the installed version",
            BuildConfig.VERSION_CODE,
            Onboarding.lastSeenVersionCode(),
        )
    }

    @Test
    @TestId("ABOUT-03")
    fun introPageOneShowsTheFourFeatureRowsAndTheAuthorFooter() {
        startAtFirstIntroPage()

        val previousHome = Screens.liveActivitiesOfType(HomeActivity::class.java).single()
        TestEnvironment.onMainThread { previousHome.recreate() }
        Screens.awaitReplacement(previousHome, HomeActivity::class.java)
        Await.assertThroughout(
            condition = "recreating Home leaves the intro in front until the user finishes it",
            windowMs = SETTLE_WINDOW_MS,
        ) { intro.isShown() }

        val page = intro.awaitSnapshot(0)

        assertEquals(
            "the first intro page title",
            string(R.string.intro_welcome_title),
            page.title?.toString(),
        )
        assertEquals(
            "the four feature rows of the first intro page, in order",
            IntroRobot.firstPageFeatures(),
            page.featureTexts.map { it.toString() },
        )
        assertTrue("the author footer must be visible on the first page", page.footerVisible)
        assertEquals(
            "the author footer text with its anchor stripped",
            FOOTER_TEXT,
            page.footerText?.toString()?.trim(),
        )
        assertTrue(
            "the author footer must carry a link to $AUTHOR_SITE, it carried ${page.footerLinkUrls}",
            AUTHOR_SITE in page.footerLinkUrls,
        )
        assertTrue("release notes remain pending while the recreated Home waits for the intro", Onboarding.showFeaturesDialog())
        intro.tapSkip()
        Screens.awaitResumed(WhatsNewActivity::class.java)
        assertFalse("release notes are consumed after the intro finishes", Onboarding.showFeaturesDialog())
    }

    @Test
    @TestId("ABOUT-04")
    @TestDuration(DurationCategory.NORMAL)
    fun swipingForwardReachesAllFivePagesInOrder() {
        startAtFirstIntroPage()

        assertEquals("the intro must publish five pages", IntroRobot.EXPECTED_PAGE_COUNT, intro.pageCount())

        for (position in 1 until IntroRobot.EXPECTED_PAGE_COUNT) {
            intro.swipeForward()
            intro.awaitPage(position, IntroRobot.pageTitle(position))
        }

        assertEquals(
            "the intro page hook must read the last page",
            IntroRobot.EXPECTED_PAGE_COUNT - 1,
            intro.describedPosition(),
        )
        assertEquals(
            "the intro page hook must report five pages",
            IntroRobot.EXPECTED_PAGE_COUNT,
            intro.describedCount(),
        )

        intro.swipeForward()
        Await.assertThroughout(
            condition = "a swipe past the last page leaves the last page on screen and the intro alive",
            windowMs = SETTLE_WINDOW_MS,
        ) {
            intro.isShown() && intro.pagerStateOrNull()?.currentItem == IntroRobot.EXPECTED_PAGE_COUNT - 1
        }
        assertEquals(
            "a swipe past the end must not finish the intro",
            IntroRobot.pageTitle(IntroRobot.EXPECTED_PAGE_COUNT - 1),
            intro.title(IntroRobot.EXPECTED_PAGE_COUNT - 1),
        )
    }

    @Test
    @TestId("ABOUT-05")
    @TestDuration(DurationCategory.NORMAL)
    fun swipingBackReturnsThroughThePages() {
        startAtFirstIntroPage()

        for (position in 1 until IntroRobot.EXPECTED_PAGE_COUNT) {
            intro.swipeForward()
            intro.awaitPage(position, IntroRobot.pageTitle(position))
        }

        for (position in IntroRobot.EXPECTED_PAGE_COUNT - 2 downTo 0) {
            intro.swipeBack()
            intro.awaitPage(position, IntroRobot.pageTitle(position))
        }

        assertEquals("the intro page hook must be back at the first page", 0, intro.describedPosition())

        val page = intro.awaitSnapshot(0)
        assertTrue(
            "the first page must rebind its footer rather than come back as a blank recycled holder",
            page.footerVisible,
        )
        assertEquals("the rebound footer text", FOOTER_TEXT, page.footerText?.toString()?.trim())
    }

    @Test
    @TestId("ABOUT-06")
    fun nextAdvancesExactlyOnePagePerTap() {
        startAtFirstIntroPage()

        for (position in 1 until IntroRobot.EXPECTED_PAGE_COUNT) {
            intro.tapNext()
            intro.awaitPage(position, IntroRobot.pageTitle(position))
            assertEquals(
                "tap number $position must leave the intro page hook on page $position",
                position,
                intro.describedPosition(),
            )
        }
    }

    @Test
    @TestId("ABOUT-07")
    fun theButtonLabelsAndSkipVisibilityChangeOnlyOnTheLastPage() {
        startAtFirstIntroPage()

        val last = IntroRobot.EXPECTED_PAGE_COUNT - 1
        for (position in 0..last) {
            if (position > 0) {
                intro.tapNext()
                intro.awaitPage(position, IntroRobot.pageTitle(position))
            }
            if (position < last) {
                assertEquals(
                    "Skip must stay visible on page $position",
                    View.VISIBLE,
                    intro.skipButtonVisibility(),
                )
                assertEquals(
                    "the primary button must read Next on page $position",
                    string(R.string.intro_next),
                    intro.nextButtonText(),
                )
            } else {
                assertEquals(
                    "Skip must go INVISIBLE and not GONE on the last page, or the button row shifts",
                    View.INVISIBLE,
                    intro.skipButtonVisibility(),
                )
                assertEquals(
                    "the primary button must read Get started on the last page",
                    string(R.string.intro_get_started),
                    intro.nextButtonText(),
                )
            }
        }
    }

    @Test
    @TestId("ABOUT-08")
    fun thePageDotsFollowTheCurrentPage() {
        startAtFirstIntroPage()

        assertEquals("the intro must draw one dot per page", IntroRobot.EXPECTED_PAGE_COUNT, intro.dotCount())
        assertEquals("only the first dot is active before any tap", listOf(0), intro.activeDotIndexes())

        intro.tapNext()
        intro.awaitPage(1, IntroRobot.pageTitle(1))
        intro.tapNext()
        intro.awaitPage(2, IntroRobot.pageTitle(2))

        intro.awaitActiveDot(2)
        assertEquals(
            "the four dots that are not the current page must all read inactive",
            List(IntroRobot.EXPECTED_PAGE_COUNT) { index ->
                if (index == 2) IntroRobot.DOT_ACTIVE else IntroRobot.DOT_INACTIVE
            },
            intro.dotDescriptions(),
        )
    }

    private fun startAtFirstIntroPage() {
        home.startFromLauncher()
        intro.awaitShown()
        intro.awaitPage(0, IntroRobot.pageTitle(0))
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {

        const val SETTLE_WINDOW_MS = 1_500L
        const val FOOTER_TEXT = "Made by Mudlej"
        const val AUTHOR_SITE = "https://mudlej.com"
    }
}
