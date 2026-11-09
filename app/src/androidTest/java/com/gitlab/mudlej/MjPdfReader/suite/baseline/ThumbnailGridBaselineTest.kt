// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.bookmarks.GoToPageRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ThumbnailGridBaselineTest : BaselineTestCase() {

    private val goToPage = GoToPageRobot()

    @Test
    @TestId("VIS-47")
    fun theThumbnailGridDrawsEveryCellFromItsOwnPage() {
        openTheGridScrolledToTheTop()

        assertEquals(
            "the grid must open at the default column count, which this test does not seed",
            Preferences.goToPageGridColumnsDefault,
            goToPage.spanCount(),
        )
        assertEquals(
            "the grid must hold one cell per page of TLCL 25.12A",
            BaselineFixtures.GOLDEN_BOOK_PAGES,
            goToPage.cellCount(),
        )
        assertTrue(
            "the cell for the reader's page must carry the current page stroke, it reads " +
                "${goToPage.gridCellStrokeWidth(0)} pixels",
            goToPage.gridCellIsCurrentPage(0),
        )
        assertFalse(
            "the next cell must carry the thinner outline stroke instead, it reads " +
                "${goToPage.gridCellStrokeWidth(1)} pixels",
            goToPage.gridCellIsCurrentPage(1),
        )

        val screen = captureScreen()
        assertEveryCellDrewItsOwnPage(screen)

        assertBitmapMatches(
            goldenName = "VIS-47-thumbnail-grid-light.png",
            actual = belowTheSystemStatusBar(screen),
            expectation = GateExpectation.forScreenOutsideTheReader(
                "the Go To Page thumbnail grid for TLCL 25.12A at three columns, light interface theme",
            ),
        )
    }

    @Test
    @TestId("VIS-48")
    fun theThumbnailGridFollowsThePdfPagesThemeIntoDarkness() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pdfPagesThemeKey to Preferences.themeDark,
                Preferences.interfaceThemeKey to Preferences.themeDark,
            )
        )
        openTheGridScrolledToTheTop()

        val screen = captureScreen()
        val frame = belowTheSystemStatusBar(screen)

        assertBitmapMatches(
            goldenName = "VIS-48-thumbnail-grid-dark.png",
            actual = frame,
            expectation = GateExpectation.forScreenOutsideTheReader(
                "the Go To Page thumbnail grid for TLCL 25.12A inverted by the dark pages theme",
            ),
        )
        CrossGolden.assertRoughlyInverted(
            frame,
            "VIS-47-thumbnail-grid-light.png",
            interiorPointsOfTheFirstThumbnail(),
        )
    }

    private fun openTheGridScrolledToTheTop() {
        launchAt(baselineFixture(BaselineFixtures.GOLDEN_BOOK))
        goToPage.openDialogFromReader()
        goToPage.tapAllPages()
        goToPage.awaitGridShown()
        scrollTheGridToTheTop()
        visiblePositions().forEach { goToPage.awaitGridThumbnail(it) }
    }

    private fun scrollTheGridToTheTop() {
        Await.until(
            "the page grid settles with its first row against the top of its content, which it has to be " +
                "driven to because GoToPageActivity posts its own scroll to the reader's page a frame after " +
                "the adapter is set, and a golden of a half scrolled grid records nothing repeatable",
            Await.LONG_TIMEOUT_MS,
        ) {
            TestEnvironment.onMainThread {
                (grid().layoutManager as GridLayoutManager).scrollToPositionWithOffset(0, 0)
            }
            TestEnvironment.waitForIdleSync()
            goToPage.gridFirstVisiblePosition() == 0 && firstRowIsAgainstTheTop()
        }
        goToPage.awaitGridScrollIdle()
        goToPage.awaitGridCellVisible(0)
    }

    private fun firstRowIsAgainstTheTop(): Boolean = TestEnvironment.onMainThread {
        val list = grid()
        val first = list.findViewHolderForAdapterPosition(0)?.itemView ?: return@onMainThread false
        first.top == list.paddingTop
    }

    private fun visiblePositions(): List<Int> =
        (goToPage.gridFirstVisiblePosition()..goToPage.gridLastVisiblePosition()).toList()

    private fun assertEveryCellDrewItsOwnPage(screen: Bitmap) {
        val cells = visiblePositions()
            .filter { thumbnailBoundsOnScreen(it).let { bounds -> isFullyInside(bounds, screen) } }
            .map { it to BaselineCapture.crop(screen, thumbnailBoundsOnScreen(it)) }

        assertTrue(
            "at least $MINIMUM_COMPARED_CELLS thumbnails must be fully on screen for the cell comparison to " +
                "mean anything, only ${cells.size} were",
            cells.size >= MINIMUM_COMPARED_CELLS,
        )

        cells.forEach { (position, image) ->
            val distinct = PixelComparison.distinctColourCount(image)
            assertTrue(
                "the thumbnail for page ${position + 1} holds $distinct distinct colours, below the floor of " +
                    "$CELL_DISTINCT_COLOUR_FLOOR recorded alongside the golden. A cell whose render failed is " +
                    "flat, and a flat cell matches a flat golden forever",
                distinct >= CELL_DISTINCT_COLOUR_FLOOR,
            )
        }

        cells.indices.forEach { first ->
            ((first + 1) until cells.size).forEach { second ->
                assertFalse(
                    "the thumbnails for pages ${cells[first].first + 1} and ${cells[second].first + 1} are " +
                        "byte identical. Every cell drawing the same page is one of the ways this grid has to " +
                        "be wrong, and a non uniformity check alone passes on it",
                    PixelComparison.areByteIdentical(cells[first].second, cells[second].second),
                )
            }
        }
    }

    private fun interiorPointsOfTheFirstThumbnail(): List<CrossGolden.Point> {
        val bounds = thumbnailBoundsOnScreen(0)
        val inset = BaselineCapture.systemStatusBarInset()
        return INTERIOR_FRACTIONS.map { (xFraction, yFraction) ->
            CrossGolden.Point(
                bounds.left + (bounds.width() * xFraction).toInt(),
                bounds.top - inset + (bounds.height() * yFraction).toInt(),
            )
        }
    }

    private fun thumbnailBoundsOnScreen(position: Int): Rect = TestEnvironment.onMainThread {
        val image = grid().findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.findViewById<View>(R.id.pageImage)
            ?: throw ConditionTimeoutException(
                "The grid cell for page index $position is not bound, so its thumbnail has no bounds on screen"
            )
        val location = IntArray(2)
        image.getLocationOnScreen(location)
        Rect(
            location[0] + THUMBNAIL_INSET_PX,
            location[1] + THUMBNAIL_INSET_PX,
            location[0] + image.width - THUMBNAIL_INSET_PX,
            location[1] + image.height - THUMBNAIL_INSET_PX,
        )
    }

    private fun isFullyInside(bounds: Rect, screen: Bitmap): Boolean =
        bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= screen.width && bounds.bottom <= screen.height &&
            bounds.width() > 0 && bounds.height() > 0

    private fun grid(): RecyclerView = goToPage.gridActivity().findViewById(R.id.pageGrid)
        ?: throw ConditionTimeoutException("The go to page grid activity carries no page list")

    private companion object {
        const val MINIMUM_COMPARED_CELLS = 4
        const val CELL_DISTINCT_COLOUR_FLOOR = 32
        const val THUMBNAIL_INSET_PX = 2

        val INTERIOR_FRACTIONS = listOf(
            0.25f to 0.20f,
            0.50f to 0.35f,
            0.75f to 0.50f,
            0.35f to 0.70f,
            0.60f to 0.85f,
        )
    }
}
