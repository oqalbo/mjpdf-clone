// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.fingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryScanner
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScanRefreshTest : ScanTestCase() {
    @After
    fun resetMetadataHooks() = MetadataWorkerTestHooks.reset()

    @Test
    @TestId("SCAN-23")
    fun unindexedFilesRequireFullRescanRatherThanPullToRefresh() {
        grantAccess()
        val privateRoot = File(TestEnvironment.targetContext.cacheDir, "scan23-private-${UUID.randomUUID()}")
        try {
            ScanFixtures.createDirectory(privateRoot)
            (1..5).forEach {
                ScanFixtures.placeWithoutScan(ScanFixtures.bulkMember(it), File(privateRoot, ScanFixtures.bulkName(it)))
            }
            ScanPrefs.seedSelected(privateRoot)

            launchHomeAndScan()
            ScanCache.awaitCountUnder(privateRoot, 0)
            awaitScanComplete(fullRescanLibrary())
            val initialRows = ScanCache.awaitAllHashedUnder(privateRoot, 5)
            home.library().awaitItemCount(5, Await.LONG_TIMEOUT_MS)

            ScanFixtures.placeWithoutScan(ScanFixtures.bulkMember(6), File(privateRoot, ScanFixtures.bulkName(6)))
            ScanFixtures.placeWithoutScan(ScanFixtures.bulkMember(7), File(privateRoot, ScanFixtures.bulkName(7)))

            val directories = ScanDebugSignals.filesystemDirectories()
            val second = refreshLibrary()
            awaitScanComplete(second)
            assertEquals("Quick refresh retains exactly the five cached rows", initialRows, ScanCache.under(privateRoot))
            assertEquals("Pull does not walk directories", directories, ScanDebugSignals.filesystemDirectories())
            home.library().awaitItemCount(5, Await.LONG_TIMEOUT_MS)
            assertFalse("Pull does not walk for unindexed files", home.library().hasItem("bulk-06"))
            assertFalse("Pull does not walk for unindexed files", home.library().hasItem("bulk-07"))

            awaitScanComplete(fullRescanLibrary())

            val rows = ScanCache.awaitAllHashedUnder(privateRoot, 7)
            val added = rows.filter { it.path.endsWith(ScanFixtures.bulkName(6)) || it.path.endsWith(ScanFixtures.bulkName(7)) }
            assertEquals("Both new files became rows", 2, added.size)
            added.forEach { ScanCache.assertCurrentIdentity(it, "The newly walked row for ${it.path}") }

            home.library().awaitItemCount(7, Await.LONG_TIMEOUT_MS)
            home.library().awaitContains("bulk-06", Await.VERY_LONG_TIMEOUT_MS)
            home.library().awaitContains("bulk-07", Await.VERY_LONG_TIMEOUT_MS)
        } finally {
            privateRoot.deleteRecursively()
        }
    }

    @Test
    @TestId("SCAN-24")
    fun filesDeletedOnDiskArePrunedFromTheCacheAndTheList() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        ScanFixtures.copyBulkMembers(1..5, bulk)
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)
        ScanCache.awaitCountUnder(bulk, 5)
        home.library().awaitItemCount(5, Await.VERY_LONG_TIMEOUT_MS)

        ScanFixtures.delete(File(bulk, ScanFixtures.bulkName(2)))
        ScanFixtures.delete(File(bulk, ScanFixtures.bulkName(3)))

        val second = refreshLibrary()
        awaitScanComplete(second)

        val rows = ScanCache.awaitCountUnder(bulk, 3)
        val names = rows.map { File(it.path).name }
        assertFalse("The deleted file is pruned", names.contains(ScanFixtures.bulkName(2)))
        assertFalse("The deleted file is pruned", names.contains(ScanFixtures.bulkName(3)))

        home.library().awaitItemCount(3, Await.VERY_LONG_TIMEOUT_MS)
        assertFalse("The deleted file leaves the grid", home.library().hasItem("bulk-02"))
        assertFalse("The deleted file leaves the grid", home.library().hasItem("bulk-03"))
    }

    @Test
    @TestId("SCAN-27")
    @TestDuration(DurationCategory.SHORT)
    fun anUnchangedFileIsNotReHashedOnAFullRescan() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        ScanFixtures.copyBulkMembers(1..5, bulk)
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)
        val before = ScanCache.awaitAllHashedUnder(bulk, 5).associateBy { it.path }
        val dispatchesBefore = before.mapValues { ScanDebugSignals.dispatchCount(it.value.fingerprint) }

        val second = fullRescanLibrary()
        awaitScanComplete(second)

        val after = ScanCache.awaitCountUnder(bulk, 5).associateBy { it.path }
        assertEquals("The row set is unchanged", before.keys, after.keys)
        before.forEach { (path, original) ->
            val current = after.getValue(path)
            assertEquals("The hash for $path is preserved rather than recomputed", original.hash, current.hash)
            assertEquals("The page count for $path is preserved", original.pageCount, current.pageCount)
            assertEquals("The size for $path is unchanged", original.size, current.size)
            assertTrue("The page count for $path stays a real count", current.pageCount > 0)
            assertEquals(
                "An unchanged complete fingerprint is not dispatched again",
                dispatchesBefore.getValue(path),
                ScanDebugSignals.dispatchCount(current.fingerprint),
            )
        }
    }

    @Test
    @TestId("SCAN-28")
    fun aFileChangedOnDiskIsReHashedAndItsPageCountRefreshed() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        val target = ScanFixtures.copyFile(ScanFixtures.bulkMember(1), File(bulk, "target.pdf"))
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)
        val before = ScanCache.awaitHashed(target)
        assertTrue("The first pass produced a real page count", before.pageCount > 0)

        MetadataWorkerTestHooks.pauseDispatch()
        ScanFixtures.copyFile(ScanFixtures.sharedFile(ScanFixtures.LARGE), target)

        val scanner = LibraryScanner.getInstance(TestEnvironment.targetContext)
        val staleRejections = ScanDebugSignals.staleResultRejections()
        val second = refreshLibrary()
        val replacementPending = ScanCache.awaitRow(
            target.absolutePath,
            "the replacement fingerprint is durable and pending before metadata dispatch",
            Await.VERY_LONG_TIMEOUT_MS,
        ) {
            it != null && it.size != before.size &&
                it.metadataState == ScannedPdfMetadataState.PENDING
        }!!
        scanner.injectStaleMetadataResultForTest(before.fingerprint, "h2-stale-result", 999)
        Await.until("the old fingerprint callback is rejected", Await.LONG_TIMEOUT_MS) {
            ScanDebugSignals.staleResultRejections() > staleRejections
        }
        val afterStale = ScanCache.byPath(target)
        assertEquals(replacementPending.fingerprint, afterStale?.fingerprint)
        assertEquals(ScannedPdfMetadataState.PENDING, afterStale?.metadataState)
        assertNotEquals("h2-stale-result", afterStale?.hash)
        scanner.resumeMetadataDispatch()
        awaitScanComplete(second)
        val after = ScanCache.awaitRow(
            target.absolutePath,
            "the row for ${target.absolutePath} reports the replacement document",
            Await.VERY_LONG_TIMEOUT_MS,
        ) { it != null && it.size != before.size && !it.hash.isNullOrEmpty() && it.hash != before.hash }!!

        ScanCache.awaitCountUnder(bulk, 1)
        assertEquals("The row still points at the same path", target.absolutePath, after.path)
        assertEquals("The size matches the replacement on disk", target.length(), after.size)
        assertNotEquals("The identity was recomputed", before.hash, after.hash)
        ScanCache.assertCurrentIdentity(after, "The recomputed row")

        val settled = ScanCache.awaitRow(
            target.absolutePath,
            "the replacement document's page count is read back",
            Await.VERY_LONG_TIMEOUT_MS,
        ) { it != null && it.pageCount > 0 }!!
        assertEquals(
            "The page count is the replacement document's own",
            ScanFixtures.LARGE_PAGE_COUNT,
            settled.pageCount,
        )
        assertNotEquals("The page count changed with the content", before.pageCount, settled.pageCount)
    }
}
