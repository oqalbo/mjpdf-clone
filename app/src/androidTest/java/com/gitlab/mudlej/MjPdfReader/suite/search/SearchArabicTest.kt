// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchArabicTest : SearchTestCase() {

    @Test
    @TestId("SRCH-33")
    fun arabicSearchFindsEveryOccurrence() {
        openReader(SearchFixtures.ARABIC) {
            val hash = fileHash()
            robot.search(SearchFixtures.KITAB)

            SearchBar.awaitCounter()
            val hits = SearchOracle.awaitHits(hash, SearchFixtures.KITAB)

            assertEquals(
                "the cached hit count for ${SearchFixtures.KITAB}. ${SearchFixtures.KITAB_GROUND_TRUTH}. " +
                    "The hits landed on page indexes ${SearchOracle.pageIndexesOf(hits)}",
                SearchFixtures.KITAB_HITS,
                hits.size,
            )
            assertTrue(
                "the hits must span at least 2 distinct pages, they landed on " +
                    "${SearchOracle.pageIndexesOf(hits)}",
                SearchOracle.pageNumbersOf(hits).distinct().size >= 2,
            )

            val failure = SearchBar.failedToHighlightLabel()
            repeat(hits.size - 1) { step ->
                SearchBar.tapNext()
                SearchBar.awaitCounterOf(step + 2, hits.size)
                awaitSettledOnPage(hits[step + 1].pageNumber - 1)
                assertEquals(
                    "stepping to hit ${step + 2} must not report a highlight failure",
                    SearchBar.resultsLabel(),
                    SearchBar.labelOrNull(),
                )
            }
            assertTrue("the label must never read \"$failure\"", SearchBar.labelOrNull() != failure)
        }
    }

    @Test
    @TestId("SRCH-34")
    @TestDuration(DurationCategory.SHORT)
    fun anArabicHitPastALigatureReachesTheRightPage() {
        openReader(SearchFixtures.ARABIC) {
            val hash = fileHash()
            robot.search(SearchFixtures.KITAB)

            SearchBar.awaitCounter()
            val hits = SearchOracle.awaitHits(hash, SearchFixtures.KITAB)
            val target = hits.indexOfFirst {
                it.pageNumber - 1 == SearchFixtures.ARABIC_SHAPING_PAGE_INDEX
            }
            assertTrue(
                "the shaping page index ${SearchFixtures.ARABIC_SHAPING_PAGE_INDEX} must carry a hit, the hits " +
                    "landed on ${SearchOracle.pageIndexesOf(hits)}",
                target >= 0,
            )

            stepTo(target, hits)

            assertEquals(
                "the reader must land on the page carrying the lam alef forms",
                SearchFixtures.ARABIC_SHAPING_PAGE_INDEX,
                pageIndex(),
            )
            assertEquals("the snackbar label", SearchBar.resultsLabel(), SearchBar.label())
        }
    }

    @Test
    @TestId("SRCH-35")
    fun harakatAreIgnoredWhenTheAccentSettingIsOn() {
        val accentSensitive = hitsWithAccentSetting(false)
        seedIgnoreAccents(true)
        val accentInsensitive = hitsWithAccentSetting(true)

        val page = SearchPageText.of(
            fixture(SearchFixtures.ARABIC),
            SearchFixtures.ARABIC_SHAPING_PAGE_INDEX + 1,
        )
        assertTrue(
            "page index ${SearchFixtures.ARABIC_SHAPING_PAGE_INDEX} of ${SearchFixtures.ARABIC} must carry a " +
                "vocalised word for this test to mean anything",
            page.any { SearchPageText.isHarakat(it) },
        )

        val reachedVocalised = hitsWhoseMatchCarries(accentInsensitive, page) { SearchPageText.isHarakat(it) }
        val reachedWithAccentsOn = hitsWhoseMatchCarries(accentSensitive, page) { SearchPageText.isHarakat(it) }

        assertTrue(
            "the accent insensitive search must reach the vocalised occurrence, meaning a match whose own " +
                "character range spans a harakat, its hits on that page sit at " +
                "${onShapingPage(accentInsensitive).map { it.originalIndex }}",
            reachedVocalised.isNotEmpty(),
        )
        assertEquals(
            "the accent sensitive search must not reach the vocalised occurrence, since a literal match of an " +
                "unvocalised query can never span a harakat",
            emptyList<Int>(),
            reachedWithAccentsOn.map { it.originalIndex },
        )
    }

    @Test
    @TestId("SRCH-36")
    fun tatweelIsIgnoredWhenTheAccentSettingIsOn() {
        val accentSensitive = hitsWithAccentSetting(false)
        seedIgnoreAccents(true)
        val accentInsensitive = hitsWithAccentSetting(true)

        val page = SearchPageText.of(
            fixture(SearchFixtures.ARABIC),
            SearchFixtures.ARABIC_SHAPING_PAGE_INDEX + 1,
        )
        val stretchedRanges = SearchPageText.rangesOfWordsCarrying(page) { it == SearchPageText.TATWEEL }
        assertTrue(
            "page index ${SearchFixtures.ARABIC_SHAPING_PAGE_INDEX} of ${SearchFixtures.ARABIC} must carry a " +
                "tatweel stretched word for this test to mean anything",
            stretchedRanges.isNotEmpty(),
        )

        val reachedStretched = accentInsensitive.filter { hit ->
            hit.pageNumber - 1 == SearchFixtures.ARABIC_SHAPING_PAGE_INDEX &&
                stretchedRanges.any { hit.originalIndex in it }
        }
        val reachedWithAccentsOff = accentSensitive.filter { hit ->
            hit.pageNumber - 1 == SearchFixtures.ARABIC_SHAPING_PAGE_INDEX &&
                stretchedRanges.any { hit.originalIndex in it }
        }

        assertTrue(
            "the accent insensitive search must reach the tatweel stretched occurrence, the stretched words " +
                "occupy $stretchedRanges",
            reachedStretched.isNotEmpty(),
        )
        assertEquals(
            "the accent sensitive search must not reach the tatweel stretched occurrence",
            emptyList<Int>(),
            reachedWithAccentsOff.map { it.originalIndex },
        )
    }

    private fun onShapingPage(hits: List<SearchSessionCache.Hit>): List<SearchSessionCache.Hit> =
        hits.filter { it.pageNumber - 1 == SearchFixtures.ARABIC_SHAPING_PAGE_INDEX }

    private fun hitsWhoseMatchCarries(
        hits: List<SearchSessionCache.Hit>,
        page: String,
        predicate: (Char) -> Boolean,
    ): List<SearchSessionCache.Hit> = onShapingPage(hits).filter { hit ->
        SearchPageText.rangeCarries(
            page,
            SearchPageText.matchedRange(hit.originalIndex, hit.inputEnd - hit.inputStart),
            predicate,
        )
    }

    private fun hitsWithAccentSetting(ignoreAccents: Boolean): List<SearchSessionCache.Hit> {
        var captured: List<SearchSessionCache.Hit> = emptyList()
        openReader(SearchFixtures.ARABIC) {
            val hash = fileHash()
            robot.search(SearchFixtures.KITAB)
            SearchBar.awaitShown()
            captured = SearchOracle.awaitHits(hash, SearchFixtures.KITAB, ignoreAccents)
        }
        return captured
    }

    private fun stepTo(target: Int, hits: List<SearchSessionCache.Hit>) {
        var current = SearchBar.requireCounter().position - 1
        while (current != target) {
            SearchBar.tapNext()
            current = (current + 1) % hits.size
            SearchBar.awaitCounterOf(current + 1, hits.size)
            awaitSettledOnPage(hits[current].pageNumber - 1)
        }
    }

}
