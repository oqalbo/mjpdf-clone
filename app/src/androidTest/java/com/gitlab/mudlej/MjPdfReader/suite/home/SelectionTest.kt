// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SelectionTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-42")
    @TestDuration(DurationCategory.SHORT)
    fun longPressStartsSelectionAndSwapsInTheSelectionToolbar() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
            BookSeed(HomeFixtures.BETA, pages = 1),
            BookSeed(HomeFixtures.GAMMA, pages = 1),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta", "gamma"))

        grid.cell("alpha").longPress()
        home.awaitSelectionStarted()
        grid.cell("beta").click()

        home.awaitSelectionTitle(selectedCount(2))
        assertTrue("the long pressed cell is selected", grid.cell("alpha").isChecked())
        assertTrue("the tapped cell joins the selection", grid.cell("beta").isChecked())
        assertTrue("an untouched cell stays unselected", !grid.cell("gamma").isChecked())

        assertEquals(
            "the Library selection offers status and hide only",
            listOf(R.id.statusBatchOption, R.id.hideBatchOption),
            home.selectionMenuIds(),
        )
    }

    @Test
    @TestId("HOME-43")
    fun theSelectionMenuChangesWithTheTab() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pages = 1, lastOpened = daysAgo(1)),
        )

        home.launchFromLauncher()

        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitContains("alpha")
        recent.cell("alpha").longPress()
        home.awaitSelectionStarted()
        assertEquals(
            "Recent offers status and remove from recent",
            listOf(R.id.statusBatchOption, R.id.removeRecentBatchOption),
            home.selectionMenuIds(),
        )
        home.pressBack()
        home.awaitSelectionEnded()

        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openTestFolder(rules.testDirectory)
        folders.awaitContains("alpha")
        folders.cell("alpha").longPress()
        home.awaitSelectionStarted()
        assertEquals(
            "Folders offers status and delete",
            listOf(R.id.statusBatchOption, R.id.deleteBatchOption),
            home.selectionMenuIds(),
        )
        home.pressBack()
        home.awaitSelectionEnded()

        home.selectTab(HomeTab.LIBRARY)
        home.openSearch()
        home.typeSearchQuery("alp")
        home.awaitSearchTitles(listOf("alpha"))
        home.longPressSearchResult("alpha")

        home.awaitActionModeShown()
        assertTrue(
            "search starts an action mode rather than the selection toolbar",
            !home.isSelectionToolbarShown(),
        )
        val expectedSearchActions = listOf(R.id.statusBatchOption, R.id.deleteBatchOption)
        val searchActions = Await.untilValue(
            condition = "the search action mode exposes status and delete only",
            read = home::actionModeMenuIds,
        ) { it == expectedSearchActions }
        assertEquals("Search offers status and delete only", expectedSearchActions, searchActions)
    }

    @Test
    @TestId("HOME-44")
    fun selectionEndsOnBackOnTabChangeAndWhenTheLastItemIsDeselected() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pages = 1),
            BookSeed(HomeFixtures.GAMMA, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta", "gamma"))

        grid.cell("alpha").longPress()
        home.awaitSelectionStarted()
        home.pressBack()
        home.awaitSelectionEnded()
        assertNothingChecked(grid)

        grid.cell("alpha").longPress()
        home.awaitSelectionStarted()
        home.selectTab(HomeTab.RECENT)
        home.awaitSelectionEnded()
        assertNothingChecked(grid)

        home.selectTab(HomeTab.LIBRARY)
        grid.cell("alpha").longPress()
        home.awaitSelectionStarted()
        grid.cell("alpha").click()
        home.awaitSelectionEnded()
        assertNothingChecked(grid)
    }

    private fun assertNothingChecked(page: HomePage) {
        Await.until("every visible cell reports itself unselected") {
            page.titles().none { page.cell(it).isChecked() }
        }
    }

    private fun selectedCount(count: Int): String =
        TestEnvironment.targetContext.getString(R.string.home_selected_count, count)
}
