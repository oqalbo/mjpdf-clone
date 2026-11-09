// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class NavigationHistoryEntryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-44")
    fun navigationHistoryIsReachableWhenOnlyForwardEntriesExist() {
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)

            hist.jumpToPage(10)
            hist.jumpBack()
            hist.reader.awaitPageIndex(0)

            hist.awaitBackStack(emptyList())
            assertEquals(
                "going all the way back leaves one forward entry",
                listOf(9),
                hist.forwardStackPages(),
            )

            hist.openActionsDialog()
            assertTrue("Jump Forward stays available", hist.tileIsEnabled(navForward()))
            assertTrue(
                "Navigation History stays available while the trail contains a forward entry",
                hist.tileIsEnabled(navHistory()),
            )
            assertEquals(
                "the forward-only Navigation History tile renders at full alpha",
                HistoryReader.FULL_ALPHA,
                hist.tileAlpha(navHistory()),
                HistoryReader.ALPHA_TOLERANCE,
            )
            hist.dismissActionsDialog()

            val rows = hist.openNavigationHistory().awaitRowCount(2)
            assertEquals("the forward entry is listed first", page(10), rows[0].title)
            assertEquals("the current page follows the forward entry", page(1), rows[1].title)
        }
    }

    @Test
    @TestId("HIST-45")
    fun navigationHistoryRowsShowTheTableOfContentsPath() {
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.TOC)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)

            hist.jumpToPage(HistoryFixtures.TOC_RESULTS_PAGE + 1)
            hist.jumpToPage(HistoryFixtures.TOC_SECOND_STOP_PAGE + 1)

            val screen = hist.openNavigationHistory()
            screen.awaitRowCount(3)
            val row = screen.awaitPathOn(page(HistoryFixtures.TOC_RESULTS_PAGE + 1))
            assertEquals(
                "the row names the chapter its page sits under",
                HistoryFixtures.TOC_RESULTS_PATH,
                row.path,
            )
            screen.close()
        }

        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)

            hist.jumpToPage(10)
            hist.jumpToPage(15)

            val screen = hist.openNavigationHistory()
            screen.awaitRowCount(3)
            screen.holdEveryPathHidden()
        }
    }

    private fun page(oneBasedPage: Int) = NavigationHistoryScreen.pageLabel(oneBasedPage)

    private fun navForward(): String = string(R.string.nav_forward)

    private fun navHistory(): String = string(R.string.navigation_history)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
