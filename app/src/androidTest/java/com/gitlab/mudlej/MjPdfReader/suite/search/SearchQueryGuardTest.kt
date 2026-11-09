// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchQueryGuardTest : SearchTestCase() {

    @Test
    @TestId("SRCH-41")
    fun theTooShortQueryWarningReturnsToTheDialog() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.openSearchDialog()
            robot.typeQuery(SearchFixtures.SHORT_QUERY)
            robot.submit()

            robot.awaitTooShortQueryDialog()
            assertTrue(
                "the warning must quote the rejected query",
                robot.tooShortQueryMessageContains("'${SearchFixtures.SHORT_QUERY}'"),
            )

            robot.tapTooShortOk()

            robot.awaitSearchDialog()
            assertEquals("the reopened dialog must offer an empty field", "", robot.queryFieldText())
            assertTrue("no results snackbar may appear", !SearchBar.isShown())
            assertNull(
                "no search may have run",
                SearchOracle.session(hash, SearchFixtures.SHORT_QUERY),
            )
        }
    }

    @Test
    @TestId("SRCH-42")
    fun proceedAnywayRunsTheShortQuery() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.searchAndProceedAnyway(SearchFixtures.SHORT_QUERY)

            SearchBar.awaitShown()
            val counter = SearchBar.awaitCounter()
            val hits = SearchOracle.awaitHits(hash, SearchFixtures.SHORT_QUERY)

            assertTrue(
                "a two letter prefix of the counting word must find more than the ${SearchFixtures.BEACON_HITS} " +
                    "whole word hits, the counter reports ${counter.total}",
                counter.total > SearchFixtures.BEACON_HITS,
            )
            val settled = SearchBar.awaitCounterStable()
            assertEquals("the cached session must hold the counted hits", settled.total, hits.size)
        }
    }

    @Test
    @TestId("SRCH-43")
    @TestDuration(DurationCategory.LONG)
    fun blankQueriesCannotPutTheAppIntoAnUnusableState() {
        seedIgnoreAccents(true)
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            val since = SearchLogcat.since()

            runBlankQuery("")
            SearchBar.awaitNoResultsLabel(BLANK_SETTLE_TIMEOUT_MS)
            assertNull("a blank key may never reach the session cache", SearchOracle.session(hash, ""))

            runBlankQuery(THREE_SPACES)
            SearchBar.awaitNoResultsLabel(BLANK_SETTLE_TIMEOUT_MS)
            assertNull(
                "a whitespace only key may never reach the session cache",
                SearchOracle.session(hash, THREE_SPACES),
            )
            SearchLogcat.assertAbsentSince(since, SearchLogcat.OUT_OF_MEMORY)
        }

        seedIgnoreAccents(false)
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            val since = SearchLogcat.since()

            runBlankQuery("")
            SearchIdle.awaitFinished(BLANK_SETTLE_TIMEOUT_MS)
            assertNull("a blank key may never reach the session cache", SearchOracle.session(hash, ""))
            awaitReaderInteractive()

            runBlankQuery(THREE_SPACES)
            SearchIdle.awaitFinished(BLANK_SETTLE_TIMEOUT_MS)
            assertNull(
                "a whitespace only key may never reach the session cache",
                SearchOracle.session(hash, THREE_SPACES),
            )
            awaitReaderInteractive()
            SearchLogcat.assertAbsentSince(since, SearchLogcat.OUT_OF_MEMORY)
        }
    }

    @Test
    @TestId("SRCH-44")
    fun anAbsurdlyLongQueryFailsCleanlyAndQuickly() {
        openReader(SearchFixtures.TEXT) {
            robot.openSearchDialog()
            robot.typeQuery(SearchFixtures.longQuery())
            robot.submit()
            robot.awaitSearchDialogGone()

            SearchBar.awaitNoResultsLabel(LONG_QUERY_TIMEOUT_MS)
            awaitReaderInteractive()
        }
    }

    private fun runBlankQuery(query: String) {
        robot.openSearchDialog()
        if (query.isNotEmpty()) robot.typeQuery(query)
        robot.submit()
        robot.awaitTooShortQueryDialog()
        robot.tapProceedAnyway()
        robot.awaitSearchDialogGone()
        SearchBar.awaitShown()
    }

    companion object {
        const val THREE_SPACES = "   "
        const val BLANK_SETTLE_TIMEOUT_MS = 30_000L
        const val LONG_QUERY_TIMEOUT_MS = 15_000L
    }
}
