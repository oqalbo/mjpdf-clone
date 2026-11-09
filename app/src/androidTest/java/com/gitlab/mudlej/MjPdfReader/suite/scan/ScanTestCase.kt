// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.home.HomeBaselines
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanLocationsActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import org.junit.After
import org.junit.Rule
import java.io.File
import com.gitlab.mudlej.MjPdfReader.ui.home.StorageManager

data class ScanBaseline(
    val generation: Long,
    val discoveryRuns: Int,
)

abstract class ScanTestCase {

    @get:Rule
    val suite = SuiteRules.standard()

    protected val home = HomeRobot()
    protected val picker = ScanLocationsRobot()

    @After
    fun stopSimulatingStorageAccess() {
        Fixtures.stopSimulatingAccess()
    }

    protected fun testDirectory(): File = suite.testDirectory.directory()

    protected fun grantAccess() = HomeBaselines.grantAccess()

    protected fun denyAccess() = HomeBaselines.denyAccess()

    protected fun scanBaseline(): ScanBaseline =
        ScanBaseline(
            generation = ScanLifecycle.generation(),
            discoveryRuns = ScanDebugSignals.discoveryRuns(),
        )

    protected fun awaitDiscoveryAfter(
        baseline: ScanBaseline,
        timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS,
    ): LibraryIndex =
        ScanLifecycle.awaitDiscoveryFinishedAfter(baseline.generation, timeoutMs)

    protected fun awaitScanComplete(
        baseline: ScanBaseline,
        timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS,
    ): LibraryIndex {
        val index = ScanLifecycle.awaitCompletedAfter(baseline.generation, timeoutMs)
        home.awaitScanCommitted(index, timeoutMs)
        return index
    }

    protected fun launchHome(): HomeRobot {
        home.launchFromLauncher(Await.LONG_TIMEOUT_MS)
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        return home
    }

    protected fun launchHomeAndScan(): LibraryIndex {
        val baseline = scanBaseline()
        launchHome()
        return awaitScanComplete(baseline)
    }

    protected fun relaunchHome(): HomeRobot {
        val previousHome = Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
        val baselineGeneration = home.generation()
        TestEnvironment.onMainThread { previousHome.finish() }
        Await.until("the previous Home task is destroyed", Await.LONG_TIMEOUT_MS) {
            previousHome.isDestroyed
        }
        home.launchFromLauncher(Await.LONG_TIMEOUT_MS)
        Await.untilValue(
            "a distinct replacement Home is resumed",
            Await.LONG_TIMEOUT_MS,
            read = { TestEnvironment.resumedActivityOfType(HomeActivity::class.java) },
        ) { it != null && it !== previousHome }
        home.awaitGenerationAtLeast(baselineGeneration + 1, Await.LONG_TIMEOUT_MS)
        return home
    }

    protected fun refreshLibrary(): ScanBaseline {
        val baseline = scanBaseline()
        home.library().pullToRefresh()
        return baseline
    }

    protected fun fullRescanLibrary(): ScanBaseline {
        val baseline = scanBaseline()
        home.openOverflowMenu().tapMenuRow(R.id.fullRescanRow)
        return baseline
    }

    protected fun openScanSetupDialogFromMenu(): ScanTestCase {
        home.openOverflowMenu()
        home.tapMenuRow(R.id.scanLocationsRow)
        awaitScanChoiceDialog()
        return this
    }

    protected fun openScanSetupDialogFromCard(): ScanTestCase {
        Await.until("the library tab shows the scan setup card", Await.LONG_TIMEOUT_MS) {
            home.library().hasScanSetupCard()
        }
        home.tapVisible(R.id.scanSetupButton)
        awaitScanChoiceDialog()
        return this
    }

    protected fun awaitScanChoiceDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        ScanDialogs.awaitChoiceDialog(timeoutMs)
    }

    protected fun chooseWholeDevice() {
        ScanDialogs.selectChoice(R.string.home_scan_choice_whole_device)
        ScanDialogs.confirm()
    }

    protected fun chooseSelectedLocations() {
        ScanDialogs.selectChoice(R.string.home_scan_choice_selected_locations)
        ScanDialogs.confirm()
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
    }

    protected fun openPickerFromMenu(): ScanLocationsRobot {
        openScanSetupDialogFromMenu()
        chooseSelectedLocations()
        return picker
    }

    protected fun awaitPickerGone() {
        Screens.awaitGone(ScanLocationsActivity::class.java, Await.LONG_TIMEOUT_MS)
        Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    protected fun scanLocationsRowDisplayed(): Boolean = home.isMenuRowDisplayed(R.id.scanLocationsRow)

    protected fun volumeRoots(): List<File> =
        StorageManager()
            .volumeRoots(TestEnvironment.targetContext)
}
