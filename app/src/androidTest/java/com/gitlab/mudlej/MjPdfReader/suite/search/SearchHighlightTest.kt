// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.selection.PdfBox
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionProjection
import com.gitlab.mudlej.MjPdfReader.suite.localization.SearchHighlight
import android.graphics.RectF
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.SearchTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SearchHighlightTest : SearchTestCase() {

    @Test
    @TestId("SRCH-46")
    fun theSearchHighlightIsClearedWhenSearchIsClosedWithDone() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            val firstHitPage = SearchFixtures.BEACON_PAGE_INDEXES.first()
            awaitSettledOnPage(firstHitPage)
            awaitMarkerOn(firstHitPage)

            SearchBar.tapDone()
            SearchBar.awaitGone()
            reader.awaitRenderIdle()

            awaitMarkersEmpty()
            assertTrue("the counter and both arrows must leave the view tree", SearchBar.navigationAbsent())
            assertEquals("clearing the marker must not move the reader", firstHitPage, pageIndex())
        }
    }

    @Test
    @TestId("SRCH-48")
    fun aHitThatCannotBeHighlightedSaysSoAndKeepsNavigating() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            SearchBar.awaitResultsLabel()

            SearchTestHooks.failNextSearchHighlight()
            SearchBar.tapNext()

            SearchBar.awaitLabel(SearchBar.failedToHighlightLabel())

            val counter = SearchBar.requireCounter()
            assertEquals(
                "a hit that cannot be highlighted must still count as a hit the user stepped onto, so the " +
                    "counter must have advanced to the second of ${SearchFixtures.BEACON_HITS}",
                2,
                counter.position,
            )
            assertTrue("the counter must still be usable", counter.total >= 1)
            SearchBar.awaitResultsLabel(LABEL_RESTORE_TIMEOUT_MS)
            assertTrue("the restored label must be clickable again", SearchBar.isLabelClickable())
            assertTrue("the counter and both arrows must stay usable", !SearchBar.navigationGone())

            SearchBar.tapNext()
            SearchBar.awaitCounterOf(3, SearchFixtures.BEACON_HITS)
            SearchBar.awaitResultsLabel()
            assertTrue(
                "the very next step must highlight normally, which is what proves the injection was the one " +
                    "shot it claims to be rather than a mode the reader stayed stuck in",
                !SearchBar.navigationGone(),
            )
        }
    }

    @Test
    @TestId("SRCH-49")
    fun searchStateResetsWhenTheDocumentIsReloaded() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())
            awaitMarkerOn(SearchFixtures.BEACON_PAGE_INDEXES.first())

            robot.runReaderAction(R.string.reload_pdf)
            robot.awaitReaderResumed()
            robot.awaitReaderReady()

            SearchBar.awaitGone()
            assertTrue("the counter and both arrows must be gone after a reload", SearchBar.navigationAbsent())
            awaitMarkersEmpty()
            assertTrue(
                "SearchCoordinator must report no running search after the reload, it reports " +
                    "${SearchIdle.progress()}",
                !SearchIdle.isRunning(),
            )
        }
    }

    @Test
    @TestId("SRCH-50")
    fun theZoomToResultSettingRaisesZoomOnlyWhenItIsOn() {
        openReader(SearchFixtures.TEXT) {
            val before = state().zoom!!
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())

            assertEquals(
                "with the setting off the zoom must be left alone",
                before.toDouble(),
                state().zoom!!.toDouble(),
                ZOOM_TOLERANCE,
            )
        }

        seedZoomToResult(true)
        openReader(SearchFixtures.TEXT) {
            val before = state().zoom!!
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES.first())

            val after = state().zoom!!
            val maxZoom = state().maxZoom!!
            assertTrue(
                "with the setting on the zoom must rise, it went from $before to $after",
                after > before,
            )
            assertTrue(
                "the raised zoom must not pass the maximum, it reached $after against a maximum of $maxZoom",
                after <= maxZoom + ZOOM_TOLERANCE,
            )
        }
    }

    @Test
    @TestId("SRCH-51")
    fun theSearchBarNeverCoversTheMatchItJumpedTo() {
        openReader(SearchFixtures.TEXT) {
            robot.search(SearchFixtures.BEACON)
            SearchBar.awaitCounterOf(1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[SearchFixtures.HIGH_HIT_INDEX])
            val high = assertBarClearsTheSettledMatch("the hit near the head of the page")

            SearchBar.tapNext()
            SearchBar.awaitCounterOf(SearchFixtures.LOW_HIT_INDEX + 1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[SearchFixtures.LOW_HIT_INDEX])
            val low = assertBarClearsTheSettledMatch("the hit near the foot of the page", high)

            SearchBar.tapPrevious()
            SearchBar.awaitCounterOf(SearchFixtures.HIGH_HIT_INDEX + 1, SearchFixtures.BEACON_HITS)
            awaitSettledOnPage(SearchFixtures.BEACON_PAGE_INDEXES[SearchFixtures.HIGH_HIT_INDEX])
            assertBarClearsTheSettledMatch("the hit near the head of the page, reached backwards", low)
        }
    }

    private fun assertBarClearsTheSettledMatch(description: String, previous: String? = null): String {
        val bounds = Await.untilValue(
            "updated highlight rectangles are available for $description",
            read = { SearchHighlight.read() },
        ) { it.page == state().page && it.rects.isNotEmpty() && it.raw != previous }
        reader.awaitMotionSettled()
        val canvas = ReaderRobot.pdfViewBounds()
        val bar = SearchBar.barBounds()
        val projection = SelectionProjection.of(state(), bounds.page)
            .shiftedBy(canvas.left.toFloat(), canvas.top.toFloat())
        for (rect in bounds.rects) {
            val match = projection.toViewRect(PdfBox(
                rect.left.toFloat(), minOf(rect.top, rect.bottom).toFloat(),
                rect.right.toFloat(), maxOf(rect.top, rect.bottom).toFloat(),
            ))
            assertTrue(
                "$description must be visible: $match in $canvas",
                RectF.intersects(match, RectF(canvas)),
            )
            assertFalse(
                "the search bar $bar must not cover $description at $match",
                RectF.intersects(match, RectF(bar)),
            )
        }
        val anchoredToTop = SearchBar.isAnchoredToTop()
        assertEquals(
            "the bar's own gravity must agree with where it is drawn for $description",
            anchoredToTop,
            bar.centerY() < canvas.centerY(),
        )
        return bounds.raw
    }

    private fun awaitMarkerOn(pageIndex: Int) {
        reader.awaitState("the search marker set covers page index $pageIndex") {
            it.markerPages?.contains(pageIndex) == true
        }
    }

    private fun awaitMarkersEmpty() {
        val state = reader.awaitState("the search marker page set empties") {
            it.markerPages?.isEmpty() == true
        }
        assertEquals("the search marker page set", emptyList<Int>(), state.markerPages)
    }

    companion object {
        const val ZOOM_TOLERANCE = 0.001
        const val LABEL_RESTORE_TIMEOUT_MS = 5_000L
    }
}
