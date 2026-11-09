// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SelectionExtractionTest : SelectionTestCase() {

    @Test
    @TestId("SEL-60")
    @Ignore("PDFium currently merges the authored inter-/national line boundary")
    fun theLatinFixtureExtractsTheLinesItWasDrawnWith() {
        val page = pageTextOf(fixture(SelectionFixtures.LATIN))

        assertEquals(
            "Page 0 of ${SelectionFixtures.LATIN} extracted ${page.lineRanges.size} lines: ${page.readable()}",
            SelectionFixtures.LATIN_LINES.size,
            page.lineRanges.size,
        )

        SelectionFixtures.LATIN_LINES.forEachIndexed { index, expected ->
            assertEquals(
                "Line $index of ${SelectionFixtures.LATIN} must extract as the generator drew it",
                expected,
                page.normalized(page.lineRange(index)),
            )
        }
    }

    @Test
    @TestId("SEL-61")
    fun theSecondLatinPageAndTheArabicFixtureExtractWhatTheyWereDrawnWith() {
        val secondPage = pageTextOf(fixture(SelectionFixtures.LATIN), SECOND_PAGE_INDEX)

        assertEquals(
            "Page 1 of ${SelectionFixtures.LATIN} extracted: ${secondPage.readable()}",
            SelectionFixtures.LATIN_PAGE_2_LINES,
            secondPage.lineRanges.indices.map { secondPage.normalized(secondPage.lineRange(it)) },
        )

        val arabic = pageTextOf(fixture(SelectionFixtures.ARABIC))

        assertEquals(
            "The first line of ${SelectionFixtures.ARABIC} must extract as the seven drawn words in order, " +
                "it extracted: ${arabic.readable()}",
            SelectionFixtures.ARABIC_FIRST_LINE_WORDS,
            arabic.wordsOf(0),
        )
    }

    private companion object {
        const val SECOND_PAGE_INDEX = 1
    }
}
