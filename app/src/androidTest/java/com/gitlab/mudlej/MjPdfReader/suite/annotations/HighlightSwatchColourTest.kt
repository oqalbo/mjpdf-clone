// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
@TestDuration(DurationCategory.LONG)
class HighlightSwatchColourTest(private val palette: HighlightPalette) : AnnotationTestCase() {

    @Test
    @TestId("ANN-02")
    fun theSwatchTheUserPicksIsTheColourThatGetsStored() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            startSelectionOnNormalPage()
            AnnotationCard.awaitShown()
            AnnotationCard.tapSwatch(palette)

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the journalled operation", AnnotationJournalFile.OP_ADD, line.op)
            assertEquals("the journalled colour for ${palette.name}", palette.colorValue, line.color)

            val group = AnnotationDump.requireHighlightGroup(0, line.group!!)
            assertEquals("the live highlight colour for ${palette.name}", palette.colorValue, group.color)
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun palettes(): List<Array<Any>> = HighlightPalette.defaultSelection.map { arrayOf<Any>(it) }
    }
}
