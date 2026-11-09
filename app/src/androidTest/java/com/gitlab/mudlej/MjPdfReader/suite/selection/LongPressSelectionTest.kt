// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LongPressSelectionTest : SelectionTestCase() {

    @Test
    @TestId("SEL-01")
    fun longPressSelectsTheWordUnderTheFinger() {
        openLatin { _, page ->
            val state = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            assertEquals("the selection must sit on page 0", 0 as Int?, state.selectionPage)
            assertEquals(
                "a long press must select exactly the five characters of \"brown\"",
                5,
                state.selectionEnd!! - state.selectionStart!!,
            )
            assertEquals(
                "a single word on one line must produce exactly one run rectangle",
                1,
                driver.selectionRects(state).size,
            )
            assertTrue("the selection action card must be displayed", SelectionCard.isShown())
            assertEquals("the clipboard must carry the selected word", "brown", copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-02")
    fun longPressInAnEmptyMarginMakesNoSelection() {
        openLatin { _, page ->
            val clipboardBefore = clipboardText()
            val longestLine = page.lineRange(LONGEST_LINE_INDEX)
            val box = page.boundsOf(longestLine)
            val projection = driver.projection(page.pageIndex)
            val marginPoint = PointF(
                projection.toViewPoint(box.right, box.centerY).x + MARGIN_GAP_PX,
                projection.toViewPoint(box.right, box.centerY).y,
            )
            assertTrue(
                "the blank margin point must still be inside the page rectangle, otherwise the press lands off " +
                    "the page and the test proves nothing",
                marginPoint.x < projection.viewBounds.right,
            )

            driver.pressWithoutSelecting(marginPoint)

            val state = state()
            assertNull("no selection may start in a blank margin", state.selectionStart)
            assertNull("no selection may start in a blank margin", state.selectionEnd)
            SelectionCard.assertStaysGone()
            assertEquals("the clipboard must be untouched", clipboardBefore, clipboardText())
        }
    }

    @Test
    @TestId("SEL-03")
    fun aScannedImageOnlyPageMakesNoSelection() {
        openReader(SelectionFixtures.SCANNED) { _, file ->
            val page = pageTextOf(file)
            assertEquals(
                "the scanned fixture must carry no character data, otherwise this test proves nothing",
                0,
                page.charCount,
            )

            val projection = driver.projection(0)
            driver.pressWithoutSelecting(
                projection.toViewPoint(
                    projection.widthPoints * PRINTED_WORD_X_FRACTION,
                    projection.heightPoints * PRINTED_WORD_Y_FRACTION,
                )
            )

            val state = state()
            assertNull("a page without characters must produce no selection", state.selectionStart)
            assertTrue("a page without characters must produce no run rectangles", driver.selectionRects(state).isEmpty())
            SelectionCard.assertStaysGone()
        }
    }

    @Test
    @TestId("SEL-05")
    fun longPressOnPunctuationSelectsOneCharacter() {
        openLatin { _, page ->
            val semicolon = page.rangeOfFragment(";")
            val state = driver.longPressRange(page, semicolon)
            SelectionCard.awaitShown()

            assertEquals(
                "pressing a symbol must select that symbol only",
                1,
                state.selectionEnd!! - state.selectionStart!!,
            )
            assertEquals("the clipboard must carry the semicolon", ";", copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-06")
    fun anArabicVocalisedWordIsSelectedWithItsMarks() {
        openArabic { _, page ->
            val range = page.rangeOf(SelectionFixtures.ARABIC_VOCALISED_WORD)
            val state = driver.longPressRange(page, range)
            SelectionCard.awaitShown()

            val copied = copyAndReadClipboard()
            assertEquals(
                "selecting a vocalised Arabic word must take the whole word",
                page.normalized(range),
                copied,
            )
            assertEquals(
                "the copied word must be exactly the fixture's vocalised third word",
                SelectionFixtures.ARABIC_VOCALISED_WORD,
                copied,
            )
            val marks = copied!!.count {
                Character.getType(it.code) == Character.NON_SPACING_MARK.toInt()
            }
            assertTrue("the copied word must keep its harakat, but it carries $marks of them", marks >= 2)
            assertEquals(
                "one word on one line must produce exactly one run rectangle",
                1,
                driver.selectionRects(state).size,
            )
        }
    }

    @Test
    @TestId("SEL-07")
    fun dualPageModeSelectsFromThePageActuallyTouched() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = false, dualPage = true)
        SettingsSeeder.seed(Preferences.dualPageFirstPageAloneKey, false)

        openReader(SelectionFixtures.LATIN) { _, file ->
            val spread = reader.awaitState("the reader lays the document out two pages to a row") {
                it.pagesPerRow == 2 && it.rowFirstPage == 0 && it.rowLastPage == 1
            }
            val current = SelectionProjection.of(spread, spread.page!!)
            val gap = current.viewBounds.width() + (spread.spacing ?: 0) * (spread.zoom ?: 1f)
            val rightProjection = when (spread.page) {
                0 -> current.forNeighbourPage(1, gap)
                1 -> current
                else -> throw AssertionError(
                    "A two page spread of pages 0 and 1 must report one of them as the current page, but the " +
                        "reader reports page ${spread.page}"
                )
            }

            val secondPage = pageTextOf(file, 1)
            val firstWord = secondPage.wordsOf(0).first()
            val range = secondPage.rangeOf(firstWord)
            val box = secondPage.boundsOf(range)

            val state = driver.longPressViewPoint(rightProjection.centreOf(box))
            SelectionCard.awaitShown()

            assertEquals("the selection must belong to the page that was touched", 1 as Int?, state.selectionPage)
            assertEquals(
                "the clipboard must carry the first word of page two",
                secondPage.normalized(range),
                copyAndReadClipboard(),
            )
        }
    }

    @Test
    @TestId("SEL-21")
    fun tappingAwayClearsTheSelection() {
        openLatin { _, page ->
            val selected = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            val pageBefore = selected.page

            val rect = driver.selectionRects(selected).first()
            driver.tapViewPoint(PointF(rect.centerX(), rect.bottom + TAP_AWAY_DISTANCE_PX))
            SelectionCard.awaitGone()

            val after = state()
            assertNull("tapping outside the selection must clear it", after.selectionStart)
            assertTrue("the run rectangles must be gone too", driver.selectionRects(after).isEmpty())
            assertEquals("tapping away must not turn the page", pageBefore, after.page)
        }
    }

    @Test
    @TestId("SEL-22")
    fun tappingInsideTheSelectionKeepsIt() {
        openLatin { _, page ->
            val selected = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            val start = selected.selectionStart
            val end = selected.selectionEnd

            val rect = driver.selectionRects(selected).first()
            driver.tapViewPoint(PointF(rect.centerX(), rect.centerY()))
            reader.awaitRenderIdle()

            val after = state()
            assertEquals("a tap inside the selection must not move its start", start, after.selectionStart)
            assertEquals("a tap inside the selection must not move its end", end, after.selectionEnd)
            assertTrue("the action card must survive a tap on the selected words", SelectionCard.isShown())
            assertEquals("Copy must still copy the visibly selected word", "brown", copyAndReadClipboard())
        }
    }

    @Test
    fun tappingTheStartHandleWithoutDraggingKeepsTheCopiedWord() {
        assertHandleTapsPreserveSelection(SelectionHandle.START)
    }

    @Test
    fun tappingTheEndHandleAfterTheStartHandleKeepsTheCopiedWord() {
        assertHandleTapsPreserveSelection(SelectionHandle.START, SelectionHandle.END)
    }

    private fun assertHandleTapsPreserveSelection(vararg handles: SelectionHandle) {
        openLatin { _, page ->
            val selected = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            for (handle in handles) {
                driver.tapViewPoint(driver.handleCentre(handle))
                val after = state()
                assertEquals("a $handle tap preserves the first selected character", selected.selectionStart, after.selectionStart)
                assertEquals("a $handle tap preserves the last selected character", selected.selectionEnd, after.selectionEnd)
                assertTrue("the selected word still has its action card", SelectionCard.isShown())
            }
            assertEquals("Copy returns the visibly selected word after handle taps", "brown", copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-23")
    fun theDiscardButtonClearsTheSelection() {
        openLatin { _, page ->
            val clipboardBefore = clipboardText()
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            SelectionCard.tap(R.id.textSelectionDiscardButton)
            SelectionCard.awaitGone()

            assertNull("the close button must clear the selection", state().selectionStart)
            assertEquals("the close button must not copy anything", clipboardBefore, clipboardText())
        }
    }

    private companion object {
        const val LONGEST_LINE_INDEX = 7
        const val MARGIN_GAP_PX = 60f
        const val TAP_AWAY_DISTANCE_PX = 300f
        const val PRINTED_WORD_X_FRACTION = 0.4f
        const val PRINTED_WORD_Y_FRACTION = 0.8f
    }
}
