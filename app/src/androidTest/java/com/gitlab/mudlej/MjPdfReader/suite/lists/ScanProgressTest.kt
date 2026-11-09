// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ScanProgressTest : ListsTestCase() {

    @Test
    @TestId("LIST-07")
    fun theScanProgressBarAppearsAndThenDisappears() {
        openReader(ListsFixtures.HUGE) { _, _ ->
            val list = openHighlightsListWithoutIdleWait()
            val running = awaitScanSnapshot(list, "the scan progress bar is visible") { it.scanning }

            assertEquals(
                "the determinate progress bar is scaled to the page count",
                ListsFixtures.HUGE_PAGES,
                running.max,
            )
            assertFalse(
                "the indeterminate bar is still showing while the determinate one runs",
                running.indeterminateShown,
            )

            list.awaitScanFinished()
            assertFalse("the scan progress bar is still visible", list.scanProgressVisible())
            assertEquals(
                "the scan progress reached the last page",
                ListsFixtures.HUGE_PAGES,
                list.scanProgress(),
            )
        }
    }

    @Test
    @TestId("LIST-08")
    fun rowsAppearInBatchesBeforeTheScanFinishes() {
        openReader(ListsFixtures.HUGE) { _, _ ->
            val list = openHighlightsListWithoutIdleWait()

            val midScan = awaitScanSnapshot(list, "the list holds rows while the scan is still running") {
                it.itemCount > 0 && it.scanning
            }

            assertFalse("the empty state was still shown at that moment", midScan.messageShown)

            list.awaitScanFinished()
            list.awaitItemCount(ListsFixtures.HUGE_GROUPS)
        }
    }

    @Test
    @TestId("LIST-50")
    @TestDuration(DurationCategory.NORMAL)
    fun leavingMidScanCancelsTheScanAndReleasesTheDocument() {
        openReader(ListsFixtures.HUGE) { _, file ->
            val readerOnlyHandles = FileDescriptors.countFor(file)

            val list = openHighlightsListWithoutIdleWait()
            awaitScanSnapshot(list, "the scan reports partial progress") {
                it.scanning && it.progress > 0 && it.progress < ListsFixtures.HUGE_PAGES
            }
            FileDescriptors.awaitAtLeast(file, readerOnlyHandles + 1)

            list.pressBack()
            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitBackInReader()

            assertEquals("the resumed activity", MainActivity::class.java.name, Screens.resumedName())
            FileDescriptors.awaitCount(file, readerOnlyHandles)

            val secondScan = openHighlightsAndScan()
            secondScan.awaitItemCount(ListsFixtures.HUGE_GROUPS)
        }
    }

    private fun awaitScanSnapshot(
        list: HighlightsListRobot,
        condition: String,
        predicate: (ScanSnapshot) -> Boolean,
    ): ScanSnapshot = requireNotNull(
        Await.untilValue(
            condition = condition,
            timeoutMs = Await.VERY_LONG_TIMEOUT_MS,
            pollMs = SCAN_POLL_MS,
            read = { list.scanSnapshotOrNull() },
        ) { it != null && predicate(it) }
    )

    private companion object {
        const val SCAN_POLL_MS = 10L
    }
}
