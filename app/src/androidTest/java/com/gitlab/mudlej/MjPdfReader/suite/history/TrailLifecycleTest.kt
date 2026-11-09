// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TrailLifecycleTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-34")
    fun openingAnotherDocumentClearsTheNavigationHistory() {
        TestEnvironment.targetContext.startActivity(
            HistoryReader.viewIntent(HistoryFixtures.ALPHA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        try {
            hist.awaitReady()
            hist.jumpToPage(10)
            hist.jumpToPage(15)
            hist.awaitBackStack(listOf(0, 9))

            hist.openInPlace(HistoryFixtures.uri(HistoryFixtures.BETA))
            hist.reader.awaitState("the second document is loaded") {
                it.pageCount == HistoryFixtures.BETA_PAGES
            }
            hist.reader.awaitRenderIdle()

            hist.awaitBackStack(emptyList())
            assertTrue(
                "the forward stack must be cleared with the document too, it holds ${hist.forwardStackPages()}",
                hist.forwardStackPages().isEmpty(),
            )

            hist.openActionsDialog()
            assertFalse("Jump Back must be disabled after the document change", hist.tileIsEnabled(navBack()))
            assertFalse(
                "Navigation History must be disabled after the document change",
                hist.tileIsEnabled(navHistory()),
            )
            assertEquals(
                "a disabled tile renders at the disabled alpha",
                HistoryReader.DISABLED_ALPHA,
                hist.tileAlpha(navBack()),
                HistoryReader.ALPHA_TOLERANCE,
            )
            hist.dismissActionsDialog()
        } finally {
            runCatching {
                TestEnvironment.onMainThread { hist.activity().finishAndRemoveTask() }
                Screens.awaitGone(MainActivity::class.java)
            }
        }
    }

    @Test
    @TestId("HIST-35")
    fun navigationHistorySurvivesRotation() {
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.jumpToPage(8)
            hist.jumpToPage(14)

            TestEnvironment.uiDevice.setOrientationLeft()
            hist.reader.awaitState("the reader view reconfigures to a landscape shape") {
                val width = it.viewWidth ?: 0
                val height = it.viewHeight ?: 0
                width > height
            }
            hist.reader.awaitRenderIdle()

            val screen = hist.openNavigationHistory()
            val rows = screen.awaitRowCount(3)
            assertEquals("the current page survives the turn", page(14), rows[0].title)
            assertEquals("the most recent origin survives the turn", page(8), rows[1].title)
            assertEquals("the oldest origin survives the turn", page(1), rows[2].title)
            screen.close()

            hist.jumpBack()
            hist.reader.awaitPageIndex(7)
        }
    }

    @Test
    @TestId("HIST-37")
    fun aFreshReaderSessionStartsWithEmptyNavigationHistory() {
        var hash = ""
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hash = hist.reader.awaitFileHashResolved()
            hist.jumpToPage(10)
            Records.awaitStoredPage(hash, 9)
        }

        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()

            hist.reader.awaitPageIndex(9)
            hist.assertBackStackStaysEmpty()

            hist.openActionsDialog()
            assertFalse("a fresh session offers no Jump Back", hist.tileIsEnabled(navBack()))
            assertFalse("a fresh session offers no Navigation History", hist.tileIsEnabled(navHistory()))
            hist.dismissActionsDialog()
        }
    }

    private fun page(oneBasedPage: Int) = NavigationHistoryScreen.pageLabel(oneBasedPage)

    private fun navBack(): String = string(R.string.nav_back)

    private fun navHistory(): String = string(R.string.navigation_history)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
