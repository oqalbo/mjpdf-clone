// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.history.NavigationHistoryActivity
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
class NavigationTrailTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-25")
    fun aJumpRecordsTheOriginPageInNavigationHistory() {
        openAlpha {
            hist.jumpToPage(10)
            hist.jumpToPage(15)

            val rows = hist.openNavigationHistory().awaitRowCount(3)

            assertEquals("the current page is at the top", page(15), rows[0].title)
            assertEquals("the current page row says so", currentPageLabel(), rows[0].subtitle)
            assertEquals("the most recent origin is next", page(10), rows[1].title)
            assertTrue(
                "the origin row names the jump that recorded it, read \"${rows[1].subtitle}\"",
                rows[1].subtitle.startsWith(goToPagePrefix()),
            )
            assertEquals("the oldest origin is last", page(1), rows[2].title)
            assertTrue(
                "the oldest origin row names its jump too, read \"${rows[2].subtitle}\"",
                rows[2].subtitle.startsWith(goToPagePrefix()),
            )
        }
    }

    @Test
    @TestId("HIST-26")
    fun theCurrentPageRowIsBoldAndNotTappable() {
        openAlpha {
            hist.jumpToPage(10)

            val screen = hist.openNavigationHistory()
            screen.awaitRowCount(2)
            screen.tapRow(page(10))

            screen.assertStillOpen()
            assertEquals(
                "the Navigation History screen stays open",
                string(R.string.navigation_history),
                Screens.titleOfResumed(),
            )
            assertTrue("the current page row is bold", screen.row(page(10)).bold)
            assertFalse("a back row is not bold", screen.row(page(1)).bold)
        }
    }

    @Test
    @TestId("HIST-27")
    fun jumpBackReturnsToTheOriginAndJumpForwardReturnsToTheTarget() {
        openAlpha {
            hist.jumpToPage(10)

            hist.jumpBack()
            hist.reader.awaitPageIndex(0)

            hist.jumpForward()
            hist.reader.awaitPageIndex(9)

            val rows = hist.openNavigationHistory().awaitRowCount(2)
            assertEquals("the jump target is the current page again", page(10), rows[0].title)
            assertEquals("the origin page sits below it", page(1), rows[1].title)
        }
    }

    @Test
    @TestId("HIST-28")
    fun tappingABackRowJumpsToThatPage() {
        openAlpha {
            hist.jumpToPage(6)
            hist.jumpToPage(12)
            hist.jumpToPage(18)

            val screen = hist.openNavigationHistory()
            screen.awaitRowCount(4)
            screen.tapRow(page(1))

            Screens.awaitGone(NavigationHistoryActivity::class.java)
            hist.reader.awaitPageIndex(0)
            hist.reader.awaitMotionSettled()

            val reopened = hist.openNavigationHistory().awaitRowCount(4)
            assertEquals("the page just left becomes the first forward entry", page(18), reopened[0].title)
            assertEquals("the trail keeps its order below it", page(12), reopened[1].title)
            assertEquals("the trail keeps its order below it", page(6), reopened[2].title)
            assertEquals("the page just reached is the current one", page(1), reopened[3].title)
            assertEquals("and it says so", currentPageLabel(), reopened[3].subtitle)
        }
    }

    @Test
    @TestId("HIST-29")
    fun tappingAForwardRowJumpsForward() {
        openAlpha {
            hist.jumpToPage(6)
            hist.jumpToPage(12)
            hist.jumpBack()
            hist.reader.awaitPageIndex(5)
            hist.jumpBack()
            hist.reader.awaitPageIndex(0)

            val screen = hist.openNavigationHistory()
            val rows = screen.awaitRowCount(3)
            assertEquals("the furthest forward entry is at the top", page(12), rows[0].title)
            assertEquals("the nearer forward entry is next", page(6), rows[1].title)
            assertEquals("the current page is below them", currentPageLabel(), rows[2].subtitle)

            screen.tapRow(page(12))

            Screens.awaitGone(NavigationHistoryActivity::class.java)
            hist.reader.awaitPageIndex(11)
        }
    }

    private fun openAlpha(block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)
            block()
        }
    }

    private fun page(oneBasedPage: Int) = NavigationHistoryScreen.pageLabel(oneBasedPage)

    private fun currentPageLabel() = NavigationHistoryScreen.currentPageLabel()

    private fun goToPagePrefix() = NavigationHistoryScreen.originPrefix(R.string.go_to_page)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
