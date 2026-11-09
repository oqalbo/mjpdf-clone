// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HighlightLookupTest : ListsTestCase() {

    @Test
    @TestId("LIST-16")
    fun openingAHighlightDoesNotLookItsBoundsUpASecondTime() {
        openReader(ListsFixtures.HUGE) { _, _ ->
            val baseline = requireNotNull(reader.readState().highlightBoundsCalls) {
                "the reader state hook carries no hlBoundsCalls counter, so the double lookup cannot be measured"
            }
            val startZoom = requireNotNull(reader.readState().zoom) { "the hook reports no zoom" }

            val list = openHighlightsAndScan()
            list.scrollTo(0)
            val targetPage = list.items().first().pageIndex
            list.tapRow(0)

            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            val after = awaitReaderSettledOn(targetPage)

            assertEquals(
                "findHighlightPdfBounds was entered again even though the list passed the bounds in its result " +
                    "intent",
                baseline as Int?,
                after.highlightBoundsCalls,
            )
            val achieved = requireNotNull(after.zoom) { "the hook reports no zoom after the jump" }
            assertTrue(
                "the reader stayed at zoom $achieved, so the bounds carried by chosenHighlightBoundsKey were " +
                    "never used to focus on the passage",
                achieved > startZoom,
            )
        }
    }

    @Test
    @TestId("LIST-18")
    @TestDuration(DurationCategory.LONG)
    fun scrollingAHeavilyHighlightedPageDoesNotRegroupOnEveryFrame() {
        openReader(ListsFixtures.HUGE) { _, _ ->
            val list = openHighlightsAndScan()
            list.scrollTo(0)
            val targetPage = list.items().first().pageIndex
            list.tapRow(0)

            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(targetPage)

            val afterArrival = requireNotNull(reader.readState().groupCacheMisses) {
                "the reader state hook carries no groupCacheMisses counter, so regrouping cannot be measured"
            }

            val firstPassPages = dragAcrossPageTenTimes()
            val afterFirstPass = requireNotNull(reader.readState().groupCacheMisses)

            val touchedPages = (firstPassPages + targetPage).toSortedSet()
            val pageCount = checkNotNull(reader.readState().pageCount)
            val reachablePages = maxOf(0, touchedPages.first() - 1)..minOf(pageCount - 1, touchedPages.last() + 1)
            val budget = reachablePages.count()
            assertTrue(
                "the group cache missed ${afterFirstPass - afterArrival} times while dragging across pages " +
                    "$touchedPages, more than the one build per page that $reachablePages allows",
                afterFirstPass - afterArrival <= budget,
            )

            dragAcrossPageTenTimes()
            val afterSecondPass = requireNotNull(reader.readState().groupCacheMisses)
            assertEquals(
                "a second pass over pages already cached rebuilt their group maps again",
                afterFirstPass as Int?,
                afterSecondPass,
            )
        }
    }

    private fun dragAcrossPageTenTimes(): Set<Int> {
        val observed = linkedSetOf<Int>()
        val width = TestEnvironment.uiDevice.displayWidth.toFloat()
        val height = TestEnvironment.uiDevice.displayHeight.toFloat()
        val centre = width / 2f
        val near = height * NEAR_FRACTION
        val far = height * FAR_FRACTION

        repeat(DRAG_PASSES) { pass ->
            val from = if (pass % 2 == 0) far else near
            val to = if (pass % 2 == 0) near else far
            val before = reader.readState()
            RawInput.dragWithoutFling(PointF(centre, from), PointF(centre, to))
            reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
            val after = reader.readState()
            assertTrue(
                "drag ${pass + 1} must move the viewport before cache counts are accepted",
                before.offsetX != after.offsetX || before.offsetY != after.offsetY || before.page != after.page,
            )
            after.page?.let(observed::add)
        }
        reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
        reader.readState().page?.let(observed::add)
        return observed
    }

    private companion object {
        const val DRAG_PASSES = 10
        const val NEAR_FRACTION = 0.42f
        const val FAR_FRACTION = 0.58f
    }
}
