// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HomeSearchTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()
    private val reader = ReaderRobot()

    @Test
    @TestId("HOME-36")
    fun searchFiltersRecordsByTitleIgnoringCaseAndRestoresOnClear() {
        HomeBaselines.recordsOnly(
            BookSeed(HomeFixtures.ALPHA, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, lastOpened = daysAgo(1)),
            BookSeed(HomeFixtures.GAMMA, lastOpened = daysAgo(2)),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        home.openSearch()

        home.typeSearchQuery("ALPH")
        home.awaitSearchTitles(listOf("alpha"))

        home.clearSearchQuery()
        home.awaitSearchTitles(listOf("alpha", "beta", "gamma"))
    }

    @Test
    @TestId("HOME-37")
    fun searchAlsoFindsDeviceFilesThatHaveNoRecord() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.GAMMA, record = false, pages = 1),
        )

        home.launchFromLauncher()

        home.openSearch()

        home.typeSearchQuery("gam")
        home.awaitSearchTitles(listOf("gamma"))

        val records = HomeLibrary.records()
        assertEquals("searching must not create a record, read ${records.map { it.fileName }}", 1, records.size)
        assertEquals("the only record is the one that was seeded", "alpha", records.single().fileName)
    }

    @Test
    @TestId("HOME-38")
    fun openingASearchResultClosesSearchAndOpensTheReader() {
        val library = HomeBaselines.recordsOnly(
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
        )

        home.launchFromLauncher()
        home.awaitRendered()
        home.openSearch()
        home.typeSearchQuery("alp")
        home.awaitSearchTitles(listOf("alpha"))

        home.tapSearchResult("alpha")
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()

        val intent = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.intent
                ?: throw AssertionError("MainActivity is not resumed, so it has no intent to read")
        }
        assertEquals("the reader opens the picked document", library["alpha"].uri, intent.data)
        assertEquals(
            "the record hash travels with the intent",
            library.hashOf("alpha"),
            intent.getStringExtra(HomeActivity.EXTRA_RECORD_HASH),
        )

        home.pressBack()
        Screens.awaitTopIs(HomeActivity::class.java)
        home.awaitSearchClosed()
    }
}
