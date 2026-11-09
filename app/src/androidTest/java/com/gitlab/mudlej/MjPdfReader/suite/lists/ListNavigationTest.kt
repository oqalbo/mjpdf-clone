// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ListNavigationTest : ListsTestCase() {

    @Test
    @TestId("LIST-14")
    fun tappingARowLandsTheReaderOnThatHighlightsPage() {
        openReader(ListsFixtures.RICH) { _, _ ->
            reader.awaitPageIndex(0)

            val list = openHighlightsAndScan()
            val position = list.positionOfPagePill("Page 9")
            list.scrollTo(position)
            list.tapRow(position)

            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(8)

            assertEquals("the resumed activity", MainActivity::class.java.name, Screens.resumedName())
        }
    }

    @Test
    @TestId("LIST-15")
    fun theReaderCentresAndZoomsOnTheHighlight() {
        openReader(ListsFixtures.RICH) { _, _ ->
            val before = reader.readState()
            val startZoom = requireNotNull(before.zoom) { "the hook reports no zoom" }
            val midZoom = requireNotNull(before.midZoom) { "the hook reports no mid zoom" }
            val maxZoom = requireNotNull(before.maxZoom) { "the hook reports no max zoom" }
            val expectedZoom = max(startZoom, midZoom).coerceAtMost(maxZoom)

            val list = openHighlightsAndScan()
            val target = ListsFixtures.lowOnPageSixGroup
            val position = list.groupKeys().indexOf(target.key)
            assertTrue("the fixture carries the group ${target.key}", position >= 0)
            list.scrollTo(position)
            list.tapRow(position)

            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            val after = awaitReaderSettledOn(target.pageIndex)

            val achieved = requireNotNull(after.zoom) { "the hook reports no zoom after the jump" }
            assertTrue(
                "the reader zoomed to $achieved rather than the target $expectedZoom that focusOnHighlight computes " +
                    "from max(zoom, midZoom) coerced to maxZoom",
                abs(achieved - expectedZoom) <= ZOOM_TOLERANCE,
            )

            val passage = ListsFixtures.lowOnPageSixPdfBounds()
            val centre = PageProjection.viewPointOf(after, passage.centerX(), passage.centerY())
            assertTrue(
                "the highlighted passage is off screen after the jump. ${PageProjection.describe(after, passage)}",
                PageProjection.isOnScreen(after, centre),
            )
        }
    }

    @Test
    @TestId("LIST-19")
    @TestDuration(DurationCategory.NORMAL)
    fun theJumpFromTheListIsRecordedInNavigationHistory() {
        openReader(ListsFixtures.RICH) { _, _ ->
            jumpReaderTo(3)

            val list = openHighlightsAndScan()
            val position = list.positionOfPagePill("Page 10")
            list.scrollTo(position)
            list.tapRow(position)

            Screens.awaitGone(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
            awaitReaderSettledOn(9)

            Await.until("the reader back stack carries the page the jump came from") {
                ReaderRoot.backStack().contains(3)
            }

            chooseReaderAction(R.string.nav_back)
            reader.awaitPageIndex(3, Await.LONG_TIMEOUT_MS)
            reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)

            assertEquals("the reader page index after jumping back", 3 as Int?, reader.readState().page)
            Await.until("the forward stack carries the highlight page the reader left") {
                ReaderRoot.forwardStack().contains(9)
            }
        }
    }

    @Test
    @TestId("LIST-31")
    fun tappingANoteRowLandsTheReaderOnThatNotesHighlight() {
        openReader(ListsFixtures.RICH) { _, _ ->
            reader.awaitPageIndex(0)
            val before = reader.readState()
            val startZoom = requireNotNull(before.zoom) { "the hook reports no zoom" }
            val midZoom = requireNotNull(before.midZoom) { "the hook reports no mid zoom" }
            val maxZoom = requireNotNull(before.maxZoom) { "the hook reports no max zoom" }
            val expectedZoom = max(startZoom, midZoom).coerceAtMost(maxZoom)

            val list = openNotesAndScan()
            val position = list.positionOfPagePill("Page 10")
            list.scrollTo(position)
            list.tapRow(position)

            Screens.awaitGone(UserNotesActivity::class.java, Await.LONG_TIMEOUT_MS)
            val after = awaitReaderSettledOn(9)

            val achieved = requireNotNull(after.zoom) { "the hook reports no zoom after the jump" }
            assertTrue(
                "the notes list jump reached zoom $achieved rather than the target $expectedZoom",
                abs(achieved - expectedZoom) <= ZOOM_TOLERANCE,
            )
            assertFalse("a notes list is still on screen", Screens.isInTask(UserNotesActivity::class.java))
        }
    }

    private companion object {
        const val ZOOM_TOLERANCE = 0.01f
    }
}
