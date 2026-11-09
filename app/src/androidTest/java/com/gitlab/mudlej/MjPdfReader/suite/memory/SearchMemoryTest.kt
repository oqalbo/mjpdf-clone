// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.FixtureResource
import com.gitlab.mudlej.MjPdfReader.suite.core.RequiresFixture
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import java.util.Collections
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchMemoryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val search = SearchRobot(reader)

    @Before
    fun seedAccentsOff() {
        SettingsSeeder.seed(Preferences.searchIgnoreAccentsKey, false)
    }

    @After
    fun releaseSearchProgress() {
        runCatching { SearchIdle.clear() }
    }

    @Test
    @TestId("MEM-04")
    fun aCompletedSearchReleasesItsExtractor() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val hash = reader.awaitFileHashResolved()
            val readerOnly = FileDescriptors.countFor(fixture)
            assertEquals(
                "the reader alone must hold exactly one descriptor before the search opens its own copy",
                1,
                readerOnly,
            )

            val progress = MemorySearch.runToCompletion(search, hash, MemoryFixtures.LARGE_UNIQUE_TERM)
            assertEquals(
                "a completed search must have walked every page of the fixture",
                progress.pageCount,
                progress.pagesScanned,
            )

            assertTrue(
                "the reader search bar must still be up, because this test asserts the release happens " +
                    "without leaving the results behind",
                SearchBar.isShown(),
            )
            MemoryReader.awaitDescriptorCount(fixture, readerOnly)
            MemoryReader.holdDescriptorCount(fixture, readerOnly)
            assertEquals(
                "SearchCoordinator.runSearch closes its extractor in a finally block, so the descriptor " +
                    "count must be back to the reader's own while the results are still displayed",
                readerOnly,
                FileDescriptors.countFor(fixture),
            )

            assertNotNull(
                "a completed search must leave a cached session, otherwise the release above was a " +
                    "cancelled search rather than a finished one",
                MemorySearch.session(hash, MemoryFixtures.LARGE_UNIQUE_TERM),
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
    }

    @Test
    @TestId("MEM-21")
    @TestDuration(DurationCategory.LONG)
    fun theSearchSessionCacheHoldsAtMostFiveSessions() {
        val fixture = MemoryFixtures.file(MemoryFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val hash = reader.awaitFileHashResolved()

            val hitCounts = SIX_QUERIES.associateWith { query -> runQuery(hash, query) }

            val empty = hitCounts.filterValues { it == 0 }.keys
            assertEquals(
                "every one of the six queries must produce at least one hit, otherwise an empty session was " +
                    "cached and the eviction order below proves nothing about real result sets. These " +
                    "returned nothing: $empty",
                emptySet<String>(),
                empty,
            )
            assertNull(
                "the cache holds five sessions, so the first of six queries must have been evicted",
                MemorySearch.session(hash, SIX_QUERIES.first()),
            )
            val missing = SIX_QUERIES.drop(1).filter { MemorySearch.session(hash, it) == null }
            assertEquals(
                "queries two through six must all still be cached. These were not: $missing",
                emptyList<String>(),
                missing,
            )

            runQuery(hash, SIX_QUERIES[1])
            runQuery(hash, SEVENTH_QUERY)

            assertNotNull(
                "the map is access ordered, so re running query two before query seven must keep query two " +
                    "alive",
                MemorySearch.session(hash, SIX_QUERIES[1]),
            )
            assertNull(
                "query three must be the one evicted by query seven, because query two was touched more " +
                    "recently than it",
                MemorySearch.session(hash, SIX_QUERIES[2]),
            )
            assertNotNull(
                "the newest query must be cached",
                MemorySearch.session(hash, SEVENTH_QUERY),
            )
        }
    }

    @Test
    @TestId("MEM-22")
    fun aSearchResultSetAboveFiveThousandHitsIsNotCached() {
        val fixture = MemoryFixtures.file(MemoryFixtures.THREE_THOUSAND_PAGES)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            val opened = reader.awaitReady()
            assertEquals(
                "the fixture must be the three thousand page document, otherwise no query reaches 5000 hits",
                MemoryFixtures.THREE_THOUSAND_PAGES_COUNT as Int?,
                opened.pageCount,
            )
            val hash = reader.awaitFileHashResolved()
            val beforeSearch = Heap.settledJavaHeap()

            SearchIdle.clear()
            search.search(EVERY_PAGE_TERM)
            val counter = SearchBar.awaitCounterTotalAbove(
                MAX_CACHED_HITS,
                MemorySearch.SEARCH_TIMEOUT_MS,
            )
            SearchIdle.awaitFinished(MemorySearch.SEARCH_TIMEOUT_MS)
            MemorySearch.awaitNotRunning(hash, EVERY_PAGE_TERM)
            MemorySearch.requireFinished(EVERY_PAGE_TERM)

            assertTrue(
                "the search must genuinely have reported more than $MAX_CACHED_HITS hits while it was on " +
                    "screen, the counter read $counter",
                counter.total > MAX_CACHED_HITS,
            )
            assertNull(
                "SearchSessionCache.put drops any result set above $MAX_CACHED_HITS hits rather than " +
                    "retaining it",
                MemorySearch.session(hash, EVERY_PAGE_TERM),
            )

            MemorySearch.dismissSearchBar()
            val afterSearch = Heap.settledJavaHeap()
            assertTrue(
                Heap.describe("the heap after a search matching every page", beforeSearch, afterSearch, HEAP_BAND),
                Heap.withinBand(beforeSearch, afterSearch, HEAP_BAND),
            )
        }
    }

    @Test
    @TestId("MEM-26")
    @TestDuration(DurationCategory.VERY_LONG)
    fun twentySearchesInSequenceDoNotGrowTheHeap() {
        val fixture = MemoryFixtures.file(MemoryFixtures.LARGE)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val hash = reader.awaitFileHashResolved()
            val starting = Heap.settledJavaHeap()

            TWENTY_QUERIES.forEach { query ->
                runQuery(hash, query)
            }

            val settled = Heap.settledJavaHeap()
            assertTrue(
                Heap.describe("the heap after twenty searches", starting, settled, TWENTY_SEARCH_BAND),
                Heap.withinBand(starting, settled, TWENTY_SEARCH_BAND),
            )

            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1)

            val retained = MemorySearch.cachedQueries(hash, TWENTY_QUERIES)
            assertEquals(
                "the session cache holds five sessions, so exactly the last five queries must survive. It " +
                    "retained $retained",
                TWENTY_QUERIES.takeLast(RETAINED_SESSIONS),
                retained,
            )
        }
    }

    @Test
    @TestId("MEM-45")
    @RequiresFixture(FixtureResource.LARGE_PDF)
    fun searchingAFourHundredMegabytePdfStaysBounded() {
        val fixture = MemoryFixtures.generatedOnDevice(MemoryFixtures.HUGE_500MB)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            val hash = reader.awaitFileHashResolved()
            val beforeSearch = collectedJavaHeap()

            val measurements = Collections.synchronizedList(mutableListOf<Pair<Int, Long>>())
            SearchTestHooks.pageScanObserver = { observedHash, query, page ->
                if (observedHash == hash && query == MemoryFixtures.LARGE_SEARCH_TERM) {
                    val raw = Heap.javaHeapInUse()
                    val measured = if (Heap.withinBand(beforeSearch, raw, IN_FLIGHT_BAND)) {
                        raw
                    } else {
                        collectedJavaHeap()
                    }
                    measurements += page to measured
                }
            }
            try {
                SearchIdle.clear()
                search.search(MemoryFixtures.LARGE_SEARCH_TERM)
                SearchBar.awaitShown(Await.LONG_TIMEOUT_MS)
                SearchIdle.awaitFinished(MemorySearch.SEARCH_TIMEOUT_MS)
            } finally {
                SearchTestHooks.pageScanObserver = null
            }
            val captured = synchronized(measurements) { measurements.toList() }
            assertEquals(
                "each scanned page must have an in-flight heap measurement",
                (1..MemoryFixtures.HUGE_500MB_PAGES).toList(),
                captured.map { it.first },
            )
            val samples = captured.map { it.second }
            MemorySearch.awaitNotRunning(hash, MemoryFixtures.LARGE_SEARCH_TERM)
            val progress = MemorySearch.requireFinished(MemoryFixtures.LARGE_SEARCH_TERM)
            assertEquals(
                "the search must have walked all ${MemoryFixtures.HUGE_500MB_PAGES} pages of the fixture. " +
                    "The hit count is not the oracle here: every page of this document is one uncompressed " +
                    "image and it carries no extractable text at all, so the work being measured is the scan " +
                    "rather than the matching",
                MemoryFixtures.HUGE_500MB_PAGES as Int?,
                progress.pagesScanned,
            )

            assertTrue(
                "the search must have been sampled at least once while it was running, otherwise the in " +
                    "flight band asserts nothing",
                samples.isNotEmpty(),
            )
            val overBand = samples.filterNot { Heap.withinBand(beforeSearch, it, IN_FLIGHT_BAND) }
            assertEquals(
                "live heap at every scanned page of $FILE_SIZE_LABEL must stay within " +
                    "${(IN_FLIGHT_BAND * 100).toInt()} percent of the settled pre-search heap " +
                    "(${Heap.megabytes(beforeSearch)} MB). Raw readings above the budget are collected " +
                    "and measured again while the search remains in its page observer. " +
                    "Over-budget readings in bytes: $overBand",
                emptyList<Long>(),
                overBand,
            )

            val settled = collectedJavaHeap()
            assertTrue(
                Heap.describe("the heap after searching $FILE_SIZE_LABEL", beforeSearch, settled, IN_FLIGHT_BAND),
                Heap.withinBand(beforeSearch, settled, IN_FLIGHT_BAND),
            )

            MemoryReader.awaitDescriptorCount(fixture, 1)
            MemoryReader.holdDescriptorCount(fixture, 1)
        }
    }

    private fun collectedJavaHeap(): Long = Heap.settle {
        Runtime.getRuntime().gc()
        Heap.javaHeapInUse()
    }

    private fun runQuery(fileHash: String, query: String): Int {
        MemorySearch.dismissSearchBar()
        val cachedBefore = MemorySearch.session(fileHash, query)
        if (cachedBefore == null) {
            MemorySearch.runToCompletion(search, fileHash, query)
        } else {
            assertTrue(
                "the cached-query UI completion oracle requires a visible result counter, but \"$query\" " +
                    "was cached with no hits",
                cachedBefore.hits.isNotEmpty(),
            )
            SearchIdle.clear()
            search.search(query)
            SearchBar.awaitShown(Await.LONG_TIMEOUT_MS)
            SearchBar.awaitCounterOf(1, cachedBefore.hits.size, Await.LONG_TIMEOUT_MS)
            MemorySearch.awaitNotRunning(fileHash, query)
            assertNull(
                "re-running cached query \"$query\" must complete from its visible ${cachedBefore.hits.size}-hit " +
                    "session without starting a coordinator search",
                SearchIdle.progress(),
            )
        }
        return MemorySearch.session(fileHash, query)?.hits?.size ?: 0
    }

    private companion object {
        const val MAX_CACHED_HITS = 5_000
        const val RETAINED_SESSIONS = 5
        const val HEAP_BAND = 0.30
        const val TWENTY_SEARCH_BAND = 0.30
        const val IN_FLIGHT_BAND = 0.30
        const val EVERY_PAGE_TERM = "page"
        const val SEVENTH_QUERY = "background"
        const val FILE_SIZE_LABEL = "four hundred megabyte"

        val SIX_QUERIES = listOf(
            "negotiation",
            "paragraph",
            "refuses",
            "understanding",
            "rhythm",
            "disappears",
        )

        val TWENTY_QUERIES = listOf(
            "reading",
            "document",
            "quiet",
            "negotiation",
            "between",
            "paragraph",
            "offers",
            "shape",
            "reader",
            "accepts",
            "refuses",
            "dense",
            "slows",
            "opens",
            "moves",
            "ahead",
            "understanding",
            "ordinary",
            "rhythm",
            "background",
        )
    }
}
