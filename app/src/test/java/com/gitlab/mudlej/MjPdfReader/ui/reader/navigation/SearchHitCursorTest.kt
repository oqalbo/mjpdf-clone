// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import org.junit.Assert.*
import org.junit.Test

class SearchHitCursorTest {
    @Test fun streamingBatchKeepsTheResultTheUserNavigatedTo() {
        val cursor = SearchHitCursor()
        cursor.onResults(results(5), false)
        repeat(3) { assertTrue(cursor.next()) }
        assertEquals(3, cursor.hits[cursor.currentPosition].resultIndex)
        cursor.onResults(results(8), false)
        assertEquals(3, cursor.hits[cursor.currentPosition].resultIndex)
        assertEquals(8, cursor.hits.size)
        val inserted = SearchResult(99, 0, 1, "hit", 1, searchResultIndexInList = 99)
        cursor.onResults(listOf(inserted) + results(8), false)
        assertEquals(4, cursor.currentPosition)
        assertEquals(3, cursor.hits[cursor.currentPosition].resultIndex)
    }

    @Test fun finishedSearchFallsBackToFirstHitWhenAnchorIsPastAllHits() {
        val cursor = pendingCursor(anchor = 99)
        cursor.onResults(results(3), true)
        assertEquals(0, cursor.autoJumpTarget())
        assertEquals(0, cursor.currentPosition)
        assertFalse(cursor.pendingAutoJump)
        assertTrue(cursor.hasFullSession)
        assertFalse(cursor.subscribedToActiveSearch)
    }

    @Test fun runningSearchWaitsForAHitAtOrAfterTheAnchor() {
        val cursor = pendingCursor(anchor = 3)
        cursor.onResults(results(3), false)
        assertNull(cursor.autoJumpTarget())
        assertTrue(cursor.pendingAutoJump)
        assertEquals(-1, cursor.currentPosition)
        cursor.onResults(results(5), false)
        assertEquals(3, cursor.autoJumpTarget())
        assertEquals(4, cursor.hits[cursor.currentPosition].pageNumber)
        assertFalse(cursor.pendingAutoJump)
    }

    @Test fun previousWhilePendingSelectsTheLastHit() {
        val cursor = pendingCursor()
        cursor.onResults(results(4), false)
        assertTrue(cursor.previous())
        assertEquals(3, cursor.currentPosition)
        assertFalse(cursor.pendingAutoJump)
    }

    @Test fun nextWhilePendingSelectsTheFirstHit() {
        val cursor = pendingCursor()
        cursor.onResults(results(4), false)
        assertTrue(cursor.next())
        assertEquals(0, cursor.currentPosition)
        assertFalse(cursor.pendingAutoJump)
    }

    @Test fun navigationWrapsInBothDirections() {
        val cursor = SearchHitCursor()
        cursor.onResults(results(3), true)
        assertTrue(cursor.previous())
        assertEquals(2, cursor.currentPosition)
        assertTrue(cursor.next())
        assertEquals(0, cursor.currentPosition)
    }

    @Test fun singleHitDoesNotNavigateInEitherDirection() {
        val cursor = SearchHitCursor()
        cursor.onResults(results(1), true)
        assertFalse(cursor.next())
        assertFalse(cursor.previous())
        assertEquals(0, cursor.currentPosition)
    }

    @Test fun finishingWithoutHitsEntersTheNoResultsState() {
        val cursor = pendingCursor()
        cursor.onResults(emptyList(), false)
        assertNull(cursor.autoJumpTarget())
        assertFalse(cursor.noResults)
        cursor.onResults(emptyList(), true)
        assertNull(cursor.autoJumpTarget())
        assertTrue(cursor.noResults)
        assertFalse(cursor.pendingAutoJump)
        assertFalse(cursor.controlsVisible())
        assertFalse(cursor.next())
        assertFalse(cursor.previous())
    }

    @Test fun restoredResultIndexMissingFromHitsFallsBackToFirstPosition() {
        val cursor = SearchHitCursor()
        cursor.onResults(results(4), true)
        cursor.restorePosition(3)
        assertEquals(3, cursor.currentPosition)
        cursor.restorePosition(99)
        assertEquals(0, cursor.currentPosition)
    }

    @Test fun singleHitControlsRequireAFullSessionOrLiveSubscription() {
        val cursor = SearchHitCursor()
        cursor.onResults(results(1), false)
        assertFalse(cursor.controlsVisible())
        cursor.subscribedToActiveSearch = true
        assertTrue(cursor.controlsVisible())
        cursor.onResults(results(1), true)
        assertTrue(cursor.controlsVisible())
        assertFalse(cursor.subscribedToActiveSearch)
    }

    private fun pendingCursor(anchor: Int = 0) = SearchHitCursor().apply {
        pendingAutoJump = true
        subscribedToActiveSearch = true
        autoJumpAnchorPage = anchor
    }

    private fun results(count: Int) = (0 until count).map { index ->
        SearchResult(index, 0, 1, "hit", index + 1, searchResultIndexInList = index)
    }
}
