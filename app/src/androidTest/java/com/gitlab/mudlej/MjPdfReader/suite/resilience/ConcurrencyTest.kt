// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.os.Environment
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ArtifactBytes
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.home.HomeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.HomeStateSource
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class ConcurrencyTest : ResilienceTestCase() {

    private val selection = SelectionRobot()
    private val home = HomeRobot()

    private val durableDirectory =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DURABLE_FOLDER)

    @After
    fun removeSharedCopies() {
        runCatching { LibraryScanner.getInstance(TestEnvironment.targetContext).resumeMetadataDispatch() }
        runCatching { MetadataWorkerTestHooks.reset() }
        runCatching { StubSharerProvider.clear() }
        durableDirectory.listFiles().orEmpty()
            .filter { it.name.startsWith(SHARED_BASE_NAME) }
            .forEach { Fixtures.delete(it) }
    }

    @Test
    @TestId("RES-29")
    fun overlappingHomeRefreshesLeaveNoStaleList() {
        Fixtures.ensureAllFilesAccess()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.homeTabKey to HomeTab.RECENT.name,
                Preferences.scanModeKey to ScanMode.SELECTED_LOCATIONS.name,
                Preferences.scanLocationsKey to setOf(testDirectory().absolutePath),
            )
        )
        val records = seedRecordsWithFiles(SEEDED_RECORDS)
        val scanBeforeLaunch = ScanLifecycle.generation()

        ActivityScenario.launch<HomeActivity>(homeIntent()).use {
            awaitHomeItems(SEEDED_RECORDS)
            val initialScan = ScanLifecycle.awaitCompletedAfter(scanBeforeLaunch)
            home.awaitScanCommitted(initialScan)
            val trigger = File(testDirectory(), REFRESH_TRIGGER_NAME)
            Fixtures.seededFixture(distinctFixtureName(SEEDED_RECORDS + 1)).copyTo(trigger)
            MetadataWorkerTestHooks.pauseDispatch()
            Fixtures.scanAndWait(trigger)
            val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
            val recent = home.recent()
            val discoveryRunsBefore = ScanDebugSignals.discoveryRuns()
            val removed = records.take(DELETED_RECORDS)

            try {
                recent.pullToRefresh()
                val firstRefresh = Await.untilValue(
                    condition = "the first pull starts a production scan after generation ${initialScan.generation}",
                    timeoutMs = Await.LONG_TIMEOUT_MS,
                    read = ScanLifecycle::snapshot,
                ) { index -> index.generation > initialScan.generation && index.active }

                removed.forEach { Records.database.pdfRecordDao().deleteByHash(it.hash) }

                assertTrue(
                    "the first scan must still be active when the second pull is made",
                    ScanLifecycle.snapshot().generation == firstRefresh.generation && ScanLifecycle.snapshot().active,
                )
                recent.pullToRefresh()
                val overlapped = ScanLifecycle.snapshot()
                assertTrue(
                    "the second pull must be delivered while the metadata-paused first generation is still " +
                        "active; the lifecycle read $overlapped",
                    overlapped.generation == firstRefresh.generation && overlapped.active,
                )

                scanner.resumeMetadataDispatch()
                recent.awaitRefreshFinished(Await.LONG_TIMEOUT_MS)
                val finalScan = ScanLifecycle.awaitCompletedAfter(firstRefresh.generation)
                home.awaitScanCommitted(finalScan)
                assertTrue(
                    "the second pull must queue a fresh production scan generation",
                    finalScan.generation > firstRefresh.generation,
                )
                assertTrue(
                    "both pull gestures must reach scanner discovery",
                    ScanDebugSignals.discoveryRuns() >= discoveryRunsBefore + REFRESH_BURST,
                )
            } finally {
                scanner.resumeMetadataDispatch()
                MetadataWorkerTestHooks.reset()
            }

            awaitHomeItems(SEEDED_RECORDS - DELETED_RECORDS)

            assertEquals(
                "the rendered list must match the database the refresh burst ended on",
                SEEDED_RECORDS - DELETED_RECORDS,
                HomeStateSource.read()?.renderedItems,
            )
            removed.forEach { record ->
                assertFalse(
                    "the deleted record ${record.fileName} must not survive on screen",
                    TestEnvironment.uiDevice.hasObject(By.text(record.fileName)),
                )
            }
        }
    }

    @Test
    @TestId("RES-30")
    @TestDuration(DurationCategory.SHORT)
    fun twoImportsOfOneNameDoNotCorruptEachOther() {
        SettingsSeeder.seed(Preferences.sharedCopyModeKey, SharedCopyMode.ALWAYS_COPY.name)
        val first = fixture(ResilienceFixtures.NORMAL)
        val second = fixture(ResilienceFixtures.SCANNED)
        val firstDigest = ArtifactBytes.sha256(first)
        val secondDigest = ArtifactBytes.sha256(second)
        assertFalse(
            "the two sources must differ, otherwise the race cannot corrupt anything",
            firstDigest == secondDigest,
        )

        val firstUri = StubSharerProvider.publish(FIRST_KEY, first.readBytes())
        val secondUri = StubSharerProvider.publish(SECOND_KEY, second.readBytes())
        StubSharerProvider.setDisplayName(FIRST_KEY, SHARED_NAME)
        StubSharerProvider.setDisplayName(SECOND_KEY, SHARED_NAME)
        val before = durableFiles().map { it.name }.toSet()
        StubSharerProvider.blockFirstOpenMatching(FIRST_KEY) {
            durableFiles().any {
                it.name !in before && (it.name.endsWith(PARTIAL_SUFFIX) || it.length() == 0L)
            }
        }

        try {
            ReaderProbe.startReader(ReaderRobot.viewIntent(firstUri))
            val firstReader = Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the first import must be parked while its partial copy exists",
                StubSharerProvider.awaitOpenBlocked(FIRST_KEY, Await.VERY_LONG_TIMEOUT_MS),
            )

            ReaderProbe.startReader(
                ReaderRobot.viewIntent(secondUri).addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            )
            Await.until(
                "a second reader takes the foreground. Waiting for any resumed MainActivity is satisfied on its " +
                    "first evaluation by the reader that is already up, so the identity has to change before " +
                    "the second import can be said to have started at all",
                Await.LONG_TIMEOUT_MS,
            ) { ReaderProbe.resumedReaderOrNull()?.let { it !== firstReader } == true }
            awaitReaderReady()

            Await.until(
                "the second import completes while the first import remains parked",
                Await.VERY_LONG_TIMEOUT_MS,
            ) {
                completedDurableFiles(before).size == 1
            }
        } finally {
            StubSharerProvider.releaseBlockedOpen(FIRST_KEY)
        }

        val copies = Await.untilValue(
            condition = "both shared documents are copied into ${durableDirectory.absolutePath}. The whole " +
                "directory currently reads ${durableFiles().map { it.name }}",
            timeoutMs = Await.VERY_LONG_TIMEOUT_MS,
            read = { completedDurableFiles(before) },
        ) { it.size == 2 }
        Await.until("no partial copy is left behind", Await.LONG_TIMEOUT_MS) {
            durableFiles().none { it.name.endsWith(PART_SUFFIX) || it.name.endsWith(PARTIAL_SUFFIX) }
        }

        val digests = copies.map { ArtifactBytes.sha256(it) }.toSet()
        assertEquals(
            "the two copies must not share a digest, which is what interleaved writes into one name produce",
            2,
            digests.size,
        )
        assertEquals(
            "each copy must be a whole copy of one of the two sources",
            setOf(firstDigest, secondDigest),
            digests,
        )
        val sourceUris = setOf(firstUri.toString(), secondUri.toString())
        val rows = Await.untilValue(
            condition = "both imported records acknowledge their durable copy",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { Records.all().filter { it.sourceUri in sourceUris } },
        ) { records ->
            records.size == 2 && records.all { it.uri.toString() != it.sourceUri }
        }
        assertEquals(
            "each imported document must own its own record",
            2,
            rows.map { it.hash }.toSet().size,
        )
        assertEquals(
            "the two records must point at two different copies",
            2,
            rows.map { it.uri.toString() }.toSet().size,
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-31")
    @TestDuration(DurationCategory.NORMAL)
    fun deletingADocumentWhileTheReaderHoldsUnsavedHighlights() {
        Fixtures.ensureAllFilesAccess()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.scanModeKey to ScanMode.SELECTED_LOCATIONS.name,
                Preferences.scanLocationsKey to setOf(testDirectory().absolutePath),
            )
        )
        val file = fixture(ResilienceFixtures.NORMAL)
        val readerActivity = launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val hash = reader.awaitFileHashResolved()
        Records.awaitRowExists(hash, Await.LONG_TIMEOUT_MS)
        ResilienceSeed.insertBookmarks(hash, SEEDED_BOOKMARKS)

        val documentUri = ReaderProbe.documentUriOfReader()
        val journal = ReaderProbe.journalFileFor(documentUri)
        repeat(2) { createHighlightOnCurrentPage() }
        Await.until("the annotation journal exists before the delete", Await.LONG_TIMEOUT_MS) { journal.isFile }
        Records.database.pdfAnnotationSaveDestinationDao().upsert(
            PdfAnnotationSaveDestination(
                sourceKey = SourceKey.of(documentUri),
                destinationUri = documentUri.toString(),
                lastSavedHash = hash,
                lastSavedAt = LocalDateTime.now(),
            )
        )

        val scanBeforeHome = ScanLifecycle.generation()
        TestEnvironment.targetContext.startActivity(homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
        home.awaitShown(Await.LONG_TIMEOUT_MS)
        home.awaitScanCommitted(ScanLifecycle.awaitCompletedAfter(scanBeforeHome))
        assertTrue(
            "the original reader must remain alive with its unsaved highlights while Home deletes the file",
            ReaderProbe.liveReaders().any { it === readerActivity },
        )

        val folders = home.selectTab(HomeTab.FOLDERS)
        openTestFolder(folders)
        val fileIndex = Await.untilValue(
            condition = "the Folders tab lists the document at ${file.absolutePath}",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { folders.items().indexOfFirst { it.uri.path == file.absolutePath } },
        ) { it >= 0 }
        folders.cellAt(fileIndex).performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        home.awaitSelectionTitle(
            TestEnvironment.targetContext.getString(R.string.home_selected_count, 1)
        )
        home.tapSelectionAction(R.id.deleteBatchOption)
        HomeDialogs.awaitConfirmationDialog()
        assertEquals(
            "the Home confirmation must cover the one selected document",
            TestEnvironment.targetContext.resources.getQuantityString(
                R.plurals.home_delete_batch_confirm_message,
                1,
                1,
            ),
            HomeDialogs.confirmationMessage(),
        )
        HomeDialogs.tapPositive()
        HomeDialogs.awaitConfirmationDialogGone()

        Await.untilTrueContinuously(
            condition = "the annotation journal directory settles with nothing for the deleted document",
            stableForMs = SETTLE_WINDOW_MS,
            timeoutMs = SETTLE_WINDOW_MS + Await.LONG_TIMEOUT_MS,
        ) { !journal.exists() }

        Records.awaitRowDisappears(hash, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "deleting a document must take its bookmarks with it",
            0,
            Records.bookmarksOf(hash).size,
        )
        assertEquals(
            "deleting a document must take its remembered save destination with it",
            null,
            Records.database.pdfAnnotationSaveDestinationDao().findByLastSavedHash(hash),
        )
        assertFalse(
            "the file itself must be gone from storage",
            file.exists(),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun createHighlightOnCurrentPage() {
        val before = state().annotationCount ?: 0
        selection.longPressInsidePage(
            BodyTextTargets.horizontalFraction(highlightIndex),
            BodyTextTargets.normalLine(FIRST_HIGHLIGHT_LINE + highlightIndex * HIGHLIGHT_LINE_STEP),
        )
        highlightIndex++
        ReaderProbe.awaitVisibility(R.id.textSelectionActionCard, View.VISIBLE)
        ReaderProbe.tapFirstHighlightSwatch()
        reader.awaitState("the page reports one more annotation than the $before it had", Await.LONG_TIMEOUT_MS) {
            (it.annotationCount ?: 0) > before
        }
    }

    private var highlightIndex = 0

    private fun homeIntent(): Intent = Intent(TestEnvironment.targetContext, HomeActivity::class.java)

    private fun seedRecordsWithFiles(count: Int) = (1..count).map { index ->
        val source = Fixtures.seededFixture(distinctFixtureName(index))
        if (!source.isFile) {
            throw AssertionError("The distinct resilience fixture ${source.absolutePath} is missing")
        }
        val target = File(testDirectory(), "seeded-${index.toString().padStart(3, '0')}.pdf")
        source.copyTo(target)
        ResilienceSeed.insertRecordFor(target, pages = DISTINCT_FIXTURE_PAGES)
    }

    private fun distinctFixtureName(index: Int): String =
        "fifty-distinct/distinct-${index.toString().padStart(2, '0')}.pdf"

    private fun openTestFolder(folders: HomePage) {
        val root = Fixtures.externalRoot.absolutePath
        val relative = testDirectory().absolutePath.removePrefix("$root/")
        folders.openFolderPath(*relative.split("/").toTypedArray())
    }

    private fun awaitHomeItems(expected: Int) {
        Await.untilValue(
            condition = "Home renders $expected items",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { HomeStateSource.read()?.renderedItems ?: -1 },
        ) { it == expected }
    }

    private fun durableFiles(): List<File> = durableDirectory.listFiles().orEmpty().filter { it.isFile }

    private fun completedDurableFiles(before: Set<String>): List<File> = durableFiles().filter {
        it.name !in before && it.length() > 0L &&
            !it.name.endsWith(PART_SUFFIX) && !it.name.endsWith(PARTIAL_SUFFIX)
    }

    private companion object {
        const val SEEDED_RECORDS = 30
        const val DELETED_RECORDS = 10
        const val REFRESH_BURST = 2
        const val SEEDED_BOOKMARKS = 2
        const val DURABLE_FOLDER = "MJ PDF"
        const val SHARED_BASE_NAME = "res30-report"
        const val SHARED_NAME = "res30-report.pdf"
        const val FIRST_KEY = "res30-a.pdf"
        const val SECOND_KEY = "res30-b.pdf"
        const val PART_SUFFIX = ".part"
        const val PARTIAL_SUFFIX = ".partial"
        const val FIRST_HIGHLIGHT_LINE = 2
        const val HIGHLIGHT_LINE_STEP = 3
        const val DISTINCT_FIXTURE_PAGES = 3
        const val REFRESH_TRIGGER_NAME = "refresh-trigger.pdf"
    }
}
