// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchCountingTest : SearchTestCase() {

    @Test
    @TestId("SRCH-01")
    @TestDuration(DurationCategory.SHORT)
    fun plainSearchReportsEveryMatch() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.BEACON)

            SearchBar.awaitShown()
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)

            assertEquals("the snackbar label", SearchBar.resultsLabel(), SearchBar.label())
            assertEquals("the snackbar action", SearchBar.doneLabel(), SearchBar.actionText())
            assertEquals("previous arrow visibility", View.VISIBLE as Int?, SearchBar.previousVisibility())
            assertEquals("next arrow visibility", View.VISIBLE as Int?, SearchBar.nextVisibility())
            assertEquals("previous arrow enabled", true as Boolean?, SearchBar.previousEnabled())
            assertEquals("next arrow enabled", true as Boolean?, SearchBar.nextEnabled())

            val hits = SearchOracle.awaitHits(hash, SearchFixtures.BEACON)
            assertEquals("cached hit count", SearchFixtures.BEACON_HITS, hits.size)
            assertEquals(
                "cached hit page numbers in resultIndex order",
                SearchFixtures.BEACON_PAGE_NUMBERS,
                SearchOracle.pageNumbersOf(hits),
            )
        }
    }

    @Test
    @TestId("SRCH-02")
    @TestDuration(DurationCategory.SHORT)
    fun aQueryWithNoMatchesSaysSoPlainly() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.ZEPHYRUS)

            SearchBar.awaitNoResultsLabel()

            assertFalse("the no results label must not be clickable", SearchBar.isLabelClickable())
            assertTrue("the counter and both arrows must be gone", SearchBar.navigationGone())
            assertEquals("the reader must not have jumped", 0, pageIndex())

            val session = SearchOracle.awaitSession(hash, SearchFixtures.ZEPHYRUS)
            assertEquals("cached hit count for a query with no matches", 0, session.hits.size)
        }
    }

    @Test
    @TestId("SRCH-03")
    fun searchingAScannedDocumentGivesTheEmptyAnswer() {
        openReader(SearchFixtures.SCANNED) {
            val before = pageIndex()
            robot.search(SearchFixtures.BEACON)

            SearchBar.awaitNoResultsLabel()

            assertTrue("the counter and both arrows must be gone", SearchBar.navigationGone())
            assertEquals("the reader must not have jumped", before, pageIndex())
            assertLabelNeverFailsToHighlight()
        }
    }

    @Test
    @TestId("SRCH-04")
    fun aSingleResultOffersNoNavigation() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.KALLIOPE)

            SearchBar.awaitCounterOf(1, 1)
            SearchOracle.awaitHits(hash, SearchFixtures.KALLIOPE)
            awaitSettledOnPage(SearchFixtures.KALLIOPE_PAGE_INDEX)

            assertEquals("previous arrow visibility", View.VISIBLE as Int?, SearchBar.previousVisibility())
            assertEquals("next arrow visibility", View.VISIBLE as Int?, SearchBar.nextVisibility())
            assertEquals("previous arrow enabled", false as Boolean?, SearchBar.previousEnabled())
            assertEquals("next arrow enabled", false as Boolean?, SearchBar.nextEnabled())
            assertEquals("previous arrow alpha", SearchBar.DISABLED_ALPHA, SearchBar.previousAlpha()!!, ALPHA_TOLERANCE)
            assertEquals("next arrow alpha", SearchBar.DISABLED_ALPHA, SearchBar.nextAlpha()!!, ALPHA_TOLERANCE)

            SearchBar.tapNext()
            SearchBar.tapPrevious()

            Await.assertThroughout(
                condition = "the counter stays 1/1 and the reader stays on the single hit after both taps",
                windowMs = STABLE_WINDOW_MS,
            ) {
                SearchBar.counterText() == "1/1" && state().page == SearchFixtures.KALLIOPE_PAGE_INDEX
            }
        }
    }

    @Test
    @TestId("SRCH-28")
    fun matchingIsCaseInsensitive() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.CHAPTER)

            SearchBar.awaitCounterOf(1, SearchFixtures.CHAPTER_HITS)
            val lowercase = SearchOracle.awaitHits(hash, SearchFixtures.CHAPTER)
            assertEquals(
                "page numbers for the lowercase query",
                SearchFixtures.CHAPTER_PAGE_NUMBERS,
                SearchOracle.pageNumbersOf(lowercase),
            )

            robot.search(SearchFixtures.CHAPTER_UPPERCASE)
            SearchBar.awaitCounterOf(1, SearchFixtures.CHAPTER_HITS)
            val uppercase = SearchOracle.awaitHits(hash, SearchFixtures.CHAPTER_UPPERCASE)
            assertEquals(
                "page numbers for the uppercase query",
                SearchFixtures.CHAPTER_PAGE_NUMBERS,
                SearchOracle.pageNumbersOf(uppercase),
            )
            assertEquals(
                "the two casings must find the same character offsets",
                lowercase.map { it.originalIndex },
                uppercase.map { it.originalIndex },
            )
        }
    }

    @Test
    @TestId("SRCH-37")
    fun hyphenWrappedWordsAreJoinedForSearch() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.HYPHENATION)

            SearchBar.awaitCounterOf(1, SearchFixtures.HYPHENATION_HITS)
            val joined = SearchOracle.awaitHits(hash, SearchFixtures.HYPHENATION)
            assertEquals(
                "hits for the intact spelling. ${SearchFixtures.HYPHENATION_GROUND_TRUTH}",
                SearchFixtures.HYPHENATION_HITS,
                joined.size,
            )
            assertEquals(
                "the split page is reached as well as the page that carries the word whole",
                SearchFixtures.HYPHENATION_PAGE_NUMBERS,
                SearchOracle.pageNumbersOf(joined),
            )

            robot.search(SearchFixtures.HYPHEN)
            val fragments = SearchOracle.awaitHits(hash, SearchFixtures.HYPHEN)
            assertEquals("hits for the fragment query", 2, fragments.size)
            assertEquals(
                "the fragment query reaches the split page as well as the intact one",
                SearchFixtures.HYPHEN_PAGE_NUMBERS,
                SearchOracle.pageNumbersOf(fragments),
            )
        }
    }

    @Test
    @TestId("SRCH-38")
    fun aPhraseBrokenByALineBreakDoesNotMatch() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(SearchFixtures.QUICK_BROWN)

            SearchBar.awaitCounterOf(1, 1)
            val phrase = SearchOracle.awaitHits(hash, SearchFixtures.QUICK_BROWN)
            assertEquals("hits for the two word phrase", 1, phrase.size)
            assertEquals(
                "the only phrase match is the one that sits on a single line",
                SearchFixtures.QUICK_BROWN_PAGE_NUMBER,
                phrase.first().pageNumber,
            )

            robot.search(SearchFixtures.QUICK)
            val single = SearchOracle.awaitHits(hash, SearchFixtures.QUICK)
            assertEquals(
                "the first word alone is present on both pages",
                SearchFixtures.QUICK_PAGE_NUMBERS,
                SearchOracle.pageNumbersOf(single),
            )
        }
    }

    @Test
    @TestId("SRCH-39")
    @TestDuration(DurationCategory.LONG)
    fun regexSpecialCharactersInTheQueryAreLiteral() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            val since = SearchLogcat.since()

            SearchFixtures.LITERAL_SPECIMENS.forEach { specimen ->
                robot.search(specimen)
                SearchBar.awaitCounterOf(1, 1)
                val hits = SearchOracle.awaitHits(hash, specimen)
                assertEquals("hits for the literal query \"$specimen\"", 1, hits.size)
                assertEquals(
                    "the literal query \"$specimen\" matches on the specimen page",
                    SearchFixtures.LITERAL_PAGE_NUMBER,
                    hits.first().pageNumber,
                )
            }

            robot.searchAndProceedAnyway(SearchFixtures.PLAIN_AB)
            val plain = SearchOracle.awaitHits(hash, SearchFixtures.PLAIN_AB)
            val dotted = SearchOracle.hits(hash, SearchFixtures.LITERAL_SPECIMENS.first())
            assertEquals(
                "the plain word and the dotted specimen must be different occurrences",
                emptyList<Int>(),
                plain.map { it.originalIndex }.intersect(dotted.map { it.originalIndex }.toSet()).toList(),
            )

            SearchLogcat.assertAbsentSince(since, SearchLogcat.PATTERN_SYNTAX)
        }
    }

    private fun assertLabelNeverFailsToHighlight(windowMs: Long = FAILURE_WINDOW_MS) {
        val failure = SearchBar.failedToHighlightLabel()
        Await.assertThroughout(
            condition = "the snackbar label never reads \"$failure\"",
            windowMs = windowMs,
        ) { SearchBar.labelOrNull() != failure }
    }

    companion object {
        const val ALPHA_TOLERANCE = 0.001f
        const val FAILURE_WINDOW_MS = 1_500L
        const val STABLE_WINDOW_MS = 1_000L
    }
}
