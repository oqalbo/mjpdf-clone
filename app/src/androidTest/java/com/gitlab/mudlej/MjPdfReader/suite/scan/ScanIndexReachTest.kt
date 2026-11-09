// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataFailure
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScanIndexReachTest : ScanTestCase() {

    @Test
    @TestId("SCAN-41")
    fun theFoldersTabShowsScanEntriesTheLibraryTabFiltersOut() {
        grantAccess()
        val root = testDirectory()
        val bulk = ScanFixtures.createDirectory(File(root, ScanFixtures.BULK))
        ScanFixtures.copyBulkMembers(1..3, bulk)
        val outsideDirectory = ScanFixtures.createDirectory(File(root, "outsidefolder"))
        ScanFixtures.copySharedAs(ScanFixtures.NORMAL, outsideDirectory, "outside.pdf")
        ScanPrefs.seedSelected(bulk)
        ScanPrefs.seedFolderFlat(false)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        home.library().awaitItemCount(3, Await.VERY_LONG_TIMEOUT_MS)
        assertFalse(
            "The library tab honours the selected scan location",
            home.library().hasItem("outside"),
        )

        val automation = androidx.test.uiautomator.Configurator.getInstance()
        val idleTimeout = automation.waitForIdleTimeout
        try {
            automation.waitForIdleTimeout = 0L
            home.selectTab(HomeTab.FOLDERS)
        } finally {
            automation.waitForIdleTimeout = idleTimeout
        }
        home.awaitRendered(Await.LONG_TIMEOUT_MS)

        home.folders().openFolderPath(*pathSegments(root))
        val folderNames = home.folders().folderNames()
        assertTrue(
            "A folder outside the scan scope is still navigable in Folders. Folder rows read $folderNames",
            folderNames.contains("outsidefolder"),
        )

        home.folders().openFolderPath("outsidefolder")
        home.folders().awaitContains("outside", Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("SCAN-42")
    fun aCorruptPdfIsMarkedFailedAndLaterReadableRowsComplete() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        val broken = ScanFixtures.copySharedAs(ScanFixtures.CORRUPT, bulk, "broken.pdf")
        ScanFixtures.copyBulkMembers(1..2, bulk)
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)

        ScanCache.awaitCountUnder(bulk, 3)
        val brokenRow = ScanCache.awaitFailed(
            broken,
            ScannedPdfMetadataFailure.INVALID_PDF,
            Await.VERY_LONG_TIMEOUT_MS,
        )
        assertEquals("unreadable metadata has no page count", 0, brokenRow.pageCount)
        ScanCache.assertCurrentIdentity(
            brokenRow,
            "The identity comes from the bytes and needs no parseable document, so the broken file",
        )

        listOf(1, 2).forEach { index ->
            val file = File(bulk, ScanFixtures.bulkName(index))
            val row = ScanCache.awaitRow(
                file.absolutePath,
                "the readable fixture ${file.name} completes metadata",
                Await.VERY_LONG_TIMEOUT_MS,
            ) {
                it?.metadataState == ScannedPdfMetadataState.COMPLETE && it.pageCount > 0
            }!!
            assertTrue(
                "One broken file must not stop later metadata, ${row.path} reads ${row.pageCount}",
                row.pageCount > 0,
            )
        }

        home.library().awaitContains("broken", Await.VERY_LONG_TIMEOUT_MS)
    }

    private fun pathSegments(directory: File): Array<String> {
        val relative = directory.absolutePath
            .removePrefix(Fixtures.externalRoot.absolutePath)
            .trim(File.separatorChar)
        return relative.split(File.separatorChar).filter { it.isNotBlank() }.toTypedArray()
    }
}
