// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchOracle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchCoordinator
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AbandonedWorkTest : ResilienceTestCase() {

    @After
    fun clearSearchState() {
        runCatching { SearchCoordinator.clearDebugProgress() }
    }

    @Test
    @TestId("RES-51")
    fun cancellingMidScanAndLeavingReleasesTheSearchDocument() {
        val file = fixture(ResilienceFixtures.LARGE)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val hash = reader.awaitFileHashResolved()
        val listener = SilentListener()
        val completedBefore = SearchTestHooks.searchCompletionCount()
        val readerHandles = FileDescriptors.awaitSettledCount(file)
        assertTrue("the reader handle is observable", readerHandles > 0)
        SearchOracle.blockAfterNextResultBatch()
        try {
            startSearch(file, hash, listener)
            SearchOracle.awaitResultBatchBlocked()
            val running = checkNotNull(SearchIdle.progress())
            assertTrue("the held scan has pages left", !running.finished && running.pagesScanned in 1 until running.pageCount)
            assertTrue("the search owns an additional handle", FileDescriptors.countFor(file) > readerHandles)

            SearchCoordinator.cancel(hash, SEARCH_TERM, IGNORE_ACCENTS)
            SearchCoordinator.unsubscribe(listener)
            assertNull("cancel removes the active search", SearchIdle.progress())
            ReaderProbe.finishAndRemoveReaderTasks()
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            SearchOracle.releaseResultBatch()
            FileDescriptors.awaitSettledCountOf(file, 0, ABANDON_TIMEOUT_MS)
            assertEquals("cancelled work must not finish the scan", completedBefore, SearchTestHooks.searchCompletionCount())
            assertNull("cancelled work stores no session", SearchSessionCache.get(hash, SEARCH_TERM, IGNORE_ACCENTS))
        } finally {
            SearchOracle.releaseResultBatch()
            SearchCoordinator.unsubscribe(listener)
        }
    }

    @Test
    @TestId("RES-52")
    fun changingReflowDuringAnInFlightLoadReloadsEveryVisiblePage() {
        val file = fixture(ResilienceFixtures.LARGE)
        ActivityScenario.launch<TextModeActivity>(TextModeProbe.intentFor(file)).use {
            TextModeProbe.awaitPagePopulated(0)

            ListScreens.scrollToPosition(R.id.textPagesRecyclerView, DEEP_PAGE)
            toggleJoinParagraphs()

            Await.until("every visible text mode page finishes loading", Await.VERY_LONG_TIMEOUT_MS) {
                visiblePositions().isNotEmpty() && visiblePositions().all { isReady(it) }
            }
            val visible = visiblePositions()
            assertTrue(
                "the reflow change must leave every visible page bound and filled, but positions $visible " +
                    "did not all report text",
                visible.isNotEmpty() && visible.all { isReady(it) },
            )
            assertFalse(
                "the reflow change must not leave a page showing stale hidden text",
                visible.any { position ->
                    TextModeRobot().snapshot(position)?.pageTextVisible == false
                },
            )
            FileDescriptors.awaitSettledCountOf(file, 1)
        }
    }

    private fun toggleJoinParagraphs() {
        val text = TextModeRobot()
        text.openTypographySheetForUiAutomation()
        text.tapVisibleSheetControl(R.id.joinParagraphsSwitch)
    }

    private fun visiblePositions(): List<Int> {
        val first = runCatching { ListScreens.firstVisiblePosition(R.id.textPagesRecyclerView) }.getOrDefault(-1)
        val last = runCatching { ListScreens.lastVisiblePosition(R.id.textPagesRecyclerView) }.getOrDefault(-2)
        if (first < 0 || last < first) return emptyList()
        return (first..last).toList()
    }

    private fun isReady(position: Int): Boolean {
        val snapshot = TextModeRobot().snapshot(position) ?: return false
        return snapshot.pageTextVisible && !snapshot.pageText.isNullOrBlank()
    }

    private fun startSearch(file: java.io.File, hash: String, listener: SearchCoordinator.Listener) {
        val activity = ReaderProbe.requireResumedReader()
        SearchCoordinator.startOrSubscribe(
            activity,
            android.net.Uri.fromFile(file).toString(),
            null,
            hash,
            SEARCH_TERM,
            IGNORE_ACCENTS,
            listener,
        )
    }

    private class SilentListener : SearchCoordinator.Listener {
        override fun onProgress(pagesScanned: Int, pageCount: Int) = Unit
        override fun onResults(results: List<SearchResult>, finished: Boolean) = Unit
    }

    private companion object {
        const val SEARCH_TERM = "the"
        const val IGNORE_ACCENTS = false
        const val ABANDON_TIMEOUT_MS = 15_000L
        const val DEEP_PAGE = 79
    }
}
