// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.data.entity.fingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSection
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScanAvailabilityTest : ScanTestCase() {

    @Test
    @TestId("SCAN-31")
    fun aScanLocationThatDisappearsEmptiesTheLibraryButKeepsTheSetting() {
        grantAccess()
        val removable = ScanFixtures.createDirectory(File(testDirectory(), "removable"))
        ScanFixtures.copyBulkMembers(1..5, removable)
        ScanPrefs.seedSelected(removable)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)
        ScanCache.awaitCountUnder(removable, 5)
        home.library().awaitItemCount(5, Await.VERY_LONG_TIMEOUT_MS)

        ScanFixtures.delete(removable)

        val second = refreshLibrary()
        awaitScanComplete(second)

        ScanCache.assertNoRowsUnder(removable)
        home.library().awaitItemCount(0, Await.VERY_LONG_TIMEOUT_MS)
        home.library().awaitEmptyState(R.string.home_empty_all_title, Await.LONG_TIMEOUT_MS)

        assertEquals(
            "The user's folder choice is kept so the library refills when the storage returns",
            setOf(removable.absolutePath),
            ScanPrefs.locations(),
        )
        assertTrue("The mode is untouched", ScanPrefs.modeIs(ScanMode.SELECTED_LOCATIONS))
    }

    @Test
    @TestId("SCAN-32")
    fun withoutAllFilesAccessNothingIsScanned() {
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, testDirectory())
        ScanPrefs.seedWholeDevice()
        denyAccess()

        launchHome()
        home.library().pullToRefresh()

        ScanCache.assertNoRowsUnder(bulk)
        assertTrue(
            "Without the grant nothing is cached at all",
            ScanCache.all().isEmpty(),
        )

        val sections = home.library().sections()
        assertTrue(
            "The permission card is the first section in the library tab, the sections read $sections",
            sections.firstOrNull() is HomeSection.PermissionCard,
        )
        assertEquals(
            "The card carries home_permission_card_message",
            R.string.home_permission_card_message,
            (sections.first() as HomeSection.PermissionCard).messageRes,
        )

        Await.assertThroughout(
            condition = "no scan progress row is ever shown while the grant is absent",
            windowMs = Await.ABSENCE_WINDOW_MS,
        ) { home.library().scanProgress() == null }

        home.openOverflowMenu()
        assertFalse(
            "The scan locations row stays hidden without the grant, even with the mode already set",
            scanLocationsRowDisplayed(),
        )
        assertTrue("The seeded mode alone is not enough", ScanPrefs.modeIs(ScanMode.WHOLE_DEVICE))
    }

    @Test
    @TestId("SCAN-33")
    @SdkSuppress(minSdkVersion = 30)
    fun grantingAccessLaterCachesMediaStoreForFoldersWithoutWalkingOrReadingMetadata() {
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        val files = ScanFixtures.copyBulkMembers(1..5, bulk)
        ScanPrefs.seedFolderFlat(false)
        denyAccess()

        launchHome()
        assertTrue(
            "The permission card is offered while the grant is absent",
            home.library().permissionCard() != null,
        )

        home.tapVisible(R.id.grantAccessButton)
        awaitRationaleDialog()
        tapRationaleContinue()

        awaitSystemSettingsScreen()
        val baseline = scanBaseline()
        val queries = ScanDebugSignals.mediaStoreQueries()
        val directories = ScanDebugSignals.filesystemDirectories()
        val metadataCommits = ScanDebugSignals.committedResults()
        val workerInstances = ScanDebugSignals.snapshot().workerInstances
        Fixtures.grantAllFilesAccess()
        Fixtures.stopSimulatingAccess()
        Await.until("the all files grant is visible to the app", Await.SHORT_TIMEOUT_MS) {
            PermissionManager.hasFullAccess(TestEnvironment.targetContext)
        }
        TestEnvironment.uiDevice.pressBack()
        home.awaitShownAndReturn(Await.LONG_TIMEOUT_MS)

        Await.until("the permission card leaves the library tab", Await.LONG_TIMEOUT_MS) {
            home.library().permissionCard() == null
        }
        Await.until("the scan setup card takes its place", Await.LONG_TIMEOUT_MS) {
            home.library().hasScanSetupCard()
        }
        ScanPrefs.assertModeUnconfigured("Granting access must not configure a scan mode by itself")

        awaitScanComplete(baseline)
        val rows = ScanCache.awaitCountUnder(bulk, 5)
        assertEquals(files.map { it.absolutePath }.toSet(), rows.map { it.path }.toSet())
        rows.forEach {
            ScanCache.assertUnhashed(it, "Unconfigured discovery caches ${it.path} for Folders without metadata")
            assertEquals("Unconfigured rows are never dispatched", 0, ScanDebugSignals.dispatchCount(it.fingerprint))
        }
        assertTrue("Granting access refreshes the global MediaStore cache", ScanDebugSignals.mediaStoreQueries() > queries)
        ScanPrefs.assertModeUnconfigured("The global Folders cache must not configure Library scanning")

        home.library().awaitItemCount(0, Await.VERY_LONG_TIMEOUT_MS)
        assertTrue("Library continues to offer scan setup", home.library().hasScanSetupCard())
        val folders = home.selectTab(HomeTab.FOLDERS)
        folders.openFolderPath(*bulk.relativeTo(Fixtures.externalRoot).path.split(File.separatorChar).toTypedArray())
        folders.awaitItemCount(5, Await.LONG_TIMEOUT_MS)
        assertEquals(files.map { it.absolutePath }.toSet(), folders.items().map { it.uri.path }.toSet())
        assertEquals("Unconfigured discovery does not walk directories", directories, ScanDebugSignals.filesystemDirectories())
        assertEquals("Unconfigured discovery does not commit metadata", metadataCommits, ScanDebugSignals.committedResults())
        assertEquals("Unconfigured discovery does not bind a metadata worker", workerInstances, ScanDebugSignals.snapshot().workerInstances)
    }

    private fun awaitRationaleDialog() {
        val title = TestEnvironment.targetString(R.string.storage_permission_rationale_title)
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(title)), Await.DEFAULT_TIMEOUT_MS) != true) {
            throw AssertionError("The storage rationale dialog titled \"$title\" never appeared")
        }
    }

    private fun tapRationaleContinue() {
        val label = TestEnvironment.targetString(R.string.storage_permission_continue)
        val button = TestEnvironment.uiDevice.findObject(By.text(label))
            ?: throw AssertionError("The rationale dialog offers no \"$label\" button")
        button.click()
        TestEnvironment.uiDevice.waitForIdle()
    }

    private fun awaitSystemSettingsScreen() {
        Await.until(
            "the system all files access screen takes the foreground",
            Await.LONG_TIMEOUT_MS,
        ) { TestEnvironment.uiDevice.currentPackageName != TestEnvironment.targetPackage }
    }
}
