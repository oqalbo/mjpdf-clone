// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.actions.ToolbarItems
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TrailRulesTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-38")
    fun aJumpToThePageYouAreAlreadyOnRecordsNothing() {
        openAlpha {
            hist.goToPageWithoutMoving(1)

            hist.assertBackStackStaysEmpty()
            assertEquals("a no-op jump must not move the reader", 0 as Int?, hist.reader.readState().page)

            hist.openActionsDialog()
            assertFalse("a no-op jump must leave Jump Back disabled", hist.tileIsEnabled(navBack()))
            hist.dismissActionsDialog()
        }
    }

    @Test
    @TestId("HIST-39")
    fun revisitingAPageAlreadyInTheTrailDoesNotDuplicateIt() {
        openAlpha {
            hist.jumpToPage(5)
            hist.jumpToPage(1)
            hist.jumpToPage(11)

            val rows = hist.openNavigationHistory().awaitRowCount(3)
            assertEquals(
                "the trail holds a single row for page one",
                1,
                rows.count { it.title == page(1) },
            )
            assertEquals(
                "the trail holds a single row for page five",
                1,
                rows.count { it.title == page(5) },
            )
            assertEquals("the page just reached is the current one", page(11), rows[0].title)
        }
    }

    @Test
    @TestId("HIST-40")
    @TestDuration(DurationCategory.LONG)
    fun theBackStackIsCappedAtFiftyEntries() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.GO_TO_PAGE.id)
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.LARGE)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(0)
            ToolbarItems.awaitLabel(ToolbarItems.primaryId, TestEnvironment.appString(R.string.go_to_page))

            val visited = mutableListOf(0)
            for (jump in 1..JUMPS) {
                val pageIndex = jump * PAGE_STEP
                ToolbarItems.tapPrimary()
                hist.enterGoToPage(pageIndex + 1)
                hist.reader.awaitPageIndex(pageIndex)
                visited += pageIndex
            }

            hist.awaitBackStackSize(CAP)
            val backStack = hist.backStackPages()
            assertEquals(
                "the oldest entries are dropped once the cap is reached",
                visited.take(JUMPS).drop(JUMPS - CAP),
                backStack,
            )
            assertEquals(
                "the first surviving entry is the origin the sixth jump recorded",
                visited[JUMPS - CAP],
                backStack.first(),
            )
            assertFalse("the page the reading started on is gone", backStack.contains(0))
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

    private fun navBack(): String = TestEnvironment.targetContext.getString(R.string.nav_back)

    private companion object {
        const val JUMPS = 55
        const val CAP = 50
        const val PAGE_STEP = 8
    }
}
