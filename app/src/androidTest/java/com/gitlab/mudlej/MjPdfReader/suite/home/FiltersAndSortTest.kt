// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSortOrder
import org.hamcrest.CoreMatchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FiltersAndSortTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-32")
    fun statusChipsFilterTheGridAndTheChoicePersists() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, reading = ReadingStatus.READING),
            BookSeed(HomeFixtures.BETA, pages = 1, reading = ReadingStatus.COMPLETED),
            BookSeed(HomeFixtures.GAMMA, pages = 1, reading = ReadingStatus.UNSET),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta", "gamma"))

        home.tapChip(R.id.chipReading)

        grid.awaitTitles(listOf("alpha"))
        HomePrefs.awaitString(Preferences.listFilterKey, "READING")

        home.restartHome()

        onView(allOf(withId(R.id.chipReading), isDisplayed())).check(matches(isChecked()))
        home.library().awaitTitles(listOf("alpha"))
    }

    @Test
    @TestId("HOME-33")
    fun anEmptyStatusFilterShowsTheStatusEmptyState() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, reading = ReadingStatus.READING),
            BookSeed(HomeFixtures.BETA, pages = 1, reading = ReadingStatus.COMPLETED),
            BookSeed(HomeFixtures.GAMMA, pages = 1, reading = ReadingStatus.UNSET),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta", "gamma"))

        home.tapChip(R.id.chipAbandoned)

        grid.awaitItemCount(0)
        grid.awaitEmptyState(R.string.home_empty_status_title)
    }

    @Test
    @TestId("HOME-34")
    fun nameSortIsCaseInsensitiveAndIgnoresTheExtension() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.CHERRY, pages = 1),
            BookSeed(HomeFixtures.APPLE, pages = 1),
            BookSeed(HomeFixtures.BANANA, pages = 1),
        )

        home.launchFromLauncher()

        home.library().awaitTitles(listOf("Apple", "banana", "Cherry"))
    }

    @Test
    @TestId("HOME-35")
    fun switchingToLastOpenedReordersTheGrid() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.APPLE, pages = 1, lastOpened = daysAgo(9)),
            BookSeed(HomeFixtures.BANANA, pages = 1),
            BookSeed(HomeFixtures.CHERRY, pages = 1, lastOpened = daysAgo(1)),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("Apple", "banana", "Cherry"))

        home.openOverflowMenu()
        home.tapMenuRow(R.id.sortLastOpenedButton)
        home.dismissOverflowMenu()

        HomePrefs.awaitString(Preferences.homeSortKey, HomeSortOrder.LAST_OPENED.name)
        grid.awaitTitles(listOf("Cherry", "Apple", "banana"))
    }
}
