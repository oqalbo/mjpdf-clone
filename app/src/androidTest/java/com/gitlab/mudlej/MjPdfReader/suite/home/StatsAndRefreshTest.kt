// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.text.format.Formatter
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import kotlinx.coroutines.CompletableDeferred
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeFixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class StatsAndRefreshTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()

    @Test
    @TestId("HOME-67")
    @TestDuration(DurationCategory.SHORT)
    fun libraryStatsMatchTheDatabaseAndTheScanIndex() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pageNumber = 0, pages = 1, reading = ReadingStatus.TO_READ),
            BookSeed(HomeFixtures.BETA, pageNumber = 4, pages = 10, reading = ReadingStatus.READING),
            BookSeed(HomeFixtures.GAMMA, pageNumber = 9, pages = 10, reading = ReadingStatus.COMPLETED),
            BookSeed(HomeFixtures.APPLE, pageNumber = 0, pages = 1, reading = ReadingStatus.UNSET),
        )
        val expectedStorageBytes = library.books.sumOf { it.file.length() }

        home.launchFromLauncher()

        home.library().awaitItemCount(4)
        val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
        Await.untilValue(
            condition = "the active scan scope carries all four file sizes",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { scanner.libraryEntries(scanner.index.value) },
        ) { entries ->
            entries.size == library.books.size && entries.sumOf { it.size } == expectedStorageBytes
        }
        home.awaitGenerationStable()

        home.openOverflowMenu()
        home.tapMenuRow(R.id.statsRow)
        HomeDialogs.awaitStatsDialog()

        assertEquals("PDFs on device", "4", HomeDialogs.statValue(R.string.home_stats_on_device))
        assertEquals(
            "Storage used",
            Formatter.formatShortFileSize(
                TestEnvironment.targetContext,
                expectedStorageBytes,
            ),
            HomeDialogs.statValue(R.string.home_stats_storage),
        )
        assertEquals("In library", "4", HomeDialogs.statValue(R.string.home_stats_in_library))
        assertEquals(
            "Pages read counts pageNumber plus one, and only past page zero",
            "15",
            HomeDialogs.statValue(R.string.home_stats_pages_read),
        )

        assertEquals("To Read", "1", HomeDialogs.statValue(R.string.home_chip_to_read))
        assertEquals("Reading", "1", HomeDialogs.statValue(R.string.home_chip_reading))
        assertEquals("On Hold", "0", HomeDialogs.statValue(R.string.home_chip_on_hold))
        assertEquals("Completed", "1", HomeDialogs.statValue(R.string.home_chip_completed))
        assertEquals("Abandoned", "0", HomeDialogs.statValue(R.string.home_chip_abandoned))
        assertEquals("No status", "1", HomeDialogs.statValue(R.string.home_stats_no_status))
    }

    @Test
    @TestId("HOME-68")
    fun pullToRefreshAlwaysClearsItsSpinner() {
        val directory = rules.testDirectory.directory()
        HomeBaselines.scopedLibrary(
            directory,
            BookSeed(HomeFixtures.ALPHA, record = false, pages = 1),
            BookSeed(HomeFixtures.BETA, record = false, pages = 1),
        )

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitTitles(listOf("alpha", "beta"))

        val added = IntakeFixtures.copyInto(directory, HomeFixtures.GAMMA)
        Fixtures.scanAndWait(added)

        grid.pullToRefresh()

        grid.awaitTitles(listOf("alpha", "beta", "gamma"), Await.LONG_TIMEOUT_MS)

        grid.awaitRefreshFinished(Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("HOME-69")
    fun aQueuedRefreshDuringAnActiveScanLeavesTheListMatchingTheDatabase() {
        val directory = rules.testDirectory.directory()
        val seeds = (1..12).map { index ->
            BookSeed(
                "fifty-distinct/distinct-%02d.pdf".format(index),
                name = "book-%02d.pdf".format(index),
                pages = 3,
            )
        }
        val library = HomeBaselines.scopedLibrary(directory, *seeds.toTypedArray())

        home.launchFromLauncher()

        val grid = home.library()
        grid.awaitItemCount(12)

        Await.until("the initial scan is complete", Await.LONG_TIMEOUT_MS) {
            ScanDebugSignals.snapshot().index.let { it.loaded && !it.active }
        }
        val doomed = library.books.take(6)
        val queuedBefore = ScanDebugSignals.manualRefreshQueueCount()
        val barrier = CompletableDeferred<Unit>()
        ScanDebugSignals.discoveryBarrier = barrier
        var runsWhenQueued = 0
        try {
            grid.pullToRefresh()
            Await.until("the first refresh is held during discovery") { ScanDebugSignals.isDiscoveryBlocked() }
            HomeLibrary.deleteRecords(doomed.map { it.hash })
            HomeLibrary.deleteScannedEntries(doomed.map { it.path })
            doomed.forEach { assertTrue("the selected fixture is deleted", it.file.delete()) }
            val previous = Screens.awaitResumed(HomeActivity::class.java) as HomeActivity
            Rotation.toLandscape(HomeActivity::class.java)
            Screens.awaitReplacement(previous, HomeActivity::class.java)
            home.awaitRendered()
            home.library().pullToRefresh()
            Await.until("a second manual refresh is queued while discovery remains active") {
                ScanDebugSignals.isDiscoveryBlocked() && ScanDebugSignals.manualRefreshQueueCount() > queuedBefore
            }
            runsWhenQueued = ScanDebugSignals.discoveryRuns()
        } finally {
            ScanDebugSignals.discoveryBarrier = null
            barrier.complete(Unit)
        }
        Await.until("the queued refresh runs and settles", Await.LONG_TIMEOUT_MS) {
            ScanDebugSignals.discoveryRuns() > runsWhenQueued && !ScanDebugSignals.snapshot().index.active
        }

        val surviving = library.books.drop(6).map { it.fileName }.sorted()
        grid.awaitTitles(surviving, Await.LONG_TIMEOUT_MS)
        home.awaitGenerationStable()

        assertEquals(
            "the rendered list must match the scan index the database holds",
            HomeLibrary.scannedEntries()
                .filter { it.path.startsWith(directory.absolutePath) }
                .map { File(it.path).nameWithoutExtension }
                .sorted(),
            grid.titles().sorted(),
        )
        assertEquals(
            "the hook's item count must agree with what the adapter holds",
            grid.itemCount(),
            home.readState().renderedItems,
        )

    }

    @Test
    @TestId("HOME-70")
    @TestDuration(DurationCategory.SHORT)
    fun hiddenBooksStayOutOfFiltersButRemainInStatsAndStorage() {
        val library = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1, reading = ReadingStatus.READING),
            BookSeed(HomeFixtures.BETA, pages = 1, reading = ReadingStatus.READING),
            BookSeed(HomeFixtures.GAMMA, pages = 1, reading = ReadingStatus.READING, hidden = true),
        )

        home.launchFromLauncher()

        val grid = home.library()

        grid.awaitTitles(listOf("alpha", "beta"))

        home.tapChip(R.id.chipReading)
        grid.awaitTitles(listOf("alpha", "beta"))

        val hidden = HomeLibrary.requireRecord(library.hashOf("gamma"))
        assertEquals("the hidden book keeps its status", ReadingStatus.READING, hidden.reading)
        assertTrue("the hidden book keeps its file", library.fileOf("gamma").exists())

        home.openOverflowMenu()
        home.tapMenuRow(R.id.statsRow)
        HomeDialogs.awaitStatsDialog()

        assertEquals(
            "stats include all three stored books, including the hidden book",
            "3",
            HomeDialogs.statValue(R.string.home_stats_in_library),
        )
    }
}
