// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.text.format.Formatter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeProgressStyle
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LibraryGridTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-11")
    fun libraryGridListsIndexedFilesAndCarriesRecordDataOntoThem() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 4, pages = 10, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, record = false, pages = 1),
            BookSeed(HomeFixtures.GAMMA, record = false, pages = 1),
        )

        home.launchFromLauncher()

        val library = home.library()

        library.awaitTitles(listOf("alpha", "beta", "gamma"))
        assertEquals("the started book keeps its progress", "50", library.cell("alpha").progressPercentText())

        listOf("beta", "gamma").forEach { title ->
            val cell = library.cell(title)
            assertTrue("$title has no progress badge", !cell.isProgressBadgeShown())
            assertTrue("$title has no progress bar", !cell.isProgressBarShown())
            assertTrue("$title shows no last opened label", !cell.isLastOpenedShown())
        }
    }

    @Test
    @TestId("HOME-16")
    fun progressStyleSwitchesBetweenTheRingBadgeAndTheBar() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 4, pages = 10, lastOpened = daysAgo(0)),
        )

        home.launchFromLauncher()

        var library = home.library()
        library.awaitContains("alpha")

        val ring = library.cell("alpha")
        assertTrue("RING shows the percent badge", ring.isProgressBadgeShown())
        assertEquals("the ring badge percent", "50", ring.progressPercentText())
        assertTrue("RING hides the linear bar", !ring.isProgressBarShown())

        SettingsSeeder.seed(Preferences.homeProgressStyleKey, HomeProgressStyle.BAR.name)
        home.restartHome()
        library = home.library()
        library.awaitContains("alpha")

        val bar = library.cell("alpha")
        Await.until("the linear progress bar takes over") { bar.isProgressBarShown() }
        assertEquals("the linear bar value", 50, bar.progressBarValue())
        assertTrue("BAR hides the percent badge", !bar.isProgressBadgeShown())
    }

    @Test
    @TestId("HOME-17")
    fun progressPercentFloorCeilingAndTheUnreadCase() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 0, pages = 500, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pageNumber = 9, pages = 10, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.GAMMA, pageNumber = 0, pages = 10),
        )

        home.launchFromLauncher()

        val library = home.library()
        library.awaitTitles(listOf("alpha", "beta", "gamma"))

        assertEquals(
            "a barely started book must never read zero percent",
            "1",
            library.cell("alpha").progressPercentText(),
        )
        assertEquals(
            "a book on its last page reads one hundred percent",
            "100",
            library.cell("beta").progressPercentText(),
        )

        val unread = library.cell("gamma")
        assertTrue("an unopened book shows no ring", !unread.isProgressBadgeShown())
        assertTrue("an unopened book shows no bar", !unread.isProgressBarShown())
        assertTrue("an unopened book shows no last opened label", !unread.isLastOpenedShown())
    }

    @Test
    @TestId("HOME-18")
    fun listRowBadgesDifferByTab() {
        HomePrefs.seedViewMode(HomeViewMode.LIST)
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                reading = ReadingStatus.READING,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()

        val today = TestEnvironment.targetString(R.string.home_relative_today)
        val sizeText = Formatter.formatShortFileSize(
            TestEnvironment.targetContext,
            library.fileOf("alpha").length(),
        )

        val libraryPage = home.library()
        libraryPage.awaitContains("alpha")
        assertEquals(
            "the Library row reports pages, progress and recency",
            listOf("5/10", "50%", today),
            libraryPage.cell("alpha").badges(),
        )

        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitContains("alpha")
        assertEquals(
            "the Recent row adds the reading status badge",
            listOf("5/10", "50%", today, TestEnvironment.targetString(R.string.home_chip_reading)),
            recent.cell("alpha").badges(),
        )

        val folders = home.selectTab(HomeTab.FOLDERS)
        openTestFolder(folders)
        folders.awaitContains("alpha")
        assertEquals(
            "the Folders row reports pages and file size only",
            listOf("5/10", sizeText),
            folders.cell("alpha").badges(),
        )
    }

    @Test
    @TestId("HOME-19")
    fun turningOffThePageBadgeRemovesItEverywhereOnTheRow() {
        HomePrefs.seedViewMode(HomeViewMode.LIST)
        SettingsSeeder.seed(Preferences.homeBadgePagesKey, false)
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                reading = ReadingStatus.READING,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()

        val library = home.library()
        library.awaitContains("alpha")

        val badges = library.cell("alpha").badges()
        assertTrue("the pages badge must be gone, read $badges", badges.none { it == "5/10" })
        assertTrue("the progress badge must remain, read $badges", badges.contains("50%"))
        assertTrue(
            "the last opened badge must remain, read $badges",
            badges.contains(TestEnvironment.targetString(R.string.home_relative_today)),
        )
    }

    @Test
    @TestId("HOME-76")
    fun turningOffTheProgressBadgeRemovesOnlyProgressFromTheLibraryRow() {
        HomePrefs.seedViewMode(HomeViewMode.LIST)
        SettingsSeeder.seed(Preferences.homeBadgeProgressKey, false)
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                reading = ReadingStatus.READING,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        val library = home.library()
        library.awaitContains("alpha")

        assertEquals(
            "disabling progress removes only the progress badge",
            listOf("5/10", TestEnvironment.targetString(R.string.home_relative_today)),
            library.cell("alpha").badges(),
        )
    }

    @Test
    @TestId("HOME-77")
    fun turningOffTheLastOpenedBadgeRemovesOnlyRecencyFromTheLibraryRow() {
        HomePrefs.seedViewMode(HomeViewMode.LIST)
        SettingsSeeder.seed(Preferences.homeBadgeLastOpenedKey, false)
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                reading = ReadingStatus.READING,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        val library = home.library()
        library.awaitContains("alpha")

        assertEquals(
            "disabling last opened removes only the recency badge",
            listOf("5/10", "50%"),
            library.cell("alpha").badges(),
        )
    }

    @Test
    @TestId("HOME-78")
    fun turningOffTheFileSizeBadgeRemovesOnlySizeFromTheFolderRow() {
        HomePrefs.seedViewMode(HomeViewMode.LIST)
        SettingsSeeder.seed(Preferences.homeBadgeFileSizeKey, false)
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                reading = ReadingStatus.READING,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        val folders = home.selectTab(HomeTab.FOLDERS)
        openTestFolder(folders)
        folders.awaitContains("alpha")

        assertEquals(
            "disabling file size removes only the size badge",
            listOf("5/10"),
            folders.cell("alpha").badges(),
        )
    }

    @Test
    @TestId("HOME-79")
    fun turningOffTheStatusBadgeRemovesOnlyStatusFromTheRecentRow() {
        HomePrefs.seedViewMode(HomeViewMode.LIST)
        SettingsSeeder.seed(Preferences.homeBadgeStatusKey, false)
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(
                HomeFixtures.ALPHA,
                pageNumber = 4,
                pages = 10,
                reading = ReadingStatus.READING,
                lastOpened = daysAgo(0),
            ),
        )

        home.launchFromLauncher()
        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitContains("alpha")

        assertEquals(
            "disabling status removes only the reading-status badge",
            listOf("5/10", "50%", TestEnvironment.targetString(R.string.home_relative_today)),
            recent.cell("alpha").badges(),
        )
    }

    @Test
    @TestId("HOME-20")
    @TestDuration(DurationCategory.NORMAL)
    fun gridSizeChangesTheNumberOfColumns() {
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, record = false),
            BookSeed(HomeFixtures.BETA, record = false),
            BookSeed(HomeFixtures.GAMMA, record = false),
            BookSeed(HomeFixtures.APPLE, record = false),
            BookSeed(HomeFixtures.BANANA, record = false),
            BookSeed(HomeFixtures.CHERRY, record = false),
        )

        home.launchFromLauncher()

        val library = home.library()
        library.awaitItemCount(6)

        val widthDp = TestEnvironment.targetContext.resources.configuration.screenWidthDp

        home.openOverflowMenu()
        home.tapMenuRow(R.id.gridSizeLargeButton)
        home.dismissOverflowMenu()

        HomePrefs.awaitString(Preferences.homeGridSizeKey, "LARGE")
        val largeSpan = max(2, widthDp / 150)
        Await.untilValue("the grid settles at $largeSpan columns", read = { library.spanCount() }) {
            it == largeSpan
        }

        home.openOverflowMenu()
        home.tapMenuRow(R.id.gridSizeSmallButton)
        home.dismissOverflowMenu()

        HomePrefs.awaitString(Preferences.homeGridSizeKey, "SMALL")
        val smallSpan = max(2, widthDp / 96)
        Await.untilValue("the grid settles at $smallSpan columns", read = { library.spanCount() }) {
            it == smallSpan
        }
        assertTrue(
            "the small grid must be denser than the large one on a ${widthDp}dp wide screen, " +
                "read $smallSpan against $largeSpan",
            smallSpan > largeSpan,
        )
    }

    private fun openTestFolder(folders: HomePage) {
        val root = Fixtures.externalRoot.absolutePath
        val relative = rules.testDirectory.expectedPath().absolutePath.removePrefix("$root/")
        val primaryRootLabel = folders.folders().firstOrNull { it.path == root }?.name
        val path = listOfNotNull(primaryRootLabel) + relative.split("/")
        folders.openFolderPath(*path.toTypedArray())
    }
}
