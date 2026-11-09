// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
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
class RowDetailTest : ListsTestCase() {

    @Test
    @TestId("LIST-09")
    fun theChapterPillShowsTheResolvedTableOfContentsPath() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            (0 until list.itemCount()).forEach { position ->
                list.scrollTo(position)
                assertFalse(
                    "row $position shows a chapter pill while it is collapsed",
                    list.rowLocationVisible(position),
                )
            }

            val pageNine = list.positionOfPagePill("Page 9")
            list.scrollTo(pageNine)
            list.tapExpandArrow(pageNine)
            assertTrue("the chapter pill of the page 9 row is shown", list.rowLocationVisible(pageNine))
            assertEquals(
                "the chapter pill of the page 9 row",
                ListsFixtures.PART_TWO_COLLECTION,
                list.rowLocationPill(pageNine),
            )

            val pageSix = list.positionOfPagePill("Page 6")
            list.scrollTo(pageSix)
            list.tapExpandArrow(pageSix)
            assertTrue("the chapter pill of the page 6 row is shown", list.rowLocationVisible(pageSix))
            assertEquals(
                "the chapter pill of the page 6 row",
                ListsFixtures.PART_ONE_ALLOCATION,
                list.rowLocationPill(pageSix),
            )
        }
    }

    @Test
    @TestId("LIST-10")
    fun aDocumentWithNoOutlineShowsNoChapterPill() {
        openReader(ListsFixtures.PLAIN) { _, _ ->
            val list = openHighlightsAndScan()

            assertEquals("adapter item count", 2, list.itemCount())
            (0 until list.itemCount()).forEach { position ->
                list.scrollTo(position)
                assertEquals(
                    "row $position offers an expand arrow on a document with no outline, no notes and no dates. " +
                        "UserNoteAdapter.bind leaves the arrow INVISIBLE when there is nothing to reveal, and an " +
                        "INVISIBLE arrow takes no touch, so a tap on it falls through to the row and opens the " +
                        "reader. If the arrow ever becomes VISIBLE here the row gained something to show and this " +
                        "test must expand it and re-check the chapter pill",
                    View.INVISIBLE,
                    list.rowExpandArrowVisibility(position),
                )
                assertFalse(
                    "row $position shows a chapter pill on a document with no outline",
                    list.rowLocationVisible(position),
                )
            }
        }
    }

    @Test
    @TestId("LIST-11")
    @TestDuration(DurationCategory.NORMAL)
    fun theCreationDateShowsOnExpandAndSortIsOfferedOnlyWhenDatesExist() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            list.scrollTo(0)
            list.tapExpandArrow(0)

            assertTrue("the date of the first row is shown when expanded", list.rowDateVisible(0))
            val shown = list.rowDate(0)
            assertTrue(
                "the row date \"$shown\" does not read as yyyy-MM-dd HH:mm",
                DATE_PATTERN.matches(shown),
            )
            assertEquals(
                "the row date of the first group",
                ListsFixtures.richGroups.first().rowDate,
                shown,
            )
            assertTrue(
                "sort by date is offered on a document whose highlights carry dates",
                list.isOverflowItemShown(R.string.sort_by_creation_date),
            )
        }

        openReader(ListsFixtures.PLAIN) { _, _ ->
            val list = openHighlightsAndScan()
            list.scrollTo(0)
            assertEquals(
                "the first row offers an expand arrow on an undated document with no outline and no notes. The " +
                    "arrow is INVISIBLE when there is nothing to reveal, and tapping an INVISIBLE arrow falls " +
                    "through to the row and opens the reader",
                View.INVISIBLE,
                list.rowExpandArrowVisibility(0),
            )

            assertFalse("the date of the first row is shown on an undated document", list.rowDateVisible(0))
            assertFalse(
                "sort by date is offered on a document whose highlights carry no dates",
                list.isOverflowItemShown(R.string.sort_by_creation_date),
            )
        }
    }

    private companion object {
        val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")
    }
}
