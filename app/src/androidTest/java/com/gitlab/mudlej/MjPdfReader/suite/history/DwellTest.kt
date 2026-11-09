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
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.navigation.ReaderHistoryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class DwellTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-41")
    fun aLongStopOnAPageRecordsAReadingStopEntry() {
        seedDwellThreshold(SHORT_THRESHOLD_MS)

        openAlpha {
            hist.awaitDwellPast(SHORT_THRESHOLD_MS)

            hist.reader.swipeForward()
            hist.reader.awaitPageIndex(1)

            val rows = hist.openNavigationHistory().awaitRowCount(2)
            assertEquals("the page just reached is the current one", page(2), rows[0].title)
            assertEquals("the page that was read is below it", page(1), rows[1].title)
            assertTrue(
                "a page you actually read is labelled as a reading stop, read \"${rows[1].subtitle}\"",
                rows[1].subtitle.startsWith(readingStopPrefix()),
            )
        }
    }

    @Test
    @TestId("HIST-42")
    fun aShortStopOnAPageRecordsNothing() {
        seedDwellThreshold(PRODUCTION_THRESHOLD_MS)

        openAlpha {
            repeat(QUICK_TURNS) { hist.turnPageForward() }

            hist.assertBackStackStaysEmpty()

            hist.openActionsDialog()
            assertFalse("flipping through pages must not build a trail", hist.tileIsEnabled(navBack()))
            hist.dismissActionsDialog()
        }
    }

    @Test
    @TestId("HIST-43")
    fun thePageYouJumpToIsNotRecordedAsAReadingStop() {
        seedDwellThreshold(SHORT_THRESHOLD_MS)

        openAlpha {
            hist.jumpToPage(10)
            hist.awaitDwellPast(SHORT_THRESHOLD_MS)

            hist.reader.swipeForward()
            hist.reader.awaitPageIndex(10)

            val rows = hist.openNavigationHistory().awaitRowCount(3)
            assertEquals("the page just reached is the current one", page(11), rows[0].title)
            assertEquals("the page that was read sits below it", page(10), rows[1].title)
            assertTrue(
                "the page that was read is labelled as a reading stop, read \"${rows[1].subtitle}\"",
                rows[1].subtitle.startsWith(readingStopPrefix()),
            )
            assertEquals("the page the jump started from is last", page(1), rows[2].title)
            assertTrue(
                "the jump origin keeps its own label, read \"${rows[2].subtitle}\"",
                rows[2].subtitle.startsWith(goToPagePrefix()),
            )
            assertEquals(
                "arriving by a jump must not record the same page twice",
                1,
                rows.count { it.title == page(10) },
            )
        }
    }

    private fun seedDwellThreshold(thresholdMs: Long) {
        SettingsSeeder.seed(ReaderHistoryManager.debugDwellThresholdKey, thresholdMs)
    }

    private fun openAlpha(block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)
            block()
        }
    }

    private fun page(oneBasedPage: Int) = NavigationHistoryScreen.pageLabel(oneBasedPage)

    private fun readingStopPrefix() = NavigationHistoryScreen.originPrefix(R.string.history_origin_dwell)

    private fun goToPagePrefix() = NavigationHistoryScreen.originPrefix(R.string.go_to_page)

    private fun navBack(): String = TestEnvironment.targetContext.getString(R.string.nav_back)

    private companion object {
        const val SHORT_THRESHOLD_MS = 2_000L
        const val PRODUCTION_THRESHOLD_MS = 30_000L
        const val QUICK_TURNS = 4
    }
}
