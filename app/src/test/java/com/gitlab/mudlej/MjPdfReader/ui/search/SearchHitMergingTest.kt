// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SearchHitMergingTest {

    @Test
    fun equalSizeResultsReturnThePreviousListInstance() {
        val results = results()
        val previous = SearchCoordinator.cacheHits(results)

        assertSame(previous, SearchCoordinator.appendHits(previous, results))
    }

    @Test
    fun growingResultsPreserveEveryPreviousHitByIdentity() {
        val results = results()
        val previous = SearchCoordinator.cacheHits(results.take(2))

        val merged = SearchCoordinator.appendHits(previous, results)

        assertEquals(3, merged.size)
        previous.indices.forEach { assertSame(previous[it], merged[it]) }
        assertEquals(
            SearchSessionCache.Hit(9, 82, 30, true, "a query suffix", 2, 7),
            merged.last(),
        )
    }

    @Test
    fun shrinkingResultsRebuildEveryRemainingHit() {
        val results = results()
        val previous = SearchCoordinator.cacheHits(results)

        val merged = SearchCoordinator.appendHits(previous, results.take(2))

        assertEquals(2, merged.size)
        merged.indices.forEach {
            assertEquals(previous[it], merged[it])
            assertNotSame(previous[it], merged[it])
        }
    }

    @Test
    fun mismatchedLastResultIndexRebuildsEveryHitEvenAtEqualSize() {
        val results = results()
        val previous = SearchCoordinator.cacheHits(results)
        val replacement = results.dropLast(1) + results.last().copy(searchResultIndexInList = 99)

        val merged = SearchCoordinator.appendHits(previous, replacement)

        assertEquals(listOf(10, 20, 99), merged.map { it.resultIndex })
        previous.indices.forEach { assertNotSame(previous[it], merged[it]) }
        assertEquals(previous[0], merged[0])
        assertEquals(previous[1], merged[1])
        assertEquals(previous[2].copy(resultIndex = 99), merged[2])
    }

    @Test
    fun firstBatchCopiesSnippetAndNavigationFieldsIntoHits() {
        val merged = SearchCoordinator.appendHits(emptyList(), results())

        assertEquals(
            listOf(
                SearchSessionCache.Hit(3, 40, 10, false, "query here", 0, 5),
                SearchSessionCache.Hit(6, 61, 20, false, "some query", 5, 10),
                SearchSessionCache.Hit(9, 82, 30, true, "a query suffix", 2, 7),
            ),
            merged,
        )
    }

    private fun results() = listOf(
        SearchResult(40, 0, 5, "query here", 3, searchResultIndexInList = 10),
        SearchResult(61, 5, 10, "some query", 6, searchResultIndexInList = 20),
        SearchResult(82, 2, 7, "a query suffix", 9, expanded = true, searchResultIndexInList = 30),
    )
}
