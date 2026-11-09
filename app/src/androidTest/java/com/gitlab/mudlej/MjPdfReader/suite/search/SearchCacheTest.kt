// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchCacheTest : SearchTestCase() {

    @Test
    @TestId("SRCH-21")
    fun theResultsListCanBeFilteredWithoutRescanning() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            SearchIdle.awaitFinished()
            val startsBefore = SearchTestHooks.searchStartCount()
            assertTrue("the initial scan was observed", startsBefore > 0)
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(SearchFixtures.BEACON_HITS)

            results.openFilter(SearchFixtures.FILTER_WORD)

            results.awaitItemCount(SearchFixtures.FILTER_REMAINING_ROWS)
            SearchNotices.awaitFilteredResults(SearchFixtures.FILTER_REMAINING_ROWS)
            results.awaitRowBound(0)
            results.awaitRowBound(1)
            assertEquals(
                "the filtered rows must both sit on the page carrying the distinguishing word",
                listOf(
                    results.pageLabelFor(SearchFixtures.FILTER_REMAINING_PAGE_NUMBER),
                    results.pageLabelFor(SearchFixtures.FILTER_REMAINING_PAGE_NUMBER),
                ),
                listOf(results.pageLabel(0), results.pageLabel(1)),
            )
            assertEquals(
                "filtering must not rescan the document",
                View.GONE,
                results.determinateProgressVisibility(),
            )
            assertEquals("cached results must not request another document scan", startsBefore, SearchTestHooks.searchStartCount())
        }
    }

    @Test
    @TestId("SRCH-23")
    fun reopeningTheResultsListAfterAFinishedSearchUsesTheCache() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            SearchIdle.awaitFinished()
            val startsBefore = SearchTestHooks.searchStartCount()
            assertTrue("the initial scan was observed", startsBefore > 0)
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(SearchFixtures.BEACON_HITS)
            results.scrollToPosition(THIRD_ROW)
            results.awaitScrollIdle()
            val recordedPosition = results.firstVisiblePosition()

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitTopIs(MainActivity::class.java)
            robot.awaitReaderResumed()

            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitShown()
            SearchBar.tapLabel()
            results.awaitShown()

            assertEquals(
                "the second visit must read the cache rather than rescanning",
                View.GONE,
                results.determinateProgressVisibility(),
            )
            results.awaitItemCount(SearchFixtures.BEACON_HITS)
            assertEquals(
                "the title on the second visit",
                results.titleFor(SearchFixtures.BEACON_HITS),
                results.title(),
            )
            Await.until("the list restores the position recorded before leaving") {
                results.firstVisiblePosition() == recordedPosition
            }
            assertEquals("cached results must not request another document scan", startsBefore, SearchTestHooks.searchStartCount())
        }
    }

    @Test
    @TestId("SRCH-54")
    fun theResultsScreenOnAnUnreadableDocumentSaysSoAndCloses() {
        openReader(SearchFixtures.TEXT) { copy ->
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)

            val since = SearchLogcat.since()
            Fixtures.delete(copy)
            Await.untilFileAbsent(copy)

            SearchBar.tapLabel()

            Toasts.awaitText(SearchNotices.unreadableDocument())
            results.awaitGone(Await.LONG_TIMEOUT_MS)
            Screens.awaitTopIs(MainActivity::class.java)
            assertTrue(
                "the reader must be the resumed screen again",
                TestEnvironment.resumedActivityOfType(MainActivity::class.java) != null,
            )
            SearchLogcat.assertNoUnhandledExceptionSince(since)
        }
    }

    companion object {
        const val THIRD_ROW = 2
    }
}
