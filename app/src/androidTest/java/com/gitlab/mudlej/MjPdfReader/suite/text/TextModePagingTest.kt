// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModePagingTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-29")
    @TestDuration(DurationCategory.SHORT)
    fun scrollingUpdatesThePageCounterAndTheSlider() {
        TextFixtures.launchReader(TextFixtures.LARGE).use {
            text.enterFromReader()
            text.showControls()

            text.scrollUntilCounter(TARGET_PAGE, TextFixtures.LARGE_PAGES)

            text.awaitPageCounter(text.counterFor(TARGET_PAGE, TextFixtures.LARGE_PAGES))
            assertEquals(
                "the page slider must follow the first visible page",
                TARGET_PAGE.toFloat(),
                text.sliderValue(),
                SLIDER_TOLERANCE,
            )
            assertEquals(
                "the first visible item must be the page the counter names",
                text.labelFor(TARGET_PAGE),
                text.pageLabel(text.firstVisiblePosition()),
            )
            assertTrue("the previous page button must be enabled away from page one", text.previousEnabled())

            text.finish()
        }
    }

    @Test
    @TestId("TXT-30")
    fun scrollingSavesThePageNumberToTheDatabase() {
        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.awaitReaderSettled()
            val hash = reader.awaitFileHashResolved()
            Records.awaitStoredPage(hash, 0)

            text.openFromReaderMenu()
            text.awaitPageText(0)
            Records.assertStoredPageStays(hash, 0)

            text.scrollUntilCounter(SAVED_PAGE, TextFixtures.NORMAL_PAGES)
            text.awaitPageCounter(text.counterFor(SAVED_PAGE, TextFixtures.NORMAL_PAGES))

            assertEquals(
                "reading progress made in text mode must reach the library",
                SAVED_PAGE - 1,
                Records.awaitStoredPage(hash, SAVED_PAGE - 1).pageNumber,
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-31")
    fun aPageSliderDragMovesToTheReleasedPage() {
        TextFixtures.launchReader(TextFixtures.LARGE).use {
            text.enterFromReader()
            text.showControls()

            text.dragPageSliderToFraction(MIDPOINT)
            val landed = text.sliderValue().toInt()
            assertTrue(
                "the slider must have moved away from the first page, it reads $landed",
                landed > 1,
            )

            text.awaitPageCounter(text.counterFor(landed, TextFixtures.LARGE_PAGES))
            val first = text.firstVisiblePosition()
            assertEquals(
                "the released slider value is the page you get",
                text.labelFor(landed),
                text.pageLabel(first),
            )
            assertTrue(
                "the target window must load, so the landed page reaches non empty text",
                !text.awaitPageText(first).pageText.isNullOrBlank(),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-32")
    fun aCancelledSliderGestureLeavesThePageUnchanged() {
        TextFixtures.launchReader(TextFixtures.LARGE).use {
            text.enterFromReader()
            text.showControls()
            val firstPageText = text.pageText(0)

            text.cancelPageSliderGesture(MIDPOINT)

            val counterAfterCancel = text.pageCounter()
            val sliderAfterCancel = text.sliderValue()
            val readings = "after the cancel the counter reads " +
                CapturedReflow.literal(counterAfterCancel) + " and the slider reads $sliderAfterCancel"

            assertEquals(
                "a cancelled slider gesture must leave the first visible item on page one, $readings",
                text.labelFor(1),
                text.pageLabel(text.firstVisiblePosition()),
            )
            assertEquals(
                "the page content must not have moved, $readings",
                firstPageText,
                text.pageText(0),
            )
            assertEquals(
                "the page counter must return to page one, $readings",
                text.counterFor(1, TextFixtures.LARGE_PAGES),
                counterAfterCancel,
            )
            assertEquals(
                "the slider value must return to page one, $readings",
                1f,
                sliderAfterCancel,
                SLIDER_TOLERANCE,
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-33")
    fun theNextAndPreviousButtonsMoveOnePageAndDisableAtTheEdges() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader()
            text.showControls()

            assertFalse("the previous button must be disabled on page one", text.previousEnabled())
            assertTrue("the next button must be enabled on page one", text.nextEnabled())

            (2..TextFixtures.STRUCTURED_PAGES).forEach { page ->
                text.tapNextPage()
                text.awaitPageCounter(text.counterFor(page, TextFixtures.STRUCTURED_PAGES))
            }
            assertEquals(
                "stepping forward must reach the last page",
                text.counterFor(TextFixtures.STRUCTURED_PAGES, TextFixtures.STRUCTURED_PAGES),
                text.pageCounter(),
            )
            assertFalse("the next button must be disabled on the last page", text.nextEnabled())

            (TextFixtures.STRUCTURED_PAGES - 1 downTo 1).forEach { page ->
                text.tapPreviousPage()
                text.awaitPageCounter(text.counterFor(page, TextFixtures.STRUCTURED_PAGES))
            }
            assertEquals(
                "stepping back must reach the first page",
                text.counterFor(1, TextFixtures.STRUCTURED_PAGES),
                text.pageCounter(),
            )
            assertFalse("the previous button must be disabled again on page one", text.previousEnabled())

            text.finish()
        }
    }

    @Test
    @TestId("TXT-34")
    @TestDuration(DurationCategory.SHORT)
    fun aOnePageDocumentDisablesTheSlider() {
        TextFixtures.launchReader(TextFixtures.SINGLE_PAGE).use {
            text.enterFromReader()
            text.showControls()

            assertEquals(
                "a one page document reads one of one",
                text.counterFor(1, TextFixtures.SINGLE_PAGE_PAGES),
                text.pageCounter(),
            )
            assertFalse("the page slider must be disabled with one page", text.sliderEnabled())
            assertFalse("the previous button must be disabled with one page", text.previousEnabled())
            assertFalse("the next button must be disabled with one page", text.nextEnabled())
            assertTrue("the single page must still carry its text", text.pageText(0).isNotBlank())

            text.finish()
        }
    }

    private companion object {
        const val TARGET_PAGE = 4
        const val SAVED_PAGE = 6
        const val MIDPOINT = 0.5f
        const val SLIDER_TOLERANCE = 0.001f
    }
}
