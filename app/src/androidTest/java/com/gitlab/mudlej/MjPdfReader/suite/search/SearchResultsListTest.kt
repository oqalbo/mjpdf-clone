// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchResultsListTest : SearchTestCase() {

    @Test
    @TestId("SRCH-16")
    fun theResultsListOpensFromTheNavigationBar() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)

            SearchBar.tapLabel()
            results.awaitShown()

            assertEquals(
                "the results screen title",
                results.titleFor(SearchFixtures.BEACON_HITS),
                results.title(),
            )
            results.awaitItemCount(SearchFixtures.BEACON_HITS)
            results.awaitRowBound(0)
            assertEquals(
                "the first row page label",
                results.pageLabelFor(SearchFixtures.BEACON_PAGE_NUMBERS.first()),
                results.pageLabel(0),
            )
            assertTrue(
                "the first row snippet must carry the query, it reads ${results.rowText(0)}",
                results.rowText(0)?.contains(SearchFixtures.BEACON, ignoreCase = true) == true,
            )
            assertEquals(
                "a cached session must not make the determinate progress bar visible",
                View.GONE,
                results.determinateProgressVisibility(),
            )
        }
    }

    @Test
    @TestId("SRCH-17")
    fun resultsAppearProgressivelyDuringALongSearch() {
        openReader(SearchFixtures.LARGE) {
            SearchOracle.blockAfterNextResultBatch()
            robot.searchWithoutSettling(SearchFixtures.LARGE_COMMON_WORD)
            SearchBar.awaitShown()
            SearchBar.tapLabelWithoutSettling()
            results.awaitShown()
            SearchOracle.awaitResultBatchBlocked()
            val firstCount = results.awaitAtLeastOneRow()
            val firstProgress = results.determinateProgress()
            results.awaitRowBound(0)
            val firstRowIdentity = results.pageLabel(0) to results.rowText(0)

            SearchOracle.releaseResultBatch()
            results.awaitSearchFinished()
            val finalCount = results.awaitItemCountMatchesTitle()
            val finalProgress = results.determinateProgress()

            assertTrue(
                "the first populated sample must hold fewer rows than the finished list, it held " +
                    "$firstCount of $finalCount",
                firstCount in 1 until finalCount,
            )
            assertTrue(
                "the row count must not fall while batches arrive, it went from $firstCount to $finalCount",
                finalCount >= firstCount,
            )
            assertTrue(
                "the determinate progress must not fall, it went from $firstProgress to $finalProgress",
                finalProgress >= firstProgress,
            )
            assertEquals(
                "the finished row count must equal the count parsed from the title",
                finalCount,
                results.awaitTitleCount(),
            )
            assertEquals(
                "the identity of the first row must not change while batches arrive",
                firstRowIdentity,
                results.pageLabel(0) to results.rowText(0),
            )
        }
    }

    @Test
    @TestId("SRCH-18")
    fun scrollPositionSurvivesIncomingResultBatches() {
        openReader(SearchFixtures.LARGE) {
            SearchOracle.blockAfterNextResultBatch()
            robot.search(SearchFixtures.LARGE_COMMON_WORD)
            SearchBar.awaitShown()
            SearchBar.tapLabel()
            results.awaitShown()
            SearchOracle.awaitResultBatchBlocked()
            Await.untilValue(
                "the parked first batch holds enough rows to scroll to $SCROLL_ROWS",
                Await.LONG_TIMEOUT_MS,
                read = { results.itemCount() },
            ) { it > SCROLL_ROWS }

            results.scrollToPosition(SCROLL_ROWS)
            results.awaitScrollIdle()
            val positionBefore = results.firstVisiblePosition()
            results.awaitRowBound(positionBefore)
            val textBefore = results.rowText(positionBefore)
            val offsetBefore = results.firstVisibleOffset()
            val countBefore = results.itemCount()

            SearchOracle.releaseResultBatch()
            Await.untilValue(
                "another batch of results arrives",
                Await.VERY_LONG_TIMEOUT_MS,
                read = { results.itemCount() },
            ) { it > countBefore }

            assertEquals(
                "the first visible position must survive an incoming batch",
                positionBefore,
                results.firstVisiblePosition(),
            )
            assertEquals(
                "the text of the top visible row must survive an incoming batch",
                textBefore,
                results.rowText(positionBefore),
            )
            SearchIdle.awaitFinished()
            results.awaitSearchFinished()
            results.awaitItemCountMatchesTitle()
            results.awaitScrollIdle()
            Await.assertThroughout(
                condition = "completion preserves both the visible row and its pixel offset",
                windowMs = 500L,
            ) {
                results.firstVisiblePosition() == positionBefore && results.firstVisibleOffset() == offsetBefore
            }
        }
    }

    @Test
    @TestId("SRCH-19")
    fun jumpingToAResultFromTheListSynchronisesTheNavigationBar() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(SearchFixtures.BEACON_HITS)

            results.tapRow(TARGET_ROW)
            Screens.awaitTopIs(MainActivity::class.java)
            robot.awaitReaderResumed()

            SearchBar.awaitCounterOf(TARGET_ROW + 1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[TARGET_ROW])
            assertEquals("previous arrow visibility", View.VISIBLE as Int?, SearchBar.previousVisibility())
            assertEquals("next arrow visibility", View.VISIBLE as Int?, SearchBar.nextVisibility())
            assertEquals("previous arrow enabled", true as Boolean?, SearchBar.previousEnabled())
            assertEquals("next arrow enabled", true as Boolean?, SearchBar.nextEnabled())

            val marked = reader.awaitState("the search marker set covers the jumped page") {
                it.markerPages?.contains(SearchFixtures.BEACON_PAGE_INDEXES[TARGET_ROW]) == true
            }
            assertTrue(
                "the jump must have drawn a marker, the hook reports ${marked.markerPages}",
                marked.markerPages?.contains(SearchFixtures.BEACON_PAGE_INDEXES[TARGET_ROW]) == true,
            )
        }
    }

    @Test
    @TestId("SRCH-20")
    fun showMoreExpandsAResultAndTheExpansionSticks() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(SearchFixtures.BEACON_HITS)

            val collapsed = results.awaitRowText(0)
            results.tapShowMore(0)
            val expanded = Await.untilValue(
                "the first row rebinds with a longer snippet",
                read = { results.rowText(0) },
            ) { it != null && it.length > collapsed.length }!!
            Await.until("the expanded row hides its show more button") {
                results.showMoreVisibility(0) == View.GONE
            }

            val hit = Await.untilNotNull("the cached session records the expansion") {
                SearchOracle.hits(hash, SearchFixtures.BEACON).firstOrNull { it.resultIndex == 0 }
                    ?.takeIf { it.expanded }
            }
            assertEquals("the cached snippet must match the expanded row", expanded, hit.text)

            TestEnvironment.uiDevice.pressBack()
            Screens.awaitTopIs(MainActivity::class.java)
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitShown()
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitItemCount(SearchFixtures.BEACON_HITS)
            results.awaitRowBound(0)

            assertEquals("the reopened row must still be expanded", expanded, results.rowText(0))
            assertEquals(
                "the reopened row must still hide its show more button",
                View.GONE as Int?,
                results.showMoreVisibility(0),
            )
        }
    }

    companion object {
        const val SCROLL_ROWS = 10
        const val TARGET_ROW = 3
    }
}
