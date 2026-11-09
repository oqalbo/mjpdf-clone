// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ColorFilterTest : ListsTestCase() {

    @Test
    @TestId("LIST-20")
    fun colourChipsAppearOnlyWhenTheDocumentHasTwoOrMoreColours() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            assertTrue("the colour filter row is shown on a four colour document", list.colorFilterVisible())
            assertEquals("the chip count is All plus one per colour", 5, list.colorChipCount())
        }

        openReader(ListsFixtures.PLAIN) { _, _ ->
            val list = openHighlightsAndScan()
            assertFalse("the colour filter row is shown on a single colour document", list.colorFilterVisible())
        }
    }

    @Test
    @TestId("LIST-21")
    fun filteringByColourNarrowsTheList() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()
            val blue = list.string(HighlightPalette.BLUE.nameRes)

            list.tapChip(blue)
            list.awaitItemCount(BLUE_GROUPS)

            assertEquals(
                "every remaining row is blue",
                List(BLUE_GROUPS) { ListsFixtures.BLUE },
                list.allColorStrips(),
            )
            assertTrue("the blue chip is checked", list.isChipChecked(blue))
            assertFalse("the All chip is still checked", list.isChipChecked(list.string(R.string.all)))
        }
    }

    @Test
    @TestId("LIST-22")
    fun theAllChipRestoresTheFullList() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.tapChip(list.string(HighlightPalette.GREEN.nameRes))
            list.awaitItemCount(GREEN_GROUPS)

            list.tapChip(list.string(R.string.all))
            list.awaitItemCount(ListsFixtures.richGroups.size)
            assertTrue("the All chip is checked again", list.isChipChecked(list.string(R.string.all)))
        }
    }

    @Test
    @TestId("LIST-27")
    fun theColourFilterAndTheSearchCombine() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val list = openHighlightsAndScan()

            list.tapChip(list.string(HighlightPalette.YELLOW.nameRes))
            list.awaitItemCount(YELLOW_GROUPS)

            list.openSearch()
            list.typeQuery(ListsFixtures.WORD_IN_A_YELLOW_AND_A_BLUE_QUOTE)
            list.awaitItemCount(1)

            list.scrollTo(0)
            assertEquals(
                "the surviving row is the yellow one, so the two filters intersected",
                ListsFixtures.YELLOW,
                list.rowColorStrip(0),
            )
            assertEquals(
                "the surviving group",
                listOf("mj-group-05"),
                list.groupKeys(),
            )
        }
    }

    private companion object {
        const val BLUE_GROUPS = 2
        const val GREEN_GROUPS = 2
        const val YELLOW_GROUPS = 4
    }
}
