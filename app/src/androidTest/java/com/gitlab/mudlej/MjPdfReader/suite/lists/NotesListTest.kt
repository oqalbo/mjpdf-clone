// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class NotesListTest : ListsTestCase() {

    @Test
    @TestId("LIST-28")
    fun theNotesListShowsOnlyHighlightsThatCarryANote() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openNotesAndScan()

            val noted = ListsFixtures.richNotedGroups
            assertEquals("note rows", noted.size, list.itemCount())
            assertEquals("the groups listed", noted.map { it.key }, list.groupKeys())

            list.allPrimaryTexts().forEachIndexed { position, text ->
                assertTrue("row $position leads with a blank note", text.isNotBlank())
            }
            assertEquals(
                "every listed row carries a note",
                emptyList<String>(),
                list.items().filter { it.note.isBlank() }.map { it.groupKey },
            )
        }
    }

    @Test
    @TestId("LIST-29")
    fun noteRowsLeadWithTheNoteAndRevealTheQuoteOnExpand() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openNotesAndScan()
            val first = ListsFixtures.richNotedGroups.first()

            list.scrollTo(0)
            assertEquals("the primary text is the note, unquoted", first.note, list.rowPrimaryText(0))
            assertFalse("the quote is shown before expanding", list.rowSecondaryVisible(0))

            list.tapExpandArrow(0)
            assertTrue("the quote is revealed on expand", list.rowSecondaryVisible(0))
            assertEquals("the revealed quote", first.rowQuote, list.rowSecondaryText(0))
            assertTrue(
                "the revealed quote is set in an italic typeface",
                list.rowSecondaryIsItalic(0),
            )
        }
    }

    @Test
    @TestId("LIST-30")
    @TestDuration(DurationCategory.NORMAL)
    fun theNotesEmptyStateAppearsOnADocumentWithHighlightsButNoNotes() {
        openReader(ListsFixtures.PLAIN) { _, _ ->
            val list = openNotesAndScan()

            assertTrue("the empty state message is shown", list.messageVisible())
            assertEquals("the empty state text", list.string(R.string.no_user_notes), list.messageText())
            assertEquals("adapter item count", 0, list.itemCount())
            assertEquals("the overflow menu items", emptyList<String>(), list.overflowItemLabels())
            assertFalse("the search icon is offered", list.isSearchIconShown())
            assertFalse(
                "the notes layout carries a colour filter row, which the highlights layout owns and this one does not",
                list.colorFilterRowExists(),
            )
        }
    }
}
