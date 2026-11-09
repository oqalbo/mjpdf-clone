// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class RecentTabTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()
    private val reader = ReaderRobot()

    @Test
    @TestId("HOME-08")
    fun recentListsOpenedBooksNewestFirstAndExcludesUnopenedOnes() {
        HomeBaselines.recordsOnly(
            BookSeed(HomeFixtures.ALPHA, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, lastOpened = daysAgo(3)),
            BookSeed(HomeFixtures.GAMMA, lastOpened = daysAgo(10)),
            BookSeed(HomeFixtures.APPLE),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)

        recent.awaitTitles(listOf("alpha", "beta", "gamma"))
        assertTrue("a book that was never opened must not appear in Recent", !recent.hasItem("Apple"))
        assertEquals(
            "Recent filters for display only, so every seeded row must survive",
            4,
            HomeLibrary.recordCount(),
        )
    }

    @Test
    @TestId("HOME-09")
    fun continueReadingCarouselCapsAtSixBooks() {
        val library = HomeBaselines.recordsOnly(
            BookSeed(HomeFixtures.ALPHA, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, lastOpened = daysAgo(1)),
            BookSeed(HomeFixtures.GAMMA, lastOpened = daysAgo(2)),
            BookSeed(HomeFixtures.APPLE, lastOpened = daysAgo(3)),
            BookSeed(HomeFixtures.BANANA, lastOpened = daysAgo(4)),
            BookSeed(HomeFixtures.CHERRY, lastOpened = daysAgo(5)),
            BookSeed(HomeFixtures.FILENAME_DIFFERS, lastOpened = daysAgo(6)),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)

        recent.awaitItemCount(7)
        assertEquals("the carousel is a shortlist of six", 6, recent.heroItems().size)
        assertEquals(
            "the carousel leads with the most recently opened book",
            library["alpha"].fileName,
            recent.heroTitles().first(),
        )
    }

    @Test
    @TestId("HOME-23")
    fun readingABookMovesItToTheTopOfRecent() {
        val library = HomeBaselines.recordsOnly(
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pages = 1, lastOpened = daysAgo(3)),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitTitles(listOf("alpha", "beta"))

        val generationBeforeReading = home.generation()
        recent.cell("beta").click()
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()

        home.pressBack()
        Screens.awaitTopIs(HomeActivity::class.java)
        home.awaitGenerationAtLeast(generationBeforeReading + 1)

        val alpha = HomeLibrary.requireRecord(library.hashOf("alpha"))
        val beta = HomeLibrary.awaitOpenedAfter(library.hashOf("beta"), alpha.lastOpened)
        assertTrue(
            "the book just read must carry the newer lastOpened, read ${beta.lastOpened} against ${alpha.lastOpened}",
            beta.lastOpened > alpha.lastOpened,
        )

        recent.awaitTitles(listOf(BETA_DOCUMENT_TITLE, "alpha"))
        Await.untilValue(
            "the carousel leads with the book just read",
            read = { recent.heroTitles().firstOrNull() },
        ) { it == BETA_DOCUMENT_TITLE }
    }

    private companion object {
        const val BETA_DOCUMENT_TITLE = "Beta"
    }
}
