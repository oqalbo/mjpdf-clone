// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScanObserverTest : ScanTestCase() {

    @Test
    @TestId("SCAN-38")
    @TestDuration(DurationCategory.SHORT)
    fun aFileAppearingOnDiskIsPickedUpWithoutUserAction() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        ScanFixtures.copyBulkMembers(1..3, bulk)
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)
        ScanCache.awaitCountUnder(bulk, 3)
        val observerBaseline = scanBaseline()

        ScanFixtures.copyFile(ScanFixtures.bulkMember(4), File(bulk, ScanFixtures.bulkName(4)))
        awaitScanComplete(observerBaseline)

        home.library().awaitContains("bulk-04", OBSERVER_TIMEOUT_MS)
        ScanCache.awaitCountUnder(bulk, 4, OBSERVER_TIMEOUT_MS)

        Await.until("observer scan progress leaves after final settlement", OBSERVER_TIMEOUT_MS) {
            home.library().scanProgress() == null
        }
    }

    @Test
    @TestId("SCAN-39")
    fun theObserverStopsWhileHomeIsInTheBackground() {
        grantAccess()
        val bulk = ScanFixtures.createDirectory(File(testDirectory(), ScanFixtures.BULK))
        ScanFixtures.copyBulkMembers(1..1, bulk)
        ScanPrefs.seedSelected(bulk)

        val baseline = scanBaseline()
        launchHome()
        awaitScanComplete(baseline)
        ScanCache.awaitCountUnder(bulk, 1)
        val foregroundBaseline = scanBaseline()
        val foregroundHome = Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS)

        TestEnvironment.uiDevice.pressHome()
        Screens.awaitStage(foregroundHome, Stage.STOPPED, Await.LONG_TIMEOUT_MS)

        ScanFixtures.copyFile(ScanFixtures.bulkMember(2), File(bulk, ScanFixtures.bulkName(2)))

        ScanCache.assertCountUnderStays(bulk, 1, BACKGROUND_WINDOW_MS)

        home.resumeExistingTask(Await.LONG_TIMEOUT_MS)
        assertSame(
            "Foregrounding Home resumes the stopped activity instead of replacing its task",
            foregroundHome,
            Screens.awaitResumed(HomeActivity::class.java, Await.LONG_TIMEOUT_MS),
        )
        home.awaitRendered(Await.LONG_TIMEOUT_MS)
        awaitScanComplete(foregroundBaseline)

        ScanCache.awaitCountUnder(bulk, 2, OBSERVER_TIMEOUT_MS)
        home.library().awaitContains("bulk-02", OBSERVER_TIMEOUT_MS)
    }

    private companion object {
        const val OBSERVER_TIMEOUT_MS = 30_000L
        const val BACKGROUND_WINDOW_MS = 6_000L
    }
}
