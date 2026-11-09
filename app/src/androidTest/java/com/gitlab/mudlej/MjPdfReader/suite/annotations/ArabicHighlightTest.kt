// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ArabicHighlightTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-08")
    fun aHighlightOnARightToLeftPageStoresTheArabicTextThatWasSelected() {
        openReader(AnnotationFixtures.ARABIC) { _, _ ->
            val journal = journal()

            val pressed = startArabicSelection()
            AnnotationCard.awaitShown()
            val charactersPressed = selectedCharacters(pressed)

            val extension = AnnotationFixtures.TEXT_COLUMN_WIDTH * EXTENSION_FRACTION * frame().viewPixelsPerPoint()
            selection.dragEndHandleBy(-extension, 0f)

            reader.awaitState(
                "dragging the end handle left across a right to left line takes in more characters than " +
                    "the long press did, which was $charactersPressed"
            ) { selectedCharacters(it) > charactersPressed }

            val selectedRects = selection.selectionRects()
            assertTrue("the extended selection must cover at least one run", selectedRects.isNotEmpty())

            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("one rectangle per selected run", selectedRects.size, line.rects.size)
            line.rects.forEach { rect ->
                assertTrue("a rectangle must run left to right, read $rect", rect[0] < rect[2])
            }

            val quote = line.contents!!
            assertTrue("the stored quote must not be blank", quote.isNotBlank())
            assertTrue(
                "the stored quote must carry Arabic letters, read \"$quote\"",
                quote.any { it in ARABIC_BLOCK },
            )

            val group = AnnotationDump.requireHighlightGroup(0, line.group!!)
            assertEquals("the live highlight dump must report the same quote", quote, group.quote)
        }
    }

    private fun selectedCharacters(state: ReaderState): Int {
        val start = state.selectionStart
            ?: throw ConditionTimeoutException(
                "The selection hook reports no start character index, read: $state"
            )
        val end = state.selectionEnd
            ?: throw ConditionTimeoutException(
                "The selection hook reports no end character index, read: $state"
            )
        return abs(end - start)
    }

    private fun startArabicSelection(): ReaderState {
        var lastError: Throwable? = null
        ARABIC_SELECTION_LINES.forEach { line ->
            val attempt = runCatching {
                selection.longPressAtViewPoint(
                    viewPointOf(AnnotationFixtures.arabicBodyLineRect(line), ARABIC_PRESS_FRACTION),
                    Await.SHORT_TIMEOUT_MS,
                )
            }
            if (attempt.isSuccess) return attempt.getOrThrow()
            lastError = attempt.exceptionOrNull()
        }
        throw ConditionTimeoutException(
            "No long press on the body of ${AnnotationFixtures.ARABIC} produced a selection. Lines were taken " +
                "from the fixture's own layout, ${AnnotationFixtures.ARABIC_BODY_TOP} as the first baseline " +
                "stepping by ${AnnotationFixtures.ARABIC_BODY_LEADING}, right aligned at " +
                "${AnnotationFixtures.ARABIC_RIGHT}. Last failure was $lastError"
        )
    }

    private companion object {
        val ARABIC_SELECTION_LINES = listOf(1, 2, 3, 4, 5)
        const val ARABIC_PRESS_FRACTION = 0.8f
        const val EXTENSION_FRACTION = 0.3f
        val ARABIC_BLOCK = '؀'..'ۿ'
    }
}
