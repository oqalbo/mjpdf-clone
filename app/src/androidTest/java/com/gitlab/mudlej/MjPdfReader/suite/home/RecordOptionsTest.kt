// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.ListFilter
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class RecordOptionsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-25")
    fun hidingABookFromTheOptionsDialogRemovesItFromRecentAndLibrary() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, lastOpened = daysAgo(0)),
            BookSeed(HomeFixtures.BETA, pages = 1, lastOpened = daysAgo(0)),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta"))

        grid.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        assertEquals(
            "the dialog offers to hide a visible book",
            TestEnvironment.targetString(R.string.home_hide_from_library),
            HomeDialogs.hideRowLabel(),
        )
        HomeDialogs.tapRow(R.id.hideRow)

        HomeLibrary.awaitHidden(library.hashOf("alpha"), true)
        grid.awaitTitles(listOf("beta"))
        home.selectTab(HomeTab.RECENT).awaitTitles(listOf("beta"))

        assertTrue(
            "hiding must not delete the file",
            library.fileOf("alpha").exists(),
        )
        HomeLibrary.requireRecord(library.hashOf("alpha"))
    }

    @Test
    @TestId("HOME-26")
    @TestDuration(DurationCategory.NORMAL)
    fun unhidingRestoresTheBookAndRelabelsTheRow() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, hidden = true, lastOpened = daysAgo(0)),
        )

        home.launchFromLauncher()

        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openTestFolder(rules.testDirectory)
        folders.awaitContains("alpha")

        folders.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        assertEquals(
            "the dialog says which direction it will go for a hidden book",
            TestEnvironment.targetString(R.string.home_show_in_library),
            HomeDialogs.hideRowLabel(),
        )
        HomeDialogs.tapRow(R.id.hideRow)

        HomeLibrary.awaitHidden(library.hashOf("alpha"), false)
        home.selectTab(HomeTab.LIBRARY).awaitContains("alpha")
    }

    @Test
    @TestId("HOME-27")
    @TestDuration(DurationCategory.NORMAL)
    fun settingAReadingStatusWritesTheReadingColumn() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, reading = ReadingStatus.UNSET),
            BookSeed(HomeFixtures.BETA, pages = 1, reading = ReadingStatus.UNSET),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta"))

        grid.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        val generationBeforeStatus = home.generation()
        HomeDialogs.chooseStatus(TestEnvironment.targetString(R.string.home_chip_completed))

        HomeLibrary.awaitReading(library.hashOf("alpha"), ReadingStatus.COMPLETED)
        home.awaitGenerationAtLeast(generationBeforeStatus + 1)
        assertEquals(
            "a status change must touch exactly one book",
            ReadingStatus.UNSET,
            HomeLibrary.requireRecord(library.hashOf("beta")).reading,
        )

        home.pressBack()
        HomeDialogs.awaitOptionsDialogGone()

        home.tapChip(R.id.chipCompleted)
        grid.awaitTitles(listOf("alpha"))
    }

    @Test
    @TestId("HOME-28")
    fun settingAStatusOnAScanOnlyFileCreatesItsRecord() {
        val library = HomeBaselines.indexedLibrary(
            BookSeed(HomeFixtures.BETA, record = false, pages = 1),
        )
        HomeLibrary.insertScannedEntryWithoutHash(library["beta"])
        assertEquals("baseline: no records before the status change", 0, HomeLibrary.recordCount())

        home.launchFromLauncher()
        home.awaitRendered()
        val grid = home.library()
        grid.awaitContains("beta")

        grid.cell("beta").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        val generationBeforeStatus = home.generation()
        HomeDialogs.chooseStatus(TestEnvironment.targetString(R.string.home_chip_to_read))

        val record = HomeLibrary.awaitRecordCount(1).single()
        home.awaitGenerationAtLeast(generationBeforeStatus + 1)
        assertEquals("the status that was picked", ReadingStatus.TO_READ, record.reading)
        assertTrue(
            "the new row must carry a content identity, read ${record.hash}",
            record.hash.startsWith(IDENTITY_PREFIX),
        )
        assertEquals(
            "the new row must point at the indexed file",
            library["beta"].uri,
            record.uri,
        )

        val entry = HomeLibrary.awaitScannedEntry(library["beta"].path)
        Await.untilValue(
            "the scan index row is given the identity that was computed for it",
            read = { HomeLibrary.scannedEntry(library["beta"].path)?.hash },
        ) { it == record.hash }
        assertEquals("the scan row path is unchanged", library["beta"].path, entry.path)
    }

    @Test
    @TestId("HOME-29")
    fun statusChangeOnAScanOnlyFileIsRefusedWhenHistorySavingIsOff() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        val library = HomeBaselines.indexedLibrary(
            BookSeed(HomeFixtures.BETA, record = false, pages = 1),
        )
        HomeLibrary.insertScannedEntryWithoutHash(library["beta"])

        home.launchFromLauncher()
        home.awaitRendered()
        val grid = home.library()
        grid.awaitContains("beta")

        grid.cell("beta").tapOptions()
        HomeDialogs.awaitOptionsDialog()
        HomeDialogs.chooseStatus(TestEnvironment.targetString(R.string.home_chip_to_read))

        Toasts.awaitText(TestEnvironment.targetString(R.string.history_action_blocked))
        HomeLibrary.holdRecordCount(0)
    }

    @Test
    @TestId("HOME-30")
    fun aStoredFavoriteFilterFallsBackToAllFiles() {
        HomePrefs.seedFilter(ListFilter.FAVORITE)
        HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, favorite = true),
            BookSeed(HomeFixtures.BETA, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()

        grid.awaitTitles(listOf("alpha", "beta"))
        assertAllFilesIsTheOnlyCheckedChip()

        HomePrefs.seedFilter(ListFilter.RECENT)
        home.restartHome()

        home.library().awaitTitles(listOf("alpha", "beta"))
        assertAllFilesIsTheOnlyCheckedChip()
    }

    @Test
    @TestId("HOME-31")
    fun theFavoriteColumnSurvivesOtherEdits() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, favorite = true, reading = ReadingStatus.UNSET),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitContains("alpha")

        grid.cell("alpha").tapOptions()
        HomeDialogs.awaitOptionsDialog()

        assertEquals(
            "the options dialog carries no favorite control",
            listOf(
                R.id.statusDropdown,
                R.id.hideRow,
                R.id.openIncognitoRow,
                R.id.sharePdfRow,
                R.id.renameRow,
                R.id.deleteRow,
            ),
            HomeDialogs.visibleOptionRows(),
        )

        HomeDialogs.chooseStatus(TestEnvironment.targetString(R.string.home_chip_reading))

        val record = HomeLibrary.awaitReading(library.hashOf("alpha"), ReadingStatus.READING)
        assertTrue("a library edit must not clobber the favorite flag", record.favorite)
    }

    private fun assertAllFilesIsTheOnlyCheckedChip() {
        onView(allOf(withId(R.id.chipAllFiles), isDisplayed())).check(matches(isChecked()))
        listOf(
            R.id.chipToRead,
            R.id.chipReading,
            R.id.chipOnHold,
            R.id.chipCompleted,
            R.id.chipAbandoned,
        ).forEach { chip ->
            onView(allOf(withId(chip), isDisplayed())).check(matches(isNotChecked()))
        }
    }

    private companion object {
        const val IDENTITY_PREFIX = "h2"
    }
}
