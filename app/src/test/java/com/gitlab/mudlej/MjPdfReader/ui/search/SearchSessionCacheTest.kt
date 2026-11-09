// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SearchSessionCacheTest {

    @Test
    fun sixthSessionEvictsTheFirstInsertedSession() {
        val hashes = List(6) { uniqueHash() }
        hashes.forEach { SearchSessionCache.put(it, "q", false, listOf(hit(10))) }

        assertNull(SearchSessionCache.get(hashes.first(), "q", false))
        hashes.drop(1).forEach {
            assertEquals(listOf(hit(10)), SearchSessionCache.get(it, "q", false)?.hits)
        }
    }

    @Test
    fun readingTheOldestSessionMakesTheSecondSessionTheEvictionTarget() {
        val hashes = List(6) { uniqueHash() }
        hashes.take(5).forEach { SearchSessionCache.put(it, "q", false, listOf(hit(10))) }
        val refreshed = requireNotNull(SearchSessionCache.get(hashes.first(), "q", false))

        SearchSessionCache.put(hashes.last(), "q", false, listOf(hit(20)))

        assertNull(SearchSessionCache.get(hashes[1], "q", false))
        assertSame(refreshed, SearchSessionCache.get(hashes.first(), "q", false))
        hashes.subList(2, 5).forEach {
            assertEquals(listOf(hit(10)), SearchSessionCache.get(it, "q", false)?.hits)
        }
        assertEquals(listOf(hit(20)), SearchSessionCache.get(hashes.last(), "q", false)?.hits)
    }

    @Test
    fun fiveThousandHitsAreKept() {
        val hash = uniqueHash()
        val hits = List(5000) { hit(it) }

        SearchSessionCache.put(hash, "q", false, hits)

        assertEquals(hits, SearchSessionCache.get(hash, "q", false)?.hits)
    }

    @Test
    fun fiveThousandAndOneHitsRemoveTheExistingSession() {
        val hash = uniqueHash()
        SearchSessionCache.put(hash, "q", false, listOf(hit(10)))
        assertEquals(listOf(hit(10)), SearchSessionCache.get(hash, "q", false)?.hits)

        SearchSessionCache.put(hash, "q", false, List(5001) { hit(it) })

        assertNull(SearchSessionCache.get(hash, "q", false))
    }

    @Test
    fun paddedAndTrimmedQueriesShareOneSessionKey() {
        val hash = uniqueHash()
        SearchSessionCache.put(hash, " q ", false, listOf(hit(10)))
        val session = requireNotNull(SearchSessionCache.get(hash, "q", false))
        assertEquals("q", session.key.query)
        assertSame(session, SearchSessionCache.get(hash, " q ", false))

        SearchSessionCache.put(hash, "q", false, listOf(hit(20)))

        assertEquals(listOf(hit(20)), SearchSessionCache.get(hash, " q ", false)?.hits)
    }

    @Test
    fun blankHashesCannotStoreSessions() {
        val query = uniqueHash()
        for (hash in listOf(null, "", " \t\n")) {
            SearchSessionCache.put(hash, query, false, listOf(hit(10)))

            assertNull(SearchSessionCache.get(hash, query, false))
        }
    }

    @Test
    fun blankQueriesCannotStoreSessions() {
        val hash = uniqueHash()
        for (query in listOf("", " \t\n")) {
            SearchSessionCache.put(hash, query, false, listOf(hit(10)))

            assertNull(SearchSessionCache.get(hash, query, false))
        }
    }

    @Test
    fun accentMatchingModesKeepSeparateSessions() {
        val hash = uniqueHash()
        SearchSessionCache.put(hash, "q", false, listOf(hit(10)))
        SearchSessionCache.put(hash, "q", true, listOf(hit(20)))

        assertEquals(listOf(hit(10)), SearchSessionCache.get(hash, "q", false)?.hits)
        assertEquals(listOf(hit(20)), SearchSessionCache.get(hash, "q", true)?.hits)
    }

    @Test
    fun blankNestedQueryClearsThePreviousQueryWhileUpdatingScrollState() {
        val hash = uniqueHash()
        SearchSessionCache.put(hash, "q", false, listOf(hit(10)))
        SearchSessionCache.updateUiState(hash, "q", false, 2, 8, "nested")
        assertEquals("nested", SearchSessionCache.get(hash, "q", false)?.nestedQuery)

        SearchSessionCache.updateUiState(hash, "q", false, 4, -15, " \t\n")

        val session = requireNotNull(SearchSessionCache.get(hash, "q", false))
        assertNull(session.nestedQuery)
        assertEquals(4, session.listPosition)
        assertEquals(-15, session.listOffsetPx)
        assertEquals(listOf(hit(10)), session.hits)
    }

    @Test
    fun updatingAHitMatchesItsResultIndexAndPreservesOtherHitIdentities() {
        val hash = uniqueHash()
        SearchSessionCache.put(hash, "q", false, listOf(hit(10), hit(30), hit(20)))
        SearchSessionCache.updateUiState(hash, "q", false, 2, -9, "nested")
        val before = requireNotNull(SearchSessionCache.get(hash, "q", false))

        SearchSessionCache.updateHit(hash, "q", false, 30, true, "long q snippet", 5, 6)

        val after = requireNotNull(SearchSessionCache.get(hash, "q", false))
        assertEquals(3, after.hits.size)
        assertSame(before.hits[0], after.hits[0])
        assertSame(before.hits[2], after.hits[2])
        assertNotSame(before.hits[1], after.hits[1])
        assertEquals(
            SearchSessionCache.Hit(3, 42, 30, true, "long q snippet", 5, 6),
            after.hits[1],
        )
        assertEquals(before.copy(hits = after.hits), after)
    }

    private fun hit(resultIndex: Int) = SearchSessionCache.Hit(3, 42, resultIndex, false, "q", 0, 1)

    private fun uniqueHash() = "SearchSessionCacheTest-${nextHash.incrementAndGet()}"

    companion object {
        private val nextHash = AtomicInteger()
    }
}
