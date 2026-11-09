// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.gotopage.GoToPageActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class GoToPageGridTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val gridColumns = GridColumnsRule()

    private val reader = ReaderRobot()
    private val goToPage = GoToPageRobot()

    @Test
    @TestId("BMK-50")
    fun theAllPagesGridOpensAndRenders() {
        openGrid(BookmarksFixtures.LARGE, startPageIndex = 24) {
            assertEquals(
                "the grid screen title",
                TestEnvironment.targetContext.getString(R.string.all_pages),
                goToPage.gridTitle(),
            )
            assertEquals("one cell per document page", BookmarksFixtures.LARGE_PAGES, goToPage.cellCount())
            assertEquals(
                "the default column count",
                BookmarksFixtures.GRID_DEFAULT_COLUMNS,
                goToPage.spanCount(),
            )

            goToPage.awaitGridCellVisible(24)
            assertTrue(
                "the cell for the reader's page carries the current page stroke",
                goToPage.gridCellIsCurrentPage(24),
            )
            assertTrue(
                "its neighbour does not",
                !goToPage.gridCellIsCurrentPage(25),
            )

            val first = goToPage.gridFirstVisiblePosition()
            val last = goToPage.gridLastVisiblePosition()
            (first..last).forEach { position ->
                assertEquals(
                    "cell $position shows its one based page number",
                    (position + 1).toString(),
                    goToPage.gridCellPageNumber(position),
                )
                goToPage.awaitGridThumbnail(position)
            }
        }
    }

    @Test
    @TestId("BMK-51")
    @TestDuration(DurationCategory.NORMAL)
    fun theColumnSliderChangesTheGridAndIsRemembered() {
        openGrid(BookmarksFixtures.LARGE, startPageIndex = 0) {
            assertEquals("the grid opens at the default", BookmarksFixtures.GRID_DEFAULT_COLUMNS, goToPage.spanCount())

            val storedBeforeDrag = storedColumns()
            val heldDownTime = goToPage.moveSliderWithoutRelease(toMaximum = true)
            try {
                goToPage.awaitSpanCount(BookmarksFixtures.GRID_MAX_COLUMNS)
                assertEquals(
                    "moving the slider without releasing must not write the preference yet",
                    storedBeforeDrag,
                    storedColumns(),
                )
            } finally {
                goToPage.releaseSlider(heldDownTime, toMaximum = true)
            }

            Await.untilValue(
                condition = "the released slider stores the maximum column count",
                read = { storedColumns() },
            ) { it == BookmarksFixtures.GRID_MAX_COLUMNS }
            goToPage.awaitSpanCount(BookmarksFixtures.GRID_MAX_COLUMNS)

            goToPage.awaitGridCellVisible(0)
            goToPage.awaitGridThumbnail(0)
            val narrowThumbnailWidth = goToPage.gridCellThumbnailWidth(0)

            goToPage.pressBack()
            goToPage.awaitGridClosed()
            reader.awaitShown()
            goToPage.openDialogFromReader()
            goToPage.tapAllPages()
            goToPage.awaitGridShown()

            assertEquals(
                "the reopened grid keeps the chosen column count",
                BookmarksFixtures.GRID_MAX_COLUMNS,
                goToPage.spanCount(),
            )

            goToPage.dragSliderToMinimum()
            goToPage.awaitSpanCount(BookmarksFixtures.GRID_MIN_COLUMNS)
            Await.untilValue(
                condition = "the released slider stores the minimum column count",
                read = { storedColumns() },
            ) { it == BookmarksFixtures.GRID_MIN_COLUMNS }

            goToPage.awaitGridCellVisible(0)
            goToPage.awaitGridThumbnailWiderThan(0, narrowThumbnailWidth)
            assertTrue(
                "two columns must re-render at a larger bucket rather than upscale the seven column bitmaps",
                goToPage.gridCellThumbnailWidth(0) > narrowThumbnailWidth,
            )
        }
    }

    @Test
    @TestId("BMK-52")
    @TestDuration(DurationCategory.NORMAL)
    fun scrollingALargeGridAndUsingTheScrollHandle() {
        openGrid(BookmarksFixtures.LARGE, startPageIndex = 0) {
            val firstBefore = goToPage.gridFirstVisiblePosition()
            val lastBefore = goToPage.gridLastVisiblePosition()

            goToPage.flingGridDown()
            assertTrue("the fling must advance the visible grid rows", goToPage.gridFirstVisiblePosition() > firstBefore)
            goToPage.awaitScrollThumbHidden()

            goToPage.flingGridDown()
            goToPage.dragScrollThumbToBottom()
            goToPage.awaitGridScrollIdle()

            val lastPage = BookmarksFixtures.LARGE_PAGES - 1
            goToPage.awaitGridCellVisible(lastPage)
            assertEquals(
                "the last cell shows the page count",
                BookmarksFixtures.LARGE_PAGES.toString(),
                goToPage.gridCellPageNumber(lastPage),
            )

            goToPage.scrollGridTo(firstBefore)
            goToPage.awaitGridScrollIdle()
            (firstBefore..lastBefore).forEach { position ->
                goToPage.awaitGridThumbnail(position)
            }
        }
    }

    @Test
    @TestId("BMK-52-single-screen")
    fun aGridThatFitsOneScreenNeverShowsTheScrollThumb() {
        SettingsSeeder.seed(Preferences.goToPageGridColumnsKey, BookmarksFixtures.GRID_MAX_COLUMNS)

        openGrid(BookmarksFixtures.NORMAL, startPageIndex = 0) {
            assertEquals("the seeded column count", BookmarksFixtures.GRID_MAX_COLUMNS, goToPage.spanCount())
            assertEquals("every page fits one screen", BookmarksFixtures.NORMAL_PAGES, goToPage.cellCount())

            val firstBefore = goToPage.gridFirstVisiblePosition()
            val lastBefore = goToPage.gridLastVisiblePosition()
            assertFalse("a one-screen grid cannot scroll vertically", goToPage.gridCanScrollVertically())
            goToPage.awaitScrollThumbHidden()
            assertEquals("the one-screen scroll thumb alpha", 0f, goToPage.scrollThumbAlpha(), 0f)

            goToPage.flingGridDown()

            assertEquals(
                "a fling leaves the first visible page unchanged",
                firstBefore,
                goToPage.gridFirstVisiblePosition(),
            )
            assertEquals(
                "a fling leaves the last visible page unchanged",
                lastBefore,
                goToPage.gridLastVisiblePosition(),
            )
            assertEquals("the scroll thumb stays transparent", 0f, goToPage.scrollThumbAlpha(), 0f)
            assertFalse("the grid remains non-scrollable", goToPage.gridCanScrollVertically())
            assertTrue("the grid remains resumed", goToPage.isGridShown())
        }
    }

    @Test
    @TestId("BMK-53")
    @TestDuration(DurationCategory.NORMAL)
    fun tappingAGridThumbnailJumpsTheReader() {
        openGrid(BookmarksFixtures.LARGE, startPageIndex = 24) {
            goToPage.scrollGridTo(TARGET_PAGE_INDEX)
            goToPage.awaitGridCellVisible(TARGET_PAGE_INDEX)
            goToPage.tapGridCell(TARGET_PAGE_INDEX)

            goToPage.awaitGridClosed()
            reader.awaitShown()
            reader.awaitPageIndex(TARGET_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the grid finished rather than being covered, so back does not return to it",
                !goToPage.isGridInAnyStage(),
            )
        }
    }

    @Test
    @TestId("BMK-54")
    @TestDuration(DurationCategory.NORMAL)
    fun rotatingTheGridKeepsYourPlaceInIt() {
        openGrid(BookmarksFixtures.LARGE, startPageIndex = 24) {
            goToPage.scrollGridTo(ROTATION_PAGE_INDEX)
            goToPage.awaitGridScrollIdle()
            val firstBefore = goToPage.gridFirstVisiblePosition()
            val normalizedPositionBefore =
                firstBefore.toFloat() / (BookmarksFixtures.LARGE_PAGES - 1)
            val spanBefore = goToPage.spanCount()

            Rotation.toLandscape(GoToPageActivity::class.java)
            goToPage.awaitGridShown()

            val firstAfter = goToPage.gridFirstVisiblePosition()
            val normalizedPositionAfter =
                firstAfter.toFloat() / (BookmarksFixtures.LARGE_PAGES - 1)
            assertTrue(
                "rotation keeps the same deep grid neighborhood, normalized position " +
                    "$normalizedPositionBefore before and $normalizedPositionAfter after",
                abs(normalizedPositionAfter - normalizedPositionBefore) <= ROTATION_POSITION_TOLERANCE,
            )
            assertEquals("the column count is unchanged by the rotation", spanBefore, goToPage.spanCount())

            goToPage.scrollGridTo(24)
            goToPage.awaitGridCellVisible(24)
            assertTrue(
                "the current page stroke is still on the reader's page",
                goToPage.gridCellIsCurrentPage(24),
            )
        }
    }

    @Test
    @TestId("BMK-55")
    fun theGridOnADocumentWhosePagesCannotBeRead() {
        val intent = Intent(TestEnvironment.targetContext, GoToPageActivity::class.java).apply {
            putExtra(PDF.filePathKey, BookmarksFixtures.uri(BookmarksFixtures.CORRUPT).toString())
            putExtra(PDF.pageNumberKey, 0)
        }

        ActivityScenario.launch<GoToPageActivity>(intent).use {
            Toasts.awaitText(TestEnvironment.targetContext.getString(R.string.failed_to_load_pages))
            goToPage.awaitGridClosed()
            assertTrue(
                "no grid activity may remain in any lifecycle stage",
                !goToPage.isGridInAnyStage(),
            )
        }
    }

    @Test
    @TestId("BMK-57-low")
    fun aColumnCountBelowTheRangeIsClampedWhenTheGridOpens() {
        SettingsSeeder.seed(Preferences.goToPageGridColumnsKey, 0)

        openGrid(BookmarksFixtures.LARGE, startPageIndex = 0) {
            assertEquals(
                "a zero column count renders at the minimum",
                BookmarksFixtures.GRID_MIN_COLUMNS,
                goToPage.spanCount(),
            )
            assertEquals(
                "the slider agrees with the layout",
                BookmarksFixtures.GRID_MIN_COLUMNS.toFloat(),
                goToPage.sliderValue(),
                SLIDER_TOLERANCE,
            )
        }
    }

    @Test
    @TestId("BMK-57-high")
    fun aColumnCountAboveTheRangeIsClampedWhenTheGridOpens() {
        SettingsSeeder.seed(Preferences.goToPageGridColumnsKey, 99)

        openGrid(BookmarksFixtures.LARGE, startPageIndex = 0) {
            assertEquals(
                "an absurd column count renders at the maximum",
                BookmarksFixtures.GRID_MAX_COLUMNS,
                goToPage.spanCount(),
            )
            assertEquals(
                "the slider agrees with the layout",
                BookmarksFixtures.GRID_MAX_COLUMNS.toFloat(),
                goToPage.sliderValue(),
                SLIDER_TOLERANCE,
            )
        }
    }

    private fun storedColumns(): Int =
        TestEnvironment.defaultPreferences.getInt(Preferences.goToPageGridColumnsKey, -1)

    private fun openGrid(fixture: String, startPageIndex: Int, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(BookmarksReader.viewIntent(fixture)).use {
            reader.awaitReady()
            if (startPageIndex > 0) {
                BookmarksReader.jumpToPage(reader, startPageIndex)
            }
            goToPage.openDialogFromReader()
            goToPage.tapAllPages()
            goToPage.awaitGridShown()
            block()
        }
    }

    private companion object {
        const val TARGET_PAGE_INDEX = 299
        const val ROTATION_PAGE_INDEX = 200
        const val ROTATION_POSITION_TOLERANCE = 0.1f
        const val SLIDER_TOLERANCE = 0.001f
    }
}
