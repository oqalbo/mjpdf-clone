// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchTraversalTest : SearchTestCase() {

    @Test
    @TestId("SRCH-05")
    fun aMatchOnTheCurrentPageIsPreferredOverLaterPages() {
        openReader(SearchFixtures.TEXT) {
            robot.goToPage(ANCHOR_PAGE_WITH_MATCH)
            robot.search(SearchFixtures.BEACON)

            SearchBar.awaitCounterOf(3, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(ANCHOR_PAGE_WITH_MATCH - 1)
        }
    }

    @Test
    @TestId("SRCH-06")
    @TestDuration(DurationCategory.SHORT)
    fun theFirstJumpGoesForwardToTheFirstMatchAhead() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)

            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())
            reader.awaitFullQualityRender()
            reader.assertFullyRendered()
        }
    }

    @Test
    @TestId("SRCH-07")
    fun matchesBehindTheAnchorPageWrapToTheFirstMatch() {
        openReader(SearchFixtures.TEXT) {
            robot.goToPage(SearchFixtures.TEXT_PAGES)
            robot.search(SearchFixtures.BEACON)

            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())
            assertEquals("the snackbar label after a wrap", SearchBar.resultsLabel(), SearchBar.label())
        }
    }

    @Test
    @TestId("SRCH-08")
    fun nextTraversalWalksTheHitsInDocumentOrder() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[0])

            val counters = mutableListOf(SearchBar.counterText())
            val pages = mutableListOf<Int?>(pageIndex())
            repeat(SearchFixtures.BEACON_HITS - 1) { step ->
                SearchBar.tapNext()
                SearchBar.awaitCounterOf(step + 2, SearchFixtures.BEACON_HITS)
                awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[step + 1])
                counters.add(SearchBar.counterText())
                pages.add(pageIndex())
            }

            assertEquals(
                "the counter sequence walking forward",
                (1..SearchFixtures.BEACON_HITS).map { "$it/${SearchFixtures.BEACON_HITS}" },
                counters,
            )
            assertEquals("the page sequence walking forward", SearchFixtures.BEACON_PAGE_INDEXES, pages)
        }
    }

    @Test
    @TestId("SRCH-09")
    fun nextAtTheLastHitWrapsToTheFirst() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            stepForwardTo(SearchFixtures.BEACON_HITS)

            SearchBar.tapNext()

            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())
        }
    }

    @Test
    @TestId("SRCH-10")
    fun previousAtTheFirstHitWrapsToTheLast() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())

            SearchBar.tapPrevious()

            SearchBar.awaitCounterOf(SearchFixtures.BEACON_HITS, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.last())
        }
    }

    @Test
    @TestId("SRCH-11")
    fun previousTraversalWalksBackwardsThroughEveryHit() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            stepForwardTo(SearchFixtures.BEACON_HITS)

            val counters = mutableListOf<String?>()
            val pages = mutableListOf<Int?>()
            for (position in SearchFixtures.BEACON_HITS - 1 downTo 1) {
                SearchBar.tapPrevious()
                SearchBar.awaitCounterOf(position, SearchFixtures.BEACON_HITS)
                awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[position - 1])
                counters.add(SearchBar.counterText())
                pages.add(pageIndex())
            }

            assertEquals(
                "the counter sequence walking backwards",
                (SearchFixtures.BEACON_HITS - 1 downTo 1).map { "$it/${SearchFixtures.BEACON_HITS}" },
                counters,
            )
            assertEquals(
                "the page sequence walking backwards",
                SearchFixtures.BEACON_PAGE_INDEXES.dropLast(1).reversed(),
                pages,
            )
        }
    }

    private fun stepForwardTo(position: Int) {
        var current = SearchBar.requireCounter().position
        while (current < position) {
            SearchBar.tapNext()
            SearchBar.awaitCounterOf(current + 1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[current])
            current += 1
        }
    }

    companion object {
        const val ANCHOR_PAGE_WITH_MATCH = 7
    }
}
