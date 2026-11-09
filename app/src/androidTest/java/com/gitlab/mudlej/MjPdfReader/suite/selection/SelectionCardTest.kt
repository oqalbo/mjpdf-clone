// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.RectF
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SelectionCardTest : SelectionTestCase() {

    @Test
    @TestId("SEL-25")
    fun aFreshSelectionShowsTheRightNonSwatchActionItems() {
        openLatin { _, page ->
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            SelectionCard.ACTION_BUTTONS.forEach { id ->
                assertEquals(
                    "${SelectionViews.name(id)} must be visible on a fresh selection",
                    View.VISIBLE,
                    SelectionCard.visibilityOf(id),
                )
            }
            assertEquals(
                "a fresh selection offers no delete, because there is no highlight to delete",
                View.GONE,
                SelectionCard.visibilityOf(R.id.textSelectionDeleteHighlightButton),
            )
            assertEquals(
                "the note button must offer to add a note",
                SelectionViews.string(R.string.add_note),
                SelectionCard.contentDescriptionOf(R.id.textSelectionNoteButton),
            )
            assertEquals(
                "the web button must offer a web search for plain text",
                SelectionViews.string(R.string.search_web),
                SelectionCard.contentDescriptionOf(R.id.textSelectionSearchWebButton),
            )
            assertEquals(
                "the swatch row must be visible",
                View.VISIBLE,
                SelectionCard.visibilityOf(R.id.textSelectionSwatchFlow),
            )
            assertTrue(
                "the swatch row must reference at least one swatch, it references ${SelectionCard.swatchCount()}",
                SelectionCard.swatchCount() >= 1,
            )
        }
    }

    @Test
    @TestId("SEL-27")
    fun theCardMovesAboveASelectionLowOnTheScreen() {
        openLatin { _, page ->
            val viewHeight = state().viewHeight!!.toFloat()
            val viewerTop = selectionRobot.readerViewOriginOnScreen().y

            val highLine = page.lineIndexContaining("quick brown fox")
            val high = driver.longPressRange(page, page.rangeOfWordOnLine(highLine, 2))
            SelectionCard.awaitShown()
            val highSelectionY = driver.selectionRects(high).first().centerY()
            assertTrue(
                "the first selection must sit in the top part of the ${viewHeight}px viewer but it is at " +
                    "$highSelectionY",
                highSelectionY < viewHeight * HIGH_FRACTION,
            )
            val highBias = SelectionCard.verticalBias()
            val highBounds = SelectionCard.boundsOnScreen()
            val highRects = driver.selectionRects(high)

            assertEquals("a selection high on the page must push the card to the bottom", 1f, highBias, 0f)
            assertTrue(
                "the card at $highBounds must sit in the lower half of the viewer",
                highBounds.top > viewerTop + viewHeight / 2f,
            )
            assertNoOverlap(highBounds, highRects)

            driver.tapViewPoint(
                android.graphics.PointF(highRects.first().centerX(), highRects.first().bottom + CLEAR_TAP_OFFSET_PX)
            )
            SelectionCard.awaitGone()

            val lowLine = page.lastTextLineIndex()
            val lowWord = page.rangeOfWordOnLine(lowLine, 1)
            SelectionScroll.placePdfPointAt(
                reader,
                page.pageIndex,
                page.boundsOf(lowWord),
                LOW_TARGET_FRACTION_X,
                LOW_TARGET_FRACTION_Y,
                LOW_ZOOM,
            )

            val low = driver.longPressRange(page, lowWord)
            SelectionCard.awaitShown()
            val lowSelectionY = driver.selectionRects(low).first().centerY()
            assertTrue(
                "the second selection must sit below the 65 percent line the card placement uses, but it is at " +
                    "$lowSelectionY of $viewHeight",
                lowSelectionY > viewHeight * LOW_FRACTION,
            )

            val lowBounds = SelectionCard.boundsOnScreen()
            assertEquals("a selection low on the page must push the card to the top", 0f, SelectionCard.verticalBias(), 0f)
            assertTrue(
                "the card at $lowBounds must sit in the upper half of the viewer",
                lowBounds.bottom < viewerTop + viewHeight / 2f,
            )
            assertNoOverlap(lowBounds, driver.selectionRects(low))
        }
    }

    @Test
    @TestId("SEL-28")
    fun copyPlacesExactlyTheSelectedTextOnTheClipboard() {
        openLatin { _, page ->
            driver.longPressWord(page, "quick")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("fox"), projection),
            )

            assertEquals("the clipboard must carry the selected phrase", "quick brown fox", copyAndReadClipboard())
            assertEquals(
                "the clip must be labelled as selected text",
                SelectionViews.string(R.string.selected_text),
                clipboardLabel(),
            )
            SelectionCard.awaitGone()
            assertNull("copying must put the selection away", state().selectionStart)
        }
    }

    @Test
    @TestId("SEL-29")
    fun copyRejoinsAWordWrappedAcrossALineBreak() {
        openLatin { _, page ->
            val wrapped = page.rangeOfFragment("inter\uFFFEnational")
            val marker = wrapped.first + "inter".length
            val inter = wrapped.first until marker
            val national = (marker + 1)..wrapped.last

            driver.longPressRange(page, inter)
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(SelectionHandle.END, SelectionTargets.caretJustAfter(page, national, projection))

            val copied = copyAndReadClipboard()!!
            assertTrue(
                "a word broken across the line break must be copied whole, but the clipboard reads \"$copied\"",
                copied.contains("international"),
            )
            assertTrue(
                "no hyphen may survive inside the rejoined word, the clipboard reads \"$copied\"",
                !copied.contains("inter-") && !copied.contains("inter\n"),
            )
            assertTrue("the copied text must carry no U+FFFE marker", !copied.contains('\uFFFE'))
        }
    }

    @Test
    @TestId("SEL-30")
    fun copyNormalisesLineBreaksToASingleNewlineForm() {
        openLatin { _, page ->
            val firstLine = page.lineIndexContaining("The date they settled")
            driver.longPressRange(page, page.rangeOfWordOnLine(firstLine, 0))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.centreOfLine(page, firstLine + 2, projection),
            )

            val copied = copyAndReadClipboard()!!
            assertEquals(
                "a selection spanning three lines must carry exactly two line breaks, the clipboard reads \"$copied\"",
                2,
                copied.count { it == '\n' },
            )
            assertTrue("no carriage return may survive the copy", !copied.contains('\r'))
            assertTrue("no zero width space may survive the copy", !copied.contains('\u200B'))
            assertTrue("no U+FFFE marker may survive the copy", !copied.contains('\uFFFE'))
        }
    }

    private fun assertNoOverlap(cardBounds: android.graphics.Rect, rects: List<RectF>) {
        val origin = selectionRobot.readerViewOriginOnScreen()
        rects.forEach { rect ->
            val onScreen = RectF(
                rect.left + origin.x,
                rect.top + origin.y,
                rect.right + origin.x,
                rect.bottom + origin.y,
            )
            assertTrue(
                "the card at $cardBounds must not cover the selected text at $onScreen",
                onScreen.right <= cardBounds.left || onScreen.left >= cardBounds.right ||
                    onScreen.bottom <= cardBounds.top || onScreen.top >= cardBounds.bottom,
            )
        }
    }

    private companion object {
        const val HIGH_FRACTION = 0.4f
        const val LOW_FRACTION = 0.65f
        const val LOW_TARGET_FRACTION_X = 0.5f
        const val LOW_TARGET_FRACTION_Y = 0.8f
        const val LOW_ZOOM = 4f
        const val CLEAR_TAP_OFFSET_PX = 120f
    }
}
