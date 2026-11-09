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
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.AboutRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.IntroRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.WhatsNewRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.about.AboutActivity
import com.gitlab.mudlej.MjPdfReader.ui.about.WhatsNewActivity
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ReplayIntroTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val home = HomeRobot()
    private val intro = IntroRobot()
    private val about = AboutRobot()
    private val whatsNew = WhatsNewRobot()

    @Test
    @TestId("ABOUT-41")
    fun replayIntroReopensTheIntroAndDoesNotReshowWhatsNew() {
        Onboarding.seedSettled()

        ActivityWatcher().use { watcher ->
            about.openFromHome(home)
            about.openRow(R.id.replayIntroRow)

            Screens.awaitResumed(MainIntroActivity::class.java)
            intro.awaitShown()
            intro.awaitPage(0, IntroRobot.pageTitle(0))

            val last = IntroRobot.EXPECTED_PAGE_COUNT - 1
            intro.advanceTo(last)
            intro.awaitPage(last, IntroRobot.pageTitle(last))
            assertEquals(
                "the replayed intro must end on its Get started button",
                string(R.string.intro_get_started),
                intro.nextButtonText(),
            )

            intro.tapNext()

            Screens.awaitTopIs(AboutActivity::class.java)
            Screens.awaitGone(MainIntroActivity::class.java)
            Onboarding.assertNothingResumedFor("no release notes screen follows the replayed intro") {
                TestEnvironment.resumedActivityOfType(WhatsNewActivity::class.java) != null
            }

            watcher.assertNeverReached(WhatsNewActivity::class.java, Stage.RESUMED)

            assertFalse("replaying the intro must not rearm firstInstall", Onboarding.firstInstall())
            assertFalse(
                "replaying the intro must not rearm the release notes flag",
                Onboarding.showFeaturesDialog(),
            )
            assertEquals(
                "replaying the intro must not touch the seen version code",
                BuildConfig.VERSION_CODE,
                Onboarding.lastSeenVersionCode(),
            )

            about.openRow(R.id.whatsNewRow)
            Screens.awaitResumed(WhatsNewActivity::class.java)
            whatsNew.awaitShown()
            assertTrue(
                "the watcher must be able to see the release notes resume, or its silence above means nothing",
                watcher.reached(WhatsNewActivity::class.java, Stage.RESUMED),
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
