// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SelectionHandleTest : SelectionTestCase() {

    @Test
    @TestId("SEL-08")
    fun draggingTheEndHandleRightGrowsTheSelectionAlongTheLine() {
        openLatin { _, page ->
            val before = driver.longPressWord(page, "quick")
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val jumps = page.rangeOf("jumps")
            val after = driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, jumps, projection),
            )

            assertEquals("growing the selection must not move its start", before.selectionStart, after.selectionStart)
            assertTrue(
                "the end must have moved forward from ${before.selectionEnd} but it reads ${after.selectionEnd}",
                after.selectionEnd!! > before.selectionEnd!!,
            )
            assertEquals(
                "a selection inside one line must stay one run rectangle",
                1,
                driver.selectionRects(after).size,
            )
            assertEquals(
                "the clipboard must carry the four words the drag covered",
                "quick brown fox jumps",
                copyAndReadClipboard(),
            )
        }
    }

    @Test
    @TestId("SEL-09")
    fun draggingTheEndHandleBackLeftShrinksTheSelection() {
        openLatin { _, page ->
            val start = driver.longPressWord(page, "quick")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)

            val grown = driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("jumps"), projection),
            )
            val shrunk = driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("brown"), projection),
            )

            assertTrue(
                "the end must move back from ${grown.selectionEnd} but it reads ${shrunk.selectionEnd}",
                shrunk.selectionEnd!! < grown.selectionEnd!!,
            )
            assertEquals("shrinking must not move the start", start.selectionStart, grown.selectionStart)
            assertEquals("shrinking must not move the start", start.selectionStart, shrunk.selectionStart)
            assertEquals("the clipboard must carry the two remaining words", "quick brown", copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-10")
    fun draggingTheStartHandleLeftGrowsTheSelectionBackwards() {
        openLatin { _, page ->
            val before = driver.longPressWord(page, "fox")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)

            val after = driver.dragCaretTo(
                SelectionHandle.START,
                SelectionTargets.caretJustBefore(page, page.rangeOf("quick"), projection),
            )

            assertTrue(
                "the start must move back from ${before.selectionStart} but it reads ${after.selectionStart}",
                after.selectionStart!! < before.selectionStart!!,
            )
            assertEquals("the end must not move", before.selectionEnd, after.selectionEnd)
            assertEquals("the clipboard must carry the three words", "quick brown fox", copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-11")
    fun theEndHandleCannotBeDraggedPastTheStartHandle() {
        openLatin { _, page ->
            val before = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val line = page.boundsOf(page.lineRange(page.lineIndexContaining("quick brown fox")))
            val lineLeft = projection.toViewPoint(line.left, line.centerY)
            val after = driver.dragCaretTo(SelectionHandle.END, PointF(lineLeft.x - OVERSHOOT_PX, lineLeft.y))

            assertEquals("the start must not move", before.selectionStart, after.selectionStart)
            assertEquals(
                "the end must stop one character past the start rather than invert or empty the selection",
                after.selectionStart!! + 1,
                after.selectionEnd,
            )
            assertTrue("a one character selection must still draw", driver.selectionRects(after).isNotEmpty())
            assertTrue("the card must survive the clamp", SelectionCard.isShown())
        }
    }

    @Test
    @TestId("SEL-12")
    @TestDuration(DurationCategory.NORMAL)
    fun theStartHandleCannotBeDraggedPastTheEndHandle() {
        openLatin { _, page ->
            val before = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val line = page.boundsOf(page.lineRange(page.lineIndexContaining("quick brown fox")))
            val lineRight = projection.toViewPoint(line.right, line.centerY)
            val after = driver.dragCaretTo(SelectionHandle.START, PointF(lineRight.x + OVERSHOOT_PX, lineRight.y))

            assertEquals("the end must not move", before.selectionEnd, after.selectionEnd)
            assertEquals(
                "the start must stop one character before the end rather than invert the selection",
                after.selectionEnd!! - 1,
                after.selectionStart,
            )
            assertTrue("a one character selection must still draw", driver.selectionRects(after).isNotEmpty())
        }
    }

    @Test
    @TestId("SEL-13")
    @TestDuration(DurationCategory.NORMAL)
    fun draggingTheEndHandleDownSelectsAcrossLines() {
        openLatin { _, page ->
            val shortLine = page.lineIndexContaining("A short line")
            val nextLine = shortLine + 1
            val firstWordRange = page.rangeOfWordOnLine(shortLine, 0)
            val firstWord = page.normalized(firstWordRange)
            driver.longPressRange(page, firstWordRange)
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val target = SelectionTargets.centreOfLine(page, nextLine, projection)
            val after = driver.dragCaretTo(SelectionHandle.END, target)

            val rects = driver.selectionRects(after)
            assertEquals("a selection spanning two lines must report two run rectangles", 2, rects.size)
            assertTrue(
                "the two bands must not overlap vertically, they read ${rects[0]} and ${rects[1]}",
                rects[0].bottom <= rects[1].top || rects[1].bottom <= rects[0].top,
            )

            val copied = copyAndReadClipboard()!!
            assertEquals("the copied text must carry exactly one line break", 1, copied.count { it == '\n' })
            assertTrue(
                "the copied text \"$copied\" must start at the word that was pressed",
                copied.startsWith(firstWord),
            )
            assertTrue(
                "the copied text \"$copied\" must end inside the following line",
                page.normalized(page.lineRange(nextLine)).contains(copied.substringAfterLast('\n')),
            )
        }
    }

    @Test
    @TestId("SEL-14")
    @TestDuration(DurationCategory.NORMAL)
    fun draggingBelowTheLastLineClampsToTheEndOfThePageText() {
        openLatin { _, page ->
            val lastLine = page.lastTextLineIndex()
            val before = driver.longPressRange(page, page.rangeOfWordOnLine(lastLine, 0))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val lastLineRange = page.lineRange(lastLine)
            val endOfText = lastLineRange.last + 1
            assertTrue("the initial selection must not already reach the final glyph", before.selectionEnd!! < endOfText)
            val box = page.boundsOf(lastLineRange)
            val lastCaret = SelectionTargets.caretJustAfter(page, lastLineRange, projection)
            val bottom = projection.toViewPoint(box.right, box.bottom)
            val drag = driver.beginCaretDrag(SelectionHandle.END)
            drag.moveCaretTo(lastCaret)
            val atEnd = drag.state()
            drag.moveCaretTo(PointF(lastCaret.x, bottom.y + BELOW_PAGE_PX))
            val after = drag.release()
            assertTrue("the drag must grow from ${before.selectionEnd} to the final glyph $endOfText, observed ${atEnd.selectionEnd}", atEnd.selectionEnd!! >= endOfText)

            assertTrue(
                "the selection end ${after.selectionEnd} must stay within $endOfText..${after.selectionCharCount}",
                after.selectionEnd!! in endOfText..after.selectionCharCount!!,
            )
            assertTrue("the selection must still draw", driver.selectionRects(after).isNotEmpty())
            assertEquals("the selection must stay on its page", before.selectionPage, after.selectionPage)
            assertTrue("the card must still be displayed", SelectionCard.isShown())
            assertEquals("dragging the end must preserve the start", before.selectionStart, after.selectionStart)
            assertEquals("the clipboard includes the complete final line suffix", page.normalized(before.selectionStart!! until endOfText), copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-15")
    fun aHandleDragDoesNotPanThePage() {
        openLatin { _, page ->
            val before = driver.longPressWord(page, "council")
            SelectionCard.awaitShown()

            val after = driver.dragHandleBy(SelectionHandle.END, 0f, LONG_DRAG_PX)

            assertEquals("a handle drag must not scroll the page", before.offsetY, after.offsetY)
            assertEquals("a handle drag must not turn the page", before.page, after.page)
            assertEquals(
                "no handle may still report itself as being dragged after the finger lifts",
                com.gitlab.mudlej.MjPdfReader.suite.state.SelectionDrag.NONE,
                after.selectionDrag,
            )
        }
    }

    @Test
    @TestId("SEL-16")
    @TestDuration(DurationCategory.NORMAL)
    fun draggingOntoTheFollowingPageKeepsTheSelectionOnItsOwnPage() {
        openReader(SelectionFixtures.LATIN) { _, file ->
            val page = pageTextOf(file, 0)
            val secondPage = pageTextOf(file, 1)
            val settled = state()
            val firstPage = SelectionProjection.of(settled, 0)

            val scrolled = SelectionScroll.toPageBoundary(reader, settled)
            val shifted = firstPage.shiftedBy(
                scrolled.offsetX!! - settled.offsetX!!,
                scrolled.offsetY!! - settled.offsetY!!,
            )

            val lastLine = page.lastTextLineIndex()
            val range = page.rangeOfWordOnLine(lastLine, 0)
            val box = page.boundsOf(range)
            val before = driver.longPressViewPoint(shifted.toViewPoint(box.centerX, box.centerY))
            SelectionCard.awaitShown()

            val secondPageTop = shifted.viewBounds.bottom + (scrolled.spacing ?: 0) * (scrolled.zoom ?: 1f) +
                NEXT_PAGE_REACH_PX
            val after = driver.dragCaretTo(SelectionHandle.END, PointF(shifted.viewBounds.centerX(), secondPageTop))

            assertEquals("the selection must stay on page one", before.selectionPage, after.selectionPage)
            assertTrue(
                "the end ${after.selectionEnd} must clamp to page one's character count ${after.selectionCharCount}",
                after.selectionEnd!! <= after.selectionCharCount!!,
            )
            driver.selectionRects(after).forEach { rect ->
                assertTrue(
                    "the run rectangle $rect must lie inside page one's on screen bounds ${shifted.viewBounds}",
                    rect.top >= shifted.viewBounds.top - EDGE_TOLERANCE_PX &&
                        rect.bottom <= shifted.viewBounds.bottom + EDGE_TOLERANCE_PX,
                )
            }

            val copied = copyAndReadClipboard()!!
            val pageTwoWords = secondPage.text.split(Regex("\\s+")).filter { it.length > 4 }.toSet() -
                page.text.split(Regex("\\s+")).toSet()
            pageTwoWords.forEach { word ->
                assertTrue(
                    "the copied text \"$copied\" must carry no word from page two, but it holds \"$word\"",
                    !copied.contains(word),
                )
            }
        }
    }

    @Test
    @TestId("SEL-17")
    @TestDuration(DurationCategory.NORMAL)
    fun releasingAHandleOverBlankSpaceKeepsTheLastValidSelection() {
        openLatin { _, page ->
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)

            val drag = driver.beginCaretDrag(SelectionHandle.END)
            drag.moveCaretTo(SelectionTargets.caretJustAfter(page, page.rangeOf("jumps"), projection))
            val overText = drag.state()

            val longest = page.boundsOf(page.lineRange(LONGEST_LINE_INDEX))
            val margin = projection.toViewPoint(longest.right, longest.centerY)
            drag.moveCaretTo(PointF(margin.x + BLANK_MARGIN_PX, overText.selectionRects!!.last().centerY()))
            val after = drag.release()

            assertEquals("the start must not move", overText.selectionStart, after.selectionStart)
            assertTrue(
                "the end must not fall back from ${overText.selectionEnd} but it reads ${after.selectionEnd}",
                after.selectionEnd!! >= overText.selectionEnd!!,
            )
            assertTrue("the selection must still draw", driver.selectionRects(after).isNotEmpty())

            val copied = copyAndReadClipboard()!!
            assertTrue("the copied text must not be blank", copied.isNotBlank())
            assertTrue("the copied text \"$copied\" must still start at brown", copied.startsWith("brown"))
            assertEquals(
                "the clipboard must carry exactly the range retained after the release over blank space",
                page.normalized(driver.selectedRange(after)),
                copied,
            )
        }
    }

    @Test
    @TestId("SEL-18")
    fun anArabicSelectionCopiesTheExactArabicText() {
        openArabic { _, page ->
            val words = page.wordsOf(0)
            driver.longPressRange(page, page.rangeOfWordOnLine(0, 2))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val target = SelectionTargets.caretJustAfterInRtl(page, page.rangeOfWordOnLine(0, 4), projection)
            val after = driver.dragCaretTo(SelectionHandle.END, target)

            val expected = page.normalized(driver.selectedRange(after))
            val copied = copyAndReadClipboard()
            assertEquals("the clipboard must carry exactly the selected Arabic characters", expected, copied)
            assertEquals(
                "the selection must cover the three consecutive words the drag ran over",
                words.subList(2, 5).joinToString(" "),
                copied,
            )
            assertTrue("the copied text must carry no U+FFFE marker", !copied!!.contains('\uFFFE'))
            assertTrue("the copied text must carry no zero width space", !copied.contains('\u200B'))
            assertEquals(
                "three words on one line must produce exactly one run rectangle",
                1,
                driver.selectionRects(after).size,
            )
        }
    }

    @Test
    @TestId("SEL-19")
    fun arabicHandleAnchorsAreMirrored() {
        openArabic { _, page ->
            val arabic = driver.longPressRange(page, page.rangeOfWordOnLine(0, 2))
            SelectionCard.awaitShown()

            val arabicRects = driver.selectionRects(arabic)
            val arabicStart = driver.handleCentre(SelectionHandle.START, arabic)
            val arabicEnd = driver.handleCentre(SelectionHandle.END, arabic)

            assertTrue(
                "on right to left text the start handle at ${arabicStart.x} must sit past the right edge " +
                    "${arabicRects.first().right} of the first rectangle",
                arabicStart.x > arabicRects.first().right,
            )
            assertTrue(
                "on right to left text the end handle at ${arabicEnd.x} must sit left of the left edge " +
                    "${arabicRects.last().left} of the last rectangle",
                arabicEnd.x < arabicRects.last().left,
            )
            assertTrue(
                "both handle centres must hang below the band, they read $arabicStart and $arabicEnd against " +
                    "${arabicRects.first().bottom}",
                arabicStart.y > arabicRects.first().bottom && arabicEnd.y > arabicRects.last().bottom,
            )
        }

        openLatin { _, page ->
            val latin = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            val latinRects = driver.selectionRects(latin)
            val latinStart = driver.handleCentre(SelectionHandle.START, latin)
            val latinEnd = driver.handleCentre(SelectionHandle.END, latin)

            assertTrue(
                "on left to right text the start handle at ${latinStart.x} must sit left of the left edge " +
                    "${latinRects.first().left} of the first rectangle",
                latinStart.x < latinRects.first().left,
            )
            assertTrue(
                "on left to right text the end handle at ${latinEnd.x} must sit past the right edge " +
                    "${latinRects.last().right} of the last rectangle",
                latinEnd.x > latinRects.last().right,
            )
            assertTrue(
                "both handle centres must hang below the band",
                latinStart.y > latinRects.first().bottom && latinEnd.y > latinRects.last().bottom,
            )
        }
    }

    @Test
    @TestId("SEL-20")
    fun draggingTheArabicEndHandleLeftwardsGrowsTheSelection() {
        openArabic { _, page ->
            val first = driver.longPressRange(page, page.rangeOfWordOnLine(0, 2))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val twoWords = page.boundsOf(page.rangeOfWordsOnLine(0, 3, 4))
            val widthPx = twoWords.width * projection.viewPixelsPerPoint()

            val grown = driver.dragHandleBy(SelectionHandle.END, -widthPx, 0f)
            val shrunk = driver.dragHandleBy(SelectionHandle.END, widthPx, 0f)

            assertTrue(
                "moving the end handle left over right to left text must grow the selection from " +
                    "${first.selectionEnd} but it reads ${grown.selectionEnd}",
                grown.selectionEnd!! > first.selectionEnd!!,
            )
            assertTrue(
                "moving it back right must shrink the selection from ${grown.selectionEnd} but it reads " +
                    "${shrunk.selectionEnd}",
                shrunk.selectionEnd!! < grown.selectionEnd!!,
            )
            assertEquals("the start must not move", first.selectionStart, grown.selectionStart)
            assertEquals("the start must not move", first.selectionStart, shrunk.selectionStart)
        }
    }

    private companion object {
        const val OVERSHOOT_PX = 200f
        const val BELOW_PAGE_PX = 300f
        const val LONG_DRAG_PX = 400f
        const val BLANK_MARGIN_PX = 150f
        const val NEXT_PAGE_REACH_PX = 40f
        const val EDGE_TOLERANCE_PX = 2f
        const val LONGEST_LINE_INDEX = 7
    }
}
