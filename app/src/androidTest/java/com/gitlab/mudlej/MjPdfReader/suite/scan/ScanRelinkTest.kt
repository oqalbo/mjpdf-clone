// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScanRelinkTest : ScanTestCase() {

    @Test
    @TestId("SCAN-34")
    fun scanResultsRelinkARecordWhoseFileMoved() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.createDirectory(File(root, ScanFixtures.BULK))
        val original = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, bulk, "movable.pdf")
        val record = ScanRecords.seedOpenedRecord(original, pageNumber = 7, length = 20)
        ScanPrefs.seedSelected(root)

        val movedDirectory = ScanFixtures.createDirectory(File(root, "moved"))
        val moved = ScanFixtures.move(original, File(movedDirectory, "movable.pdf"))

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        val healed = ScanRecords.awaitUri(record.hash, ScanRecords.fileUriOf(moved), Await.VERY_LONG_TIMEOUT_MS)
        assertEquals("The reading position survives the move", 7, healed.pageNumber)
        assertEquals("The identity is unchanged", record.hash, healed.hash)

        home.library().awaitItemCount(1, Await.VERY_LONG_TIMEOUT_MS)
        val item = home.library().item("movable")
        assertFalse(
            "The healed record is not a scan only duplicate",
            item.isScanOnly,
        )
        assertEquals("The single item points at the new path", ScanRecords.fileUriOf(moved), item.uri.toString())

        assertNoRelocatePrompt()
    }

    @Test
    @TestId("SCAN-35")
    fun relinkingIsOffWhenHistoryIsDisabled() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.createDirectory(File(root, ScanFixtures.BULK))
        val original = ScanFixtures.copySharedAs(ScanFixtures.NORMAL, bulk, "movable.pdf")
        val record = ScanRecords.seedOpenedRecord(original, pageNumber = 7, length = 20)
        ScanPrefs.seedSelected(root)
        ScanPrefs.seedHistoryEnabled(false)

        val originalUri = ScanRecords.fileUriOf(original)
        val movedDirectory = ScanFixtures.createDirectory(File(root, "moved"))
        val moved = ScanFixtures.move(original, File(movedDirectory, "movable.pdf"))

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        val scanned = ScanCache.awaitHashed(moved)
        assertEquals(
            "The scan found the file at its new path and hashed it to the record's identity, " +
                "so only the relink was suppressed",
            record.hash,
            scanned.hash,
        )

        ScanRecords.assertUriStays(record.hash, originalUri)
        assertTrue(
            "With history off, a moved file does not relink its record",
            ScanRecords.byHash(record.hash)?.uri?.toString() == originalUri,
        )
    }

    private fun assertNoRelocatePrompt() {
        val toast = TestEnvironment.targetString(R.string.home_relocate_found)
        val dialogTitle = TestEnvironment.targetString(R.string.home_relocate_title)
        Await.assertThroughout(
            condition = "no automatic relocation notice or dialog appears",
            windowMs = Await.ABSENCE_WINDOW_MS,
        ) {
            !TestEnvironment.uiDevice.hasObject(By.textContains(toast.substringBefore("%"))) &&
                !TestEnvironment.uiDevice.hasObject(By.text(dialogTitle))
        }
    }
}
