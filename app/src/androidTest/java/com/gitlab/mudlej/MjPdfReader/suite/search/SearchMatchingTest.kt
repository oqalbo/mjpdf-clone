// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchMatchingTest : SearchTestCase() {

    @Test
    @TestId("SRCH-29")
    fun diacriticsAreSignificantByDefault() {
        openReader(SearchFixtures.ACCENTS) {
            val hash = fileHash()
            robot.search(SearchFixtures.CAFE_PLAIN)

            SearchBar.awaitCounterOf(1, 1)
            val plain = SearchOracle.awaitHits(hash, SearchFixtures.CAFE_PLAIN)
            assertEquals("hits for the unaccented spelling", 1, plain.size)
            assertEquals(
                "the unaccented spelling matches on its own page",
                SearchFixtures.CAFE_PAGE_NUMBER,
                plain.first().pageNumber,
            )

            robot.search(SearchFixtures.CAFE_ACCENTED)
            SearchBar.awaitCounterOf(1, 1)
            val accented = SearchOracle.awaitHits(hash, SearchFixtures.CAFE_ACCENTED)
            assertEquals("hits for the accented spelling", 1, accented.size)
            assertEquals(
                "the accented spelling matches on the same page",
                SearchFixtures.CAFE_PAGE_NUMBER,
                accented.first().pageNumber,
            )
            assertTrue(
                "the two spellings must be different occurrences, both report originalIndex " +
                    "${plain.first().originalIndex}",
                plain.first().originalIndex != accented.first().originalIndex,
            )
        }
    }

    @Test
    @TestId("SRCH-30")
    fun ignoreAccentsMatchesAcrossDiacritics() {
        seedIgnoreAccents(true)
        openReader(SearchFixtures.ACCENTS) {
            val hash = fileHash()

            robot.search(SearchFixtures.CAFE_PLAIN)
            SearchBar.awaitCounterOf(1, 2)
            val cafe = SearchOracle.awaitHits(hash, SearchFixtures.CAFE_PLAIN, ignoreAccents = true)
            assertEquals("accent insensitive hits for the cafe query", 2, cafe.size)
            assertEquals(
                "both cafe spellings sit on the same page",
                listOf(SearchFixtures.CAFE_PAGE_NUMBER, SearchFixtures.CAFE_PAGE_NUMBER),
                SearchOracle.pageNumbersOf(cafe),
            )

            robot.search(SearchFixtures.CESTINA_PLAIN)
            val cestina = SearchOracle.awaitHits(hash, SearchFixtures.CESTINA_PLAIN, ignoreAccents = true)
            assertEquals("accent insensitive hits for the cestina query", 1, cestina.size)
            assertEquals(
                "the cestina occurrence sits on the second page",
                SearchFixtures.CESTINA_PAGE_NUMBER,
                cestina.first().pageNumber,
            )

            robot.search(SearchFixtures.ZURICH_PLAIN)
            val zurich = SearchOracle.awaitHits(hash, SearchFixtures.ZURICH_PLAIN, ignoreAccents = true)
            assertEquals("accent insensitive hits for the zurich query", 2, zurich.size)
            assertEquals(
                "both zurich spellings sit on the second page",
                listOf(SearchFixtures.ZURICH_PAGE_NUMBER, SearchFixtures.ZURICH_PAGE_NUMBER),
                SearchOracle.pageNumbersOf(zurich),
            )

            assertNull(
                "the accent insensitive sessions must not be reachable under the accent sensitive key",
                SearchOracle.session(hash, SearchFixtures.CAFE_PLAIN, ignoreAccents = false),
            )
        }
    }

    @Test
    @TestId("SRCH-31")
    fun theDialogCheckboxWritesThePreferenceImmediately() {
        openReader(SearchFixtures.TEXT) {
            robot.openSearchDialog()
            assertEquals(
                "the checkbox must start unchecked at the default",
                false,
                robot.ignoreAccentsChecked(),
            )

            robot.toggleIgnoreAccents()

            assertEquals(
                "the preference must be written on the toggle, before any search runs",
                true,
                preferenceBoolean(Preferences.searchIgnoreAccentsKey),
            )

            robot.cancel()
            robot.openSearchDialog()

            assertEquals(
                "the reopened dialog must show the remembered choice",
                true,
                robot.ignoreAccentsChecked(),
            )
            assertNull(
                "cancelling must not have run a search",
                SearchOracle.session(fileHash(), SearchFixtures.BEACON, ignoreAccents = true),
            )
        }
    }

    @Test
    @TestId("SRCH-32")
    fun ligatureWordsAreFound() {
        openReader(SearchFixtures.LIGATURES) {
            val hash = fileHash()
            robot.search(SearchFixtures.FFI_LIGATURE_WORD)

            SearchBar.awaitCounterOf(1, 1)
            reader.awaitFullQualityRender()
            val ffi = SearchOracle.awaitHits(hash, SearchFixtures.FFI_LIGATURE_WORD)
            assertEquals(
                "the ffi ligature word must match once and the plain spelling word on the same page must not",
                1,
                ffi.size,
            )
            assertEquals("the snackbar label", SearchBar.resultsLabel(), SearchBar.label())

            robot.search(SearchFixtures.FI_LIGATURE_WORD)
            SearchBar.awaitCounterOf(1, 1)
            val fi = SearchOracle.awaitHits(hash, SearchFixtures.FI_LIGATURE_WORD)
            assertEquals("the fi ligature word must match once", 1, fi.size)
            assertEquals("the snackbar label", SearchBar.resultsLabel(), SearchBar.label())
        }
    }

    @Test
    @TestId("SRCH-40")
    fun unicodeDigitsMatchTheDigitsTheUserTyped() {
        openReader(SearchFixtures.DIGITS) {
            val hash = fileHash()

            robot.search(SearchFixtures.ASCII_DIGITS)
            SearchBar.awaitCounterOf(1, 2)
            val ascii = SearchOracle.awaitHits(hash, SearchFixtures.ASCII_DIGITS)
            assertEquals(
                "the ASCII query with the setting off. ${SearchFixtures.DIGITS_GROUND_TRUTH}",
                2,
                ascii.size,
            )

            robot.search(SearchFixtures.ARABIC_INDIC_DIGITS)
            SearchBar.awaitCounterOf(1, 1)
            val arabicIndic = SearchOracle.awaitHits(hash, SearchFixtures.ARABIC_INDIC_DIGITS)
            assertEquals(
                "the Arabic-Indic query with the setting off, NFKC leaves Arabic-Indic digits alone",
                1,
                arabicIndic.size,
            )
            assertTrue(
                "the Arabic-Indic query must land on its own occurrence, not on either folded ASCII one",
                ascii.none { it.originalIndex == arabicIndic.first().originalIndex },
            )

            robot.search(SearchFixtures.FULLWIDTH_DIGITS)
            SearchBar.awaitNoResultsLabel()
            val fullwidth = SearchOracle.awaitHits(hash, SearchFixtures.FULLWIDTH_DIGITS)
            assertEquals(
                "the fullwidth query with the setting off finds nothing, because the page text is folded to " +
                    "ASCII while the query the user typed is not folded at all",
                0,
                fullwidth.size,
            )
        }

        seedIgnoreAccents(true)
        openReader(SearchFixtures.DIGITS) {
            val hash = fileHash()

            robot.search(SearchFixtures.ASCII_DIGITS)
            SearchBar.awaitCounterOf(1, 2)
            val folded = SearchOracle.awaitHits(hash, SearchFixtures.ASCII_DIGITS, ignoreAccents = true)
            assertEquals(
                "with the setting on the ASCII query still reaches the ASCII and the fullwidth occurrence, the " +
                    "same two the folded page text already gave it with the setting off",
                2,
                folded.size,
            )

            robot.search(SearchFixtures.ARABIC_INDIC_DIGITS)
            val arabicIndic = SearchOracle.awaitHits(hash, SearchFixtures.ARABIC_INDIC_DIGITS, ignoreAccents = true)
            assertEquals(
                "the Arabic-Indic digits are a separate script and must stay a separate single match",
                1,
                arabicIndic.size,
            )
            assertTrue(
                "the ASCII query must never reach the Arabic-Indic occurrence",
                folded.none { it.originalIndex == arabicIndic.first().originalIndex },
            )
        }
    }

    @Test
    @TestId("SRCH-45")
    @TestDuration(DurationCategory.SHORT)
    fun surroundingWhitespaceInAQueryIsTrimmed() {
        openReader(SearchFixtures.TEXT) {
            val hash = fileHash()
            robot.search(PADDED_BEACON)

            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)

            val trimmed = SearchOracle.awaitHits(hash, SearchFixtures.BEACON)
            assertEquals("the session is stored under the trimmed key", SearchFixtures.BEACON_HITS, trimmed.size)
            val padded = SearchOracle.session(hash, PADDED_BEACON)
            assertEquals(
                "looking the session up with the padded query must reach the same session",
                SearchOracle.session(hash, SearchFixtures.BEACON),
                padded,
            )
        }
    }

    companion object {
        const val PADDED_BEACON = "  beacon  "
    }
}
