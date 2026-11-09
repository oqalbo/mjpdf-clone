// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeJumpTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-35")
    fun aTableOfContentsEntryJumpsTextModeToTheRightPage() {
        TextFixtures.launchReader(TextFixtures.TOC).use {
            text.awaitReaderSettled()
            val hash = reader.awaitFileHashResolved()

            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.tapTableOfContents()

            val entry = TestEnvironment.uiDevice.wait(
                Until.findObject(By.text(TextFixtures.TOC_RESULTS_TITLE)),
                Await.LONG_TIMEOUT_MS,
            ) ?: throw ConditionTimeoutException(
                "The table of contents never showed an entry titled \"${TextFixtures.TOC_RESULTS_TITLE}\""
            )
            entry.click()

            text.awaitShown()
            val expectedPage = TextFixtures.TOC_RESULTS_PAGE_INDEX + 1
            text.awaitPageCounter(text.counterFor(expectedPage, TextFixtures.TOC_PAGES))

            val first = text.firstVisiblePosition()
            assertEquals(
                "the table of contents entry must land on its destination page",
                text.labelFor(expectedPage),
                text.pageLabel(first),
            )
            assertTrue(
                "the destination page must reach non empty text",
                !text.awaitPageText(first).pageText.isNullOrBlank(),
            )
            assertEquals(
                "the stored page index after a table of contents jump",
                TextFixtures.TOC_RESULTS_PAGE_INDEX,
                Records.awaitStoredPage(hash, TextFixtures.TOC_RESULTS_PAGE_INDEX).pageNumber,
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-36")
    fun theGoToPageGridJumpsTextModeToTheChosenPage() {
        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.awaitReaderSettled()
            val hash = reader.awaitFileHashResolved()

            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.openAllPagesGrid()

            gridThumbnail(GRID_PAGE).click()

            text.awaitShown()
            text.awaitPageCounter(text.counterFor(GRID_PAGE, TextFixtures.NORMAL_PAGES))
            assertEquals(
                "the chosen thumbnail must be the page text mode lands on",
                text.labelFor(GRID_PAGE),
                text.pageLabel(text.firstVisiblePosition()),
            )
            assertEquals(
                "the stored page index after a grid jump",
                GRID_PAGE - 1,
                Records.awaitStoredPage(hash, GRID_PAGE - 1).pageNumber,
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-38")
    fun aLongScrollThroughALongDocumentKeepsRenderingPages() {
        val since = TextLogcat.since()

        TextFixtures.launchReader(TextFixtures.LARGE).use {
            text.enterFromReader()
            text.hideControls()
            val startingPage = text.firstVisiblePosition()
            var furthestPage = startingPage
            repeat(FLING_COUNT) {
                text.flingUpOnce()
                val page = text.firstVisiblePosition()
                furthestPage = maxOf(furthestPage, page)
                assertTrue("visible page ${page + 1} is populated during scrolling", !text.awaitPageText(page).pageText.isNullOrBlank())
            }
            assertTrue(
                "$FLING_COUNT flings must traverse multiple pages, reached $furthestPage from $startingPage",
                furthestPage >= startingPage + 2,
            )

            val first = text.firstVisiblePosition()
            assertTrue(
                "the first visible item must still resolve to text after $FLING_COUNT flings",
                !text.awaitPageText(first).pageText.isNullOrBlank(),
            )
            assertEquals(
                "the counter must name the page the list is actually showing",
                text.labelFor(first + 1),
                text.pageLabel(first),
            )
            text.awaitPageCounter(text.counterFor(first + 1, TextFixtures.LARGE_PAGES))
            TextLogcat.assertAbsentSince(since, OUT_OF_MEMORY)

            text.finish()
        }
    }

    private fun gridSelector() = By.res(TestEnvironment.targetPackage, GRID_ID)

    private fun gridThumbnail(oneBasedPage: Int): UiObject2 {
        val label = By.res(TestEnvironment.targetPackage, GRID_PAGE_NUMBER_ID).text(oneBasedPage.toString())
        repeat(GRID_SCROLL_BUDGET) {
            TestEnvironment.uiDevice.wait(Until.hasObject(gridSelector()), Await.SHORT_TIMEOUT_MS)
            val grid = TestEnvironment.uiDevice.findObject(gridSelector())
                ?: throw ConditionTimeoutException("The all pages grid is not on screen")
            grid.findObject(label)?.let { found ->
                return found.parent?.findObject(By.res(TestEnvironment.targetPackage, GRID_CARD_ID))
                    ?: throw ConditionTimeoutException(
                        "The grid cell labelled $oneBasedPage carries no tappable thumbnail card"
                    )
            }
            grid.scroll(Direction.DOWN, GRID_SCROLL_FRACTION)
            TestEnvironment.uiDevice.waitForIdle()
        }
        throw ConditionTimeoutException(
            "The all pages grid never showed a thumbnail labelled $oneBasedPage, not even after " +
                "$GRID_SCROLL_BUDGET scrolls"
        )
    }

    private companion object {
        const val GRID_PAGE = 12
        const val GRID_PAGE_NUMBER_ID = "pageNumber"
        const val GRID_CARD_ID = "pageCard"
        const val GRID_ID = "pageGrid"
        const val GRID_SCROLL_BUDGET = 8
        const val GRID_SCROLL_FRACTION = 0.4f
        const val FLING_COUNT = 20
        const val OUT_OF_MEMORY = "OutOfMemoryError"
    }
}
