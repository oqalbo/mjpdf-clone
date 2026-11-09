// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId

import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScanSetupTest : ScanTestCase() {

    @Test
    @TestId("SCAN-01")
    fun scanLocationsEntryPointRequiresAllFilesAccess() {
        ScanFixtures.copyTree(ScanFixtures.BULK, testDirectory())

        denyAccess()
        launchHome()
        home.openOverflowMenu()
        assertFalse(
            "Without the all files grant the scan locations row must not be offered",
            scanLocationsRowDisplayed(),
        )
        assertTrue(
            "The library tab must raise the permission card while the grant is absent",
            home.library().permissionCard() != null,
        )
        assertEquals(
            "The permission card carries home_permission_card_message",
            R.string.home_permission_card_message,
            home.library().permissionCard()?.messageRes,
        )
        home.dismissOverflowMenu()

        grantAccess()
        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        home.openOverflowMenu()
        assertTrue(
            "With the grant the scan locations row must be offered",
            scanLocationsRowDisplayed(),
        )

        home.tapMenuRow(R.id.scanLocationsRow)
        awaitScanChoiceDialog()
        assertTrue(
            "The scan locations row opens the scan setup dialog",
            ScanDialogs.isChoiceDialogShowing(),
        )
    }

    @Test
    @TestId("SCAN-02")
    fun scanSetupDialogDefaultsToWholeDeviceAndAppliesIt() {
        grantAccess()
        val bulk = ScanFixtures.copyTree(ScanFixtures.BULK, testDirectory())
        val debugBefore = ScanDebugSignals.snapshot()
        val mainPid = debugBefore.mainPid

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openScanSetupDialogFromCard()

        assertTrue(
            "The whole device option is preselected when the dialog opens",
            ScanDialogs.isChoiceChecked(R.string.home_scan_choice_whole_device),
        )
        assertFalse(
            "The selected locations option is not preselected",
            ScanDialogs.isChoiceChecked(R.string.home_scan_choice_selected_locations),
        )

        val baseline = scanBaseline()
        ScanDialogs.confirm()
        ScanPrefs.awaitMode(ScanMode.WHOLE_DEVICE)
        ScanPrefs.assertLocationsEmpty("Choosing whole device must not write any scan location")

        awaitScanComplete(baseline)
        val rows = ScanCache.awaitAllHashedUnder(bulk, ScanFixtures.BULK_COUNT)
        assertTrue("Every readable row is COMPLETE with a page count", rows.all { it.pageCount > 0 })
        val debug = ScanDebugSignals.snapshot()
        assertEquals("The foreground process survives metadata extraction", mainPid, debug.mainPid)
        assertTrue(
            "The scan connected a private metadata worker",
            (debug.workerInstances - debugBefore.workerInstances).isNotEmpty(),
        )
        assertTrue("Every metadata PID differs from the main PID", debug.workerPids.all { it != mainPid })
        assertTrue(
            "This scan published real device memory samples",
            debug.realMemorySamples > debugBefore.realMemorySamples,
        )

        Await.until("the scan setup card leaves the library tab", Await.LONG_TIMEOUT_MS) {
            !home.library().hasScanSetupCard()
        }
    }

    @Test
    @TestId("SCAN-03")
    @TestDuration(DurationCategory.SHORT)
    fun cancellingTheLocationsPickerLeavesScanningUnconfigured() {
        grantAccess()
        ScanFixtures.copyTree(ScanFixtures.BULK, testDirectory())

        launchHome()
        home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        openScanSetupDialogFromCard()
        chooseSelectedLocations()

        picker.leaveWithBack()
        awaitPickerGone()

        ScanPrefs.assertModeUnconfigured("Backing out of the picker must not configure scanning")
        ScanPrefs.assertLocationsEmpty("Backing out of the picker must not write a scan location")

        Await.until("the scan setup card is offered again", Await.LONG_TIMEOUT_MS) {
            home.library().hasScanSetupCard()
        }
        assertTrue(
            "No scan only item may reach the library grid while scanning is not configured",
            home.library().items().none { it.isScanOnly },
        )
    }
}
