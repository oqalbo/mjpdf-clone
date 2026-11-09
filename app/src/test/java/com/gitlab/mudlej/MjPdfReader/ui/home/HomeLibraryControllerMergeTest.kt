// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase_Impl
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeLibraryControllerMergeTest {
    @get:Rule val files = TemporaryFolder()
    private val pref = Preferences(InMemorySharedPreferences())
    private val controller = HomeLibraryController(PdfRepository(AppDatabase_Impl()), pref, HistoryPolicy(pref))

    private fun entry(name: String, hash: String? = null): ScannedPdfEntry {
        val file = files.newFile(name).apply { writeBytes(ByteArray(17)) }
        return ScannedPdfEntry(file.path, file.length(), 1_000, hash, 30, ScannedPdfMetadataState.COMPLETE)
    }

    @Test fun pathMatchPreservesRecordFieldsAndPositiveSizeAndLength() {
        val scan = entry("book.pdf", "different-hash")
        val record = homeRecordItem(java.io.File(scan.path), "My title").copy(
            length = 12, pageNumber = 4, favorite = true, hidden = true,
            readingStatus = ReadingStatus.READING, lastOpened = LocalDateTime.of(2026, 1, 2, 0, 0),
        )
        assertEquals(17L, record.sizeBytes)
        assertEquals(listOf(record), controller.reconcileWithScan(listOf(record), listOf(scan.copy(size = 900))))
    }

    @Test fun pathMatchFillsOnlyNonpositiveRecordSizeAndLength() {
        val scan = entry("book.pdf")
        for (missing in listOf(0, -1)) {
            val record = homeRecordItem(java.io.File(scan.path)).copy(sizeBytes = missing.toLong(), length = missing)
            assertEquals(listOf(record.copy(sizeBytes = 17, length = 30)), controller.reconcileWithScan(listOf(record), listOf(scan)))
        }
    }

    @Test fun incompleteMetadataCannotSupplyAPageCount() {
        val scan = entry("book.pdf")
        val record = homeRecordItem(java.io.File(scan.path)).copy(length = -1)
        for (state in ScannedPdfMetadataState.entries) {
            val expected = if (state == ScannedPdfMetadataState.COMPLETE) 30 else 0
            assertEquals(expected, controller.reconcileWithScan(listOf(record), listOf(scan.copy(metadataState = state))).single().length)
        }
    }

    @Test fun hashMatchUsesTheMovedFilesRecordWithoutReplacingItsIdentity() {
        val record = homeRecordItem(files.newFile("old.pdf")).copy(favorite = true, pageNumber = 3)
        val moved = entry("moved.pdf", record.hash)
        assertEquals(listOf(record.copy(sizeBytes = 17, length = 30)), controller.reconcileWithScan(listOf(record), listOf(moved)))
    }

    @Test fun reconciliationCollapsesHashCopiesWhileDiscoveryKeepsDistinctPaths() {
        val record = homeRecordItem(files.newFile("old.pdf"))
        val first = entry("first.pdf", record.hash)
        val second = entry("second.pdf", record.hash)
        assertEquals(listOf(record.copy(sizeBytes = 17, length = 30)), controller.reconcileWithScan(listOf(record), listOf(first, second)))
        assertEquals(listOf(HomeItem.fromScan(first), HomeItem.fromScan(second)), controller.mergeDuringDiscovery(listOf(record), listOf(first, second, first)))
    }

    @Test fun blankSearchReturnsTheOriginalRecordsWithoutScanRows() {
        val record = homeRecordItem(files.newFile("book.pdf"))
        val records = listOf(record)
        assertSame(records, controller.searchAll(records, listOf(entry("scan.pdf")), " \t"))
    }

    @Test fun searchCombinesTitleAndScanMatchesExcludesHashMatchesAndSorts() {
        pref.setHomeSort(HomeSortOrder.NAME)
        val matching = homeRecordItem(files.newFile("z.pdf"), "Zebra manual")
        val nonmatching = homeRecordItem(files.newFile("hidden.pdf"), "Unrelated")
        val scan = entry("Alpha MANUAL.pdf")
        val hashCopy = entry("Manual copy.pdf", nonmatching.hash)
        val unrelated = entry("Unrelated.pdf")
        assertEquals(listOf(HomeItem.fromScan(scan), matching), controller.searchAll(listOf(nonmatching, matching), listOf(hashCopy, unrelated, scan), "manual"))
    }

    @Test fun noMissingFileRecordsReturnsTheSameIndexInstance() {
        val scan = entry("available.pdf")
        val record = homeRecordItem(java.io.File(scan.path)).copy(availability = Availability.ACCESS_UNKNOWN)
        val index = LibraryIndex(entries = listOf(scan))
        assertSame(index, controller.excludeMissingRecordFiles(listOf(record), index))
    }

    @Test fun missingFilterRemovesOnlyFileSchemePathsAndKeepsIndexMetadata() {
        val missing = entry("missing.pdf")
        val unknown = entry("unknown.pdf")
        val provider = entry("provider.pdf")
        val records = listOf(
            homeRecordItem(java.io.File(missing.path)).copy(availability = Availability.MISSING),
            homeRecordItem(java.io.File(unknown.path)).copy(availability = Availability.ACCESS_UNKNOWN),
            homeRecordItem(java.io.File(provider.path)).copy(uri = Uri.parse("content://books${provider.path}"), availability = Availability.MISSING),
        )
        val index = LibraryIndex(entries = listOf(missing, unknown, provider), loaded = true, generation = 9)
        assertEquals(index.copy(entries = listOf(unknown, provider)), controller.excludeMissingRecordFiles(records, index))
    }
}
