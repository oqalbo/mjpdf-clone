// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.home.HomePrefs
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSortOrder
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.ui.home.ListFilter
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanProgress
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanRefreshReason
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.gitlab.mudlej.MjPdfReader.R

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScanProgressTest : ScanTestCase() {

    private var watch: ScanIndexWatch? = null

    @After
    fun stopWatchingTheScanIndex() {
        watch?.stop()
        watch = null
        MetadataWorkerTestHooks.reset()
    }

    @Test
    @TestId("SCAN-25")
    fun fullRescanShowsDetailsOnlyOnAllWhileCachedQuickRefreshStaysSilent() {
        grantAccess()
        val privateRoot = File(TestEnvironment.targetContext.cacheDir, "scan25-private-${UUID.randomUUID()}")
        try {
            ScanFixtures.createDirectory(privateRoot)
            ScanFixtures.writeMarker(File(privateRoot, ".nomedia"))
            ScanPrefs.seedSelected(privateRoot)
            SettingsSeeder.seed(Preferences.listFilterKey, ListFilter.ALL.name)
            launchHomeAndScan()
            ScanCache.awaitCountUnder(privateRoot, 0)
            home.library().awaitItemCount(0, Await.LONG_TIMEOUT_MS)
            MetadataWorkerTestHooks.pauseDispatch()
            (1..5).forEach {
                ScanFixtures.placeWithoutScan(ScanFixtures.bulkMember(it), File(privateRoot, ScanFixtures.bulkName(it)))
            }

            val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
            val watcher = startWatching()
            val baseline = fullRescanLibrary()
            Await.untilTrueContinuously(
                condition = "Full Rescan holds five private PDFs pending while All shows details progress",
                stableForMs = 500L,
                timeoutMs = Await.VERY_LONG_TIMEOUT_MS,
            ) {
                val index = scanner.index.value
                index.generation > baseline.generation && index.phase == ScanPhase.READING_DETAILS &&
                    index.showProgress && index.detailsTotal == 5 && index.detailsCompleted == 0 &&
                    home.library().scanProgress()?.progress == ScanProgress.ReadingDetails(0, 5)
            }

            home.tapChip(R.id.chipReading)
            HomePrefs.awaitString(Preferences.listFilterKey, ListFilter.READING.name)
            assertProgressRowStaysAbsent()
            assertTrue("Status filters remain record-only", home.library().items().none { it.isScanOnly })
            home.tapChip(R.id.chipAllFiles)
            HomePrefs.awaitString(Preferences.listFilterKey, ListFilter.ALL.name)
            Await.until("returning to All restores active full-rescan progress", Await.LONG_TIMEOUT_MS) {
                home.library().scanProgress()?.progress == ScanProgress.ReadingDetails(0, 5)
            }

            scanner.resumeMetadataDispatch()
            awaitScanComplete(baseline)
            ScanCache.awaitAllHashedUnder(privateRoot, 5)
            val beforeWarm = watcher.seen().size
            val directories = ScanDebugSignals.filesystemDirectories()
            val warm = refreshLibrary()
            awaitScanComplete(warm)
            val foreground = scanBaseline()
            scanner.refresh(ScanRefreshReason.FOREGROUND)
            awaitScanComplete(foreground)
            assertTrue("Quick refreshes publish new backend snapshots", watcher.seen().size > beforeWarm)
            assertTrue(
                "Cached foreground and pull refreshes never request scan progress",
                watcher.seen().drop(beforeWarm).none { it.showProgress },
            )
            assertEquals("Quick refreshes do not walk directories", directories, ScanDebugSignals.filesystemDirectories())
            assertProgressRowStaysAbsent()
        } finally {
            privateRoot.deleteRecursively()
        }
    }

    @Test
    @TestId("SCAN-26")
    fun aLargeScanAppendsInOrderAndSettlesOnceWithoutMovingTheAnchor() {
        grantAccess()
        val huge = ScanFixtures.seededTree(ScanFixtures.HUGE)
        ScanFixtures.scanTree(huge)
        ScanPrefs.seedSelected(huge)
        HomePrefs.seedSort(HomeSortOrder.NAME)
        SettingsSeeder.seed(Preferences.listFilterKey, ListFilter.ALL.name)
        MetadataWorkerTestHooks.pauseDispatch()

        val appendBaseline = ScanDebugSignals.appendPublications().size
        val baseline = scanBaseline()
        launchHome()
        awaitDiscoveryAfter(baseline)
        ScanCache.awaitCountUnder(huge, ScanFixtures.HUGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)

        val partials = Await.untilValue(
            "the committed adapter exposes the large scan before metadata completion",
            Await.VERY_LONG_TIMEOUT_MS,
            read = { ScanDebugSignals.appendPublications().drop(appendBaseline).filter { it.isNotEmpty() } },
        ) { publications ->
            publications.any { it.size == ScanFixtures.HUGE_COUNT }
        }
        partials.zipWithNext().forEach { (before, after) ->
            assertEquals(
                "Every active-scan publication preserves the already visible prefix",
                before,
                after.take(before.size),
            )
            val suffix = after.drop(before.size)
            assertEquals(
                "Only the newly appended suffix is sorted within its batch",
                canonicalStableKeys(suffix),
                suffix,
            )
        }
        val fullPreSettlement = partials.last { it.size == ScanFixtures.HUGE_COUNT }
        val settlementBaseline = ScanDebugSignals.settlementCommits()
        val anchorTarget = fullPreSettlement[fullPreSettlement.size / 2]
        home.library().cellAt(fullPreSettlement.size / 2).performLongClickForSetupAndAwaitChecked()
        home.awaitSelectionStarted()
        home.library().scrollToStableKey(anchorTarget, ANCHOR_OFFSET)
        TestEnvironment.waitForIdleSync()
        val anchorBefore = Await.untilNotNull(
            "a fixture row is the first visible library row before settlement",
            Await.LONG_TIMEOUT_MS,
        ) { home.library().firstVisibleAnchor() }
        assertNotNull(anchorBefore)

        val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
        scanner.resumeMetadataDispatch()
        val completed = ScanLifecycle.awaitCompletedAfter(baseline.generation)
        home.awaitScanCommitted(completed, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals("Selection never blocks backend completion", ScanPhase.IDLE, scanner.index.value.phase)
        assertTrue("The real selection remains active after backend completion", home.isSelectionToolbarShown())
        home.library().awaitItemCount(ScanFixtures.HUGE_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        val blockedKeys = home.library().items().map { it.stableKey }
        assertEquals(
            "The complete committed adapter is visible while canonical settlement is blocked",
            fullPreSettlement,
            blockedKeys,
        )
        ScanDebugSignals.appendPublications().drop(appendBaseline)
            .dropWhile { it.size < ScanFixtures.HUGE_COUNT }.forEach { publication ->
                assertEquals("Page-count content updates never move row keys", fullPreSettlement, publication)
            }
        assertEquals(
            "No canonical UI settlement is committed while selection is active",
            settlementBaseline,
            ScanDebugSignals.settlementCommits(),
        )

        home.pressBack()
        home.awaitSelectionEnded()
        Await.until("releasing selection commits canonical UI order", Await.LONG_TIMEOUT_MS) {
            ScanDebugSignals.settlementCommits() > settlementBaseline
        }
        awaitScanComplete(baseline)
        assertEquals(
            "The continuous scan session publishes exactly one canonical settlement",
            1,
            ScanDebugSignals.settlementCommits() - settlementBaseline,
        )
        val finalKeys = home.library().items().map { it.stableKey }
        assertEquals("The final adapter list uses canonical NAME plus URI order", canonicalStableKeys(finalKeys), finalKeys)
        assertEquals(
            "The debug settlement snapshot is the actual canonical adapter order",
            finalKeys,
            ScanDebugSignals.settlementPublications().last(),
        )
        val anchorAfter = Await.untilNotNull(
            "the same stable row and pixel offset are restored after settlement",
            Await.LONG_TIMEOUT_MS,
        ) {
            home.library().firstVisibleAnchor()?.takeIf {
                it.stableKey == anchorBefore.stableKey && it.pixelOffset == anchorBefore.pixelOffset
            }
        }
        assertEquals(anchorBefore, anchorAfter)
    }

    @Test
    @TestId("SCAN-36")
    fun refreshesDuringAScanAreCoalescedIntoOneFollowUpScan() {
        grantAccess()
        val huge = ScanFixtures.seededTree(ScanFixtures.HUGE)
        ScanFixtures.scanTree(huge)
        ScanPrefs.seedSelected(huge)
        HomePrefs.seedSort(HomeSortOrder.NAME)
        MetadataWorkerTestHooks.pauseDispatch()

        val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
        val baseline = scanBaseline()
        launchHome()
        val first = awaitDiscoveryAfter(baseline)
        assertEquals("Metadata is held while refresh requests arrive", ScanPhase.READING_DETAILS, first.phase)
        repeat(EXTRA_REFRESHES) { scanner.refresh(ScanRefreshReason.MANUAL) }
        scanner.resumeMetadataDispatch()
        val final = ScanLifecycle.awaitCompletedAfter(first.generation)
        home.awaitScanCommitted(final, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(
            "All concurrent requests coalesce into exactly one follow-up discovery generation",
            2,
            ScanDebugSignals.discoveryRuns() - baseline.discoveryRuns,
        )
        assertEquals(
            "The final committed UI order is canonical regardless of intermediate snapshot conflation",
            canonicalStableKeys(home.library().items().map { it.stableKey }),
            home.library().items().map { it.stableKey },
        )
        assertEquals(
            "The actor never runs competing discovery producers",
            1,
            ScanDebugSignals.maxDiscoveryProducers(),
        )

        val rows = ScanCache.awaitCountUnder(huge, ScanFixtures.HUGE_COUNT)
        assertEquals("No duplicate paths were written", rows.size, rows.map { it.path }.toSet().size)

        val pull = refreshLibrary()
        awaitScanComplete(pull)
        home.library().awaitRefreshFinished(Await.VERY_LONG_TIMEOUT_MS)
        assertFalse("The swipe refresh spinner is not left showing", home.library().isRefreshing())
    }

    private fun startWatching(): ScanIndexWatch = ScanIndexWatch.started().also { watch = it }

    private fun canonicalStableKeys(keys: List<String>): List<String> = keys.sortedWith(
        compareBy<String> {
            File(Uri.parse(it).path ?: it).nameWithoutExtension.lowercase()
        }.thenBy { it }
    )

    private fun assertProgressRowStaysAbsent() {
        Await.assertThroughout(
            condition = "no scan progress row is committed in the Library",
            windowMs = Await.ABSENCE_WINDOW_MS,
        ) { home.library().scanProgress() == null }
    }

    private companion object {
        const val EXTRA_REFRESHES = 3
        const val ANCHOR_OFFSET = 37
    }
}
