// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightPaletteSettingTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-04")
    fun aCustomPaletteReplacesTheSwatchesAndDecidesTheStoredColour() {
        seedPalette(
            HighlightPalette.TEAL,
            HighlightPalette.BROWN,
            HighlightPalette.CYAN,
            HighlightPalette.RED,
            HighlightPalette.YELLOW,
            HighlightPalette.GREEN,
        )

        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()

            assertEquals(
                "the card must offer the configured palette in the configured order",
                listOf(
                    HighlightPalette.TEAL,
                    HighlightPalette.BROWN,
                    HighlightPalette.CYAN,
                    HighlightPalette.RED,
                    HighlightPalette.YELLOW,
                    HighlightPalette.GREEN,
                ).map(AnnotationCard::descriptionOf),
                AnnotationCard.awaitSwatches(SWATCH_COUNT),
            )

            AnnotationCard.tapSwatch(0)

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the first configured colour is what gets stored", HighlightPalette.TEAL.colorValue, line.color)
        }
    }

    @Test
    @TestId("ANN-05")
    fun aShortPaletteIsPaddedAndTheReservedNoteColourIsDropped() {
        seedPalette(HighlightPalette.GRAY, HighlightPalette.TEAL, HighlightPalette.TEAL)

        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()

            val descriptions = AnnotationCard.awaitSwatches(SWATCH_COUNT)

            assertEquals(
                "gray is dropped, the duplicate collapses, and the default selection pads the rest",
                listOf(
                    HighlightPalette.TEAL,
                    HighlightPalette.YELLOW,
                    HighlightPalette.ORANGE,
                    HighlightPalette.GREEN,
                    HighlightPalette.BLUE,
                    HighlightPalette.PINK_RED,
                ).map(AnnotationCard::descriptionOf),
                descriptions,
            )
            assertFalse(
                "the reserved note colour must never be offered",
                descriptions.contains(AnnotationCard.descriptionOf(HighlightPalette.GRAY)),
            )
            assertEquals("no swatch may repeat", descriptions.size, descriptions.distinct().size)
        }
    }

    @Test
    @TestId("ANN-06")
    fun anUnparseablePaletteFallsBackToTheDefaults() {
        SettingsSeeder.seed(Preferences.highlightColorsKey, "MAUVE,CHARTREUSE")

        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()

            assertEquals(
                "an unreadable palette must leave the default selection in place",
                HighlightPalette.defaultSelection.map(AnnotationCard::descriptionOf),
                AnnotationCard.awaitSwatches(SWATCH_COUNT),
            )

            AnnotationCard.tapSwatch(0)

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the first default colour is what gets stored", HighlightPalette.YELLOW.colorValue, line.color)
        }
    }

    private fun seedPalette(vararg palettes: HighlightPalette) {
        SettingsSeeder.seed(Preferences.highlightColorsKey, palettes.joinToString(",") { it.name })
    }

    private companion object {
        const val SWATCH_COUNT = 6
    }
}
