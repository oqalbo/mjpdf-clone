// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ListSearchTest : ListsTestCase() {

    @Test
    @TestId("LIST-23")
    fun searchingFiltersByQuotedText() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.openSearch()
            list.typeQuery(ListsFixtures.WORD_IN_ONE_QUOTE_ON_PAGE_SIX.uppercase())
            list.awaitItemCount(1)

            list.scrollTo(0)
            assertEquals("the surviving row's page pill", "Page 6", list.rowPagePill(0))
            Snackbars.awaitText(list.filteredResultsSnackbarText(1))
        }
    }

    @Test
    @TestId("LIST-24")
    fun searchingAlsoMatchesNoteTextOnTheHighlightsList() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            val word = ListsFixtures.WORD_IN_ONE_NOTE_ONLY
            val carrier = ListsFixtures.richGroups.single { it.note?.contains(word, true) == true }
            assertFalse(
                "the word $word must not appear in any quote for this test to mean what it says",
                ListsFixtures.richGroups.any { it.quote.contains(word, true) },
            )

            list.openSearch()
            list.typeQuery(word)
            list.awaitItemCount(1)

            list.scrollTo(0)
            list.tapExpandArrow(0)
            assertTrue("the note is revealed on expand", list.rowSecondaryVisible(0))
            assertTrue(
                "the revealed note does not carry the typed word, it reads \"${list.rowSecondaryText(0)}\"",
                list.rowSecondaryText(0).contains(word, true),
            )
            assertEquals("the surviving group", listOf(carrier.key), list.groupKeys())
        }
    }

    @Test
    @TestId("LIST-25")
    @TestDuration(DurationCategory.NORMAL)
    fun closingTheSearchRestoresTheFullList() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.openSearch()
            list.typeQuery(ListsFixtures.WORD_IN_ONE_QUOTE_ON_PAGE_SIX)
            list.awaitItemCount(1)

            list.closeSearch()
            list.awaitItemCount(ListsFixtures.richGroups.size)
            assertFalse("the search field is still expanded in the action bar", list.isSearchExpanded())
        }
    }
}
