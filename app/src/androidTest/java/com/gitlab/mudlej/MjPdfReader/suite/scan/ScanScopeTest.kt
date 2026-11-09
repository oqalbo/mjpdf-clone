// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.home.StorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScanScopeTest : ScanTestCase() {

    @Test
    @TestId("SCAN-16")
    fun anEmptyFolderAsTheOnlyScanLocationProducesAnEmptyLibrary() {
        grantAccess()
        val root = testDirectory()
        val empty = ScanFixtures.createDirectory(File(root, "emptyfolder"))
        val elsewhere = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, root, "elsewhere.pdf")
        ScanPrefs.seedSelected(empty)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        home.library().awaitItemCount(0, Await.VERY_LONG_TIMEOUT_MS)
        home.library().awaitEmptyState(R.string.home_empty_all_title, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "The empty state carries home_empty_all_message",
            R.string.home_empty_all_message,
            home.library().emptyState()?.messageRes,
        )

        ScanCache.assertNoRowsUnder(empty)
        val outsideRow = ScanCache.awaitRowExists(elsewhere)
        ScanCache.assertUnhashed(
            outsideRow,
            "A file outside the scan scope is cached by the MediaStore pass but never hashed or listed",
        )
    }

    @Test
    @TestId("SCAN-17")
    fun nonPdfFilesAreIgnoredAndUppercaseExtensionsAreAccepted() {
        grantAccess()
        val mixed = ScanFixtures.copyTree(ScanFixtures.MIXED, testDirectory())
        ScanPrefs.seedSelected(mixed)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        awaitScanComplete(fullRescanLibrary())

        ScanCache.awaitCountUnder(mixed, ScanFixtures.MIXED_INDEXED.size)
        val names = ScanCache.namesUnder(mixed)
        ScanFixtures.MIXED_INDEXED.forEach {
            assertTrue("ScannedPdfCache holds a row for $it, it holds $names", names.contains(it))
        }
        ScanFixtures.MIXED_IGNORED.forEach {
            assertFalse("ScannedPdfCache must hold no row for $it, it holds $names", names.contains(it))
        }

        home.library().awaitItemCount(ScanFixtures.MIXED_INDEXED.size, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(
            "Only the two PDFs reach the grid",
            listOf("REPORT-UPPER", "report"),
            home.library().titles().sorted(),
        )
    }

    @Test
    @TestId("SCAN-18")
    fun nestedFoldersAreWalkedRecursively() {
        grantAccess()
        val nested = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.NESTED))
        val deep = ScanFixtures.placeWithoutScan(
            File(ScanFixtures.seededTree(ScanFixtures.NESTED), ScanFixtures.NESTED_DEEP_RELATIVE),
            File(nested, ScanFixtures.NESTED_DEEP_RELATIVE),
        )
        ScanPrefs.seedSelected(nested)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        awaitScanComplete(fullRescanLibrary())

        val row = ScanCache.awaitHashed(deep)
        assertEquals("The row path is the file three levels down", deep.absolutePath, row.path)
        ScanCache.assertCurrentIdentity(row, "The row for the deep file")

        home.library().awaitContains("deep", Await.VERY_LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("SCAN-19")
    fun aNomediaMarkerDoesNotExcludePdfsFromTheSelectedFolder() {
        grantAccess()
        val root = testDirectory()
        val scanme = ScanFixtures.createDirectory(File(root, "scanme"))
        val private = ScanFixtures.createDirectory(File(scanme, "private"))
        ScanFixtures.writeMarker(File(private, ".nomedia"))
        val secret = ScanFixtures.placeWithoutScan(
            ScanFixtures.sharedFile(ScanFixtures.NORMAL),
            File(private, "secret.pdf"),
        )
        val open = ScanFixtures.placeWithoutScan(
            ScanFixtures.sharedFile(ScanFixtures.NORMAL),
            File(scanme, "open.pdf"),
        )
        val scanned = Fixtures.scanAndWait(open)
        assertNotNull(
            "The explicitly indexed open PDF must have a MediaStore URI",
            scanned[open.absolutePath],
        )

        val discovered = StorageManager().readAllFiles(listOf(scanme))
            .filter { it.isFile && it.extension == "pdf" }.toSet()
        assertEquals("The filesystem walk includes PDFs below .nomedia", setOf(open, secret), discovered)

        ScanPrefs.seedSelected(scanme)
        launchHomeAndScan()
        awaitScanComplete(fullRescanLibrary())
        assertEquals(
            "The selected folder includes both PDFs regardless of MediaStore indexing",
            setOf(open.absolutePath, secret.absolutePath),
            ScanCache.under(scanme).map { it.path }.toSet(),
        )
        home.library().awaitItemCount(2, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(listOf("open", "secret"), home.library().titles().sorted())

        ScanPrefs.seedSelected(private)
        awaitScanComplete(fullRescanLibrary())

        val rows = ScanCache.awaitAllHashedUnder(scanme, 2)
        assertEquals(
            "Narrowing the selection does not create duplicate cache paths",
            setOf(open.absolutePath, secret.absolutePath),
            rows.map { it.path }.toSet(),
        )
        ScanCache.assertCurrentIdentity(rows.single { it.path == secret.absolutePath }, "The explicitly selected PDF")
        home.library().awaitItemCount(1, Await.VERY_LONG_TIMEOUT_MS)
        assertEquals(
            "Only the explicitly selected private root is rendered in Library",
            listOf("secret"),
            home.library().titles().sorted(),
        )
    }

    @Test
    @TestId("SCAN-20")
    fun filesOutsideTheSelectedLocationsAreCachedButNeverHashedOrListed() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, root)
        val one = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, File(root, "downloads"), "one.pdf")
        val two = ScanFixtures.copySharedAs(ScanFixtures.LARGE, File(root, "movies"), "two.pdf")
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        val inScope = ScanCache.awaitAllHashedUnder(bulk, ScanFixtures.BULK_COUNT)
        inScope.forEach { ScanCache.assertCurrentIdentity(it, "The in scope row for ${it.path}") }

        ScanCache.assertUnhashed(ScanCache.awaitRowExists(one), "The row for ${one.absolutePath}")
        ScanCache.assertUnhashed(ScanCache.awaitRowExists(two), "The row for ${two.absolutePath}")

        home.library().awaitItemCount(ScanFixtures.BULK_COUNT, Await.VERY_LONG_TIMEOUT_MS)
        assertFalse("An out of scope file is not listed", home.library().hasItem("one"))
        assertFalse("An out of scope file is not listed", home.library().hasItem("two"))
    }

    @Test
    @TestId("SCAN-21")
    fun theSameFileInTwoScanLocationsProducesTwoEntriesWithOneHash() {
        grantAccess()
        val dupe = ScanFixtures.copyTree(ScanFixtures.DUPE, testDirectory())
        val left = File(dupe, "left")
        val right = File(dupe, "right")
        ScanPrefs.seedSelected(left, right)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        val leftRow = ScanCache.awaitHashed(File(left, "same.pdf"))
        val rightRow = ScanCache.awaitHashed(File(right, "same.pdf"))
        ScanCache.awaitCountUnder(dupe, 2)

        assertEquals("Byte identical copies share one identity", leftRow.hash, rightRow.hash)
        assertEquals("Byte identical copies share one size", leftRow.size, rightRow.size)
        ScanCache.assertCurrentIdentity(leftRow, "The left copy")

        home.library().awaitItemCount(2, Await.VERY_LONG_TIMEOUT_MS)
        val items = home.library().items()
        assertEquals("Both copies are titled from the file name", listOf("same", "same"), items.map { it.title })
        assertEquals(
            "Each copy is addressable at its own path",
            2,
            items.map { it.uri.toString() }.toSet().size,
        )
    }

    @Test
    @TestId("SCAN-22")
    fun aDuplicateThatMatchesAnOpenedRecordCollapsesToOneLibraryItem() {
        grantAccess()
        val dupe = ScanFixtures.copyTree(ScanFixtures.DUPE, testDirectory())
        val left = File(dupe, "left")
        val right = File(dupe, "right")
        val leftFile = File(left, "same.pdf")
        ScanPrefs.seedSelected(left, right)
        val record = ScanRecords.seedOpenedRecord(leftFile, pageNumber = 2, length = 4)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        ScanCache.awaitCountUnder(dupe, 2)

        home.library().awaitItemCount(1, Await.VERY_LONG_TIMEOUT_MS)
        val item = home.library().item("same")
        assertEquals(
            "A duplicate that matches an opened record shows as that record's copy",
            ScanRecords.fileUriOf(leftFile),
            item.uri.toString(),
        )
        assertFalse("The merged item is a record, not a scan only item", item.isScanOnly)
        assertEquals("The merged item keeps the record's identity", record.hash, item.hash)
        assertNotNull("The record still exists", ScanRecords.byHash(record.hash))
    }
}
