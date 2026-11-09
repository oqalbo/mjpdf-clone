// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class GoToPageGridRotationTest : ResilienceTestCase() {

    @Test
    @TestId("RES-07")
    fun rotatingThePageGridRestoresItsScrollPosition() {
        val file = fixture(ResilienceFixtures.LARGE)
        val columns = Preferences(TestEnvironment.defaultPreferences).getGoToPageGridColumns()
        val handlesBefore = FileDescriptors.countFor(file)

        ActivityScenario.launch<GoToPageActivity>(gridIntent(file)).use {
            ListScreens.awaitItemsAtLeast(R.id.pageGrid, ResilienceFixtures.LARGE_PAGE_COUNT)
            ListScreens.awaitLaidOut(R.id.pageGrid)
            val columnsBefore = ListScreens.spanCount(R.id.pageGrid)
            val requestedAnchor = rowStartAtOrBefore(DEEP_PAGE_INDEX, columnsBefore)
            ListScreens.onRecycler(R.id.pageGrid) { recycler ->
                recycler.scrollToPosition(requestedAnchor)
            }
            TestEnvironment.uiDevice.waitForIdle()
            TestEnvironment.waitForIdleSync()
            Await.until(
                "the row containing adapter position $DEEP_PAGE_INDEX is visible after the deep grid scroll",
                Await.LONG_TIMEOUT_MS,
            ) {
                ListScreens.firstVisiblePosition(R.id.pageGrid) <= DEEP_PAGE_INDEX &&
                    ListScreens.lastVisiblePosition(R.id.pageGrid) >= DEEP_PAGE_INDEX
            }
            ListScreens.awaitLayoutSettled(R.id.pageGrid)

            val firstBefore = ListScreens.firstVisiblePosition(R.id.pageGrid)
            val spanBefore = ListScreens.spanCount(R.id.pageGrid)
            assertEquals(
                "the grid must lay out with the column count the preference asks for",
                columns,
                spanBefore,
            )
            assertEquals(
                "the actual first visible anchor may differ from the requested cell, but it must be the start " +
                    "of a grid row",
                firstBefore,
                rowStartAtOrBefore(firstBefore, spanBefore),
            )
            val handlesWithGrid = FileDescriptors.countFor(file)
            assertTrue(
                "the grid must hold a descriptor on ${file.absolutePath} before rotating, otherwise the leak " +
                    "assertion below measures nothing",
                handlesWithGrid > handlesBefore,
            )

            Rotation.toLandscape(GoToPageActivity::class.java, Await.LONG_TIMEOUT_MS)
            ListScreens.awaitItemsAtLeast(R.id.pageGrid, ResilienceFixtures.LARGE_PAGE_COUNT)
            ListScreens.awaitLaidOut(R.id.pageGrid)
            ListScreens.awaitLayoutSettled(R.id.pageGrid)

            val firstAfter = ListScreens.firstVisiblePosition(R.id.pageGrid)
            val spanAfter = ListScreens.spanCount(R.id.pageGrid)
            val anchorAfter = rowStartAtOrBefore(firstAfter, spanAfter)
            val rowsOff = abs(anchorAfter - firstBefore).toFloat() / spanBefore
            assertTrue(
                "the grid restores an adapter position from its saved state, so the first visible cell must be " +
                    "within one row of the actual pre-rotation anchor $firstBefore, but it came back at " +
                    "$firstAfter (row anchor $anchorAfter), which is $rowsOff rows away",
                rowsOff <= 1f,
            )
            assertEquals(
                "the recreated grid must keep the configured column count",
                columns,
                spanAfter,
            )
            FileDescriptors.awaitSettledCountOf(file, handlesWithGrid)
        }
    }

    private fun rowStartAtOrBefore(position: Int, columns: Int): Int = position - position % columns

    private fun gridIntent(file: java.io.File): Intent =
        Intent(TestEnvironment.targetContext, GoToPageActivity::class.java).apply {
            putExtra(PDF.filePathKey, Uri.fromFile(file).toString())
            putExtra(PDF.pageNumberKey, 0)
        }

    private companion object {
        const val DEEP_PAGE_INDEX = 149
    }
}
