// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightsListContentTest : ListsTestCase() {

    @Test
    @TestId("LIST-01")
    fun theListHoldsOneRowPerHighlightGroup() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            assertEquals("highlight rows", ListsFixtures.richGroups.size, list.itemCount())
            assertEquals(
                "the raw annotation count would have been ${ListsFixtures.RICH_ANNOTATIONS}",
                ListsFixtures.richGroups.map { it.key },
                list.groupKeys(),
            )
            assertFalse("the empty state message is shown", list.messageVisible())

            val firstQuoteOfPageZero = ListsFixtures.richGroups.first().rowQuote
            assertEquals(
                "rows whose primary text is the first quote of page index 0",
                1,
                list.allPrimaryTexts().count { it == firstQuoteOfPageZero },
            )
        }
    }

    @Test
    @TestId("LIST-02")
    fun rowsAreOrderedByPageAscending() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            assertEquals("the page pill of every row in order", ListsFixtures.richPagePills, list.allPagePills())
            val pageIndexes = list.pageIndexes()
            assertEquals("page indexes never decrease", pageIndexes.sorted(), pageIndexes)
        }
    }

    @Test
    @TestId("LIST-03")
    fun aMultiLineHighlightIsDeduplicatedByGroupKey() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            ListsFixtures.richMultiLineGroups.forEach { group ->
                assertEquals(
                    "rows carrying the three line group ${group.key}",
                    1,
                    list.groupKeys().count { it == group.key },
                )
            }

            val threeLineGroupOnPageThree = ListsFixtures.richGroups.single { it.key == "mj-group-03" }
            val position = list.groupKeys().indexOf(threeLineGroupOnPageThree.key)
            list.scrollTo(position)
            assertEquals("the row page pill", threeLineGroupOnPageThree.pagePill, list.rowPagePill(position))
            assertEquals(
                "the row quote is the whole group quote and not one of its three lines",
                threeLineGroupOnPageThree.rowQuote,
                list.rowPrimaryText(position),
            )

            assertEquals("adapter item count", ListsFixtures.richGroups.size, list.itemCount())
            assertNotEquals(
                "the adapter must not hold one row per annotation",
                ListsFixtures.RICH_ANNOTATIONS,
                list.itemCount(),
            )
        }
    }

    @Test
    @TestId("LIST-04")
    fun theColourStripMatchesTheAnnotationColour() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            val strips = list.allColorStrips()
            val expected = ListsFixtures.richGroups.map { it.color }
            assertEquals("the colour strip of the first three rows", expected.take(3), strips.take(3))

            val nonPalettePosition = list.groupKeys().indexOf(ListsFixtures.nonPaletteGroup.key)
            assertEquals(
                "the strip of the non palette highlight",
                ListsFixtures.NON_PALETTE,
                strips[nonPalettePosition],
            )

            strips.forEachIndexed { position, colour ->
                assertTrue("row $position paints no colour at all", colour != 0)
                assertTrue("row $position paints a fully transparent strip", (colour ushr 24) != 0)
            }
        }
    }

    @Test
    @TestId("LIST-05")
    fun aNonPaletteColourFallsBackToAHexLabel() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            val labels = list.colorChipLabels()
            assertEquals(
                "one chip is labelled with the uppercase hex of the non palette colour",
                1,
                labels.count { it == ListsFixtures.NON_PALETTE_CHIP },
            )
            assertEquals(
                "the chip labels in the order the scan met the colours",
                listOf(
                    list.string(R.string.all),
                    list.string(HighlightPalette.YELLOW.nameRes),
                    list.string(HighlightPalette.BLUE.nameRes),
                    list.string(HighlightPalette.GREEN.nameRes),
                    ListsFixtures.NON_PALETTE_CHIP,
                ),
                labels,
            )
        }
    }

    @Test
    @TestId("LIST-06")
    @TestDuration(DurationCategory.NORMAL)
    fun theEmptyStateAppearsOnADocumentWithNoHighlights() {
        openReader(ListsFixtures.NONE) { _, _ ->
            val list = openHighlightsAndScan()

            assertTrue("the empty state message is shown", list.messageVisible())
            assertEquals(
                "the empty state text",
                list.string(R.string.no_user_highlights),
                list.messageText(),
            )
            assertEquals("adapter item count", 0, list.itemCount())
            assertFalse("the colour filter row is shown", list.colorFilterVisible())
            assertFalse("the search icon is offered", list.isSearchIconShown())
            assertEquals("the overflow menu items", emptyList<String>(), list.overflowItemLabels())
        }
    }

    @Test
    @TestId("LIST-12")
    @TestDuration(DurationCategory.NORMAL)
    fun highlightsMadeByOtherAppsAppearInTheList() {
        SettingsSeeder.seed(Preferences.detectExistingHighlightsKey, false)
        openReader(ListsFixtures.THIRDPARTY) { _, _ ->
            val list = openHighlightsAndScan()

            assertEquals("adapter item count", 3, list.itemCount())
            assertEquals(
                "every row carries the foreign highlight colour",
                List(3) { ListsFixtures.THIRDPARTY_COLOR },
                list.allColorStrips(),
            )
            (0 until list.itemCount()).forEach { position ->
                list.scrollTo(position)
                assertEquals(
                    "row $position has nothing to reveal, so its expand arrow is invisible",
                    View.INVISIBLE,
                    list.rowExpandArrowVisibility(position),
                )
            }
            assertFalse(
                "the sort by date item is offered on a document with no creation dates",
                list.isOverflowItemShown(R.string.sort_by_creation_date),
            )
        }
    }

    @Test
    @TestId("LIST-13")
    fun aHighlightWithNoExtractableQuoteStillGetsARow() {
        openReader(ListsFixtures.SCANNED) { _, _ ->
            val list = openHighlightsAndScan()

            assertEquals("adapter item count", 1, list.itemCount())
            list.scrollTo(0)
            assertEquals("the row quote of a highlight with no text under it", "", list.rowPrimaryText(0))
            assertEquals("the row page pill", "Page 1", list.rowPagePill(0))
            assertEquals(
                "the note gives the row something to reveal, so its expand arrow is visible",
                View.VISIBLE,
                list.rowExpandArrowVisibility(0),
            )
        }
    }
}
