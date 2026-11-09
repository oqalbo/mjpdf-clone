// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SelectionContextTest : SelectionTestCase() {

    @Test
    @TestId("SEL-55")
    fun aSwatchHandsTheSelectionOffToTheHighlightPathAndClearsIt() {
        openLatin { _, page ->
            val journalDirectory = File(TestEnvironment.targetContext.filesDir, "annotations")
            journalDirectory.listFiles()?.forEach { it.delete() }

            driver.longPressWord(page, "quick")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)
            val selected = driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("fox"), projection),
            )
            val rectCount = driver.selectionRects(selected).size
            val selectionPage = selected.selectionPage!!

            SelectionCard.tapSwatch(0)
            SelectionCard.awaitGone()

            val target = TestEnvironment.onMainThread {
                (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity)
                    ?.annotationDebug()
                    ?: throw ConditionTimeoutException("No resumed reader debug target")
            }
            val groups = Await.untilValue(
                condition = "the live highlight dump reports the new group on page $selectionPage",
                timeoutMs = Await.DEFAULT_TIMEOUT_MS,
                read = { JSONArray(target.dumpPageHighlights(selectionPage)) },
            ) { it.length() == 1 }
            val group = groups.getJSONObject(0)
            assertEquals(
                "the created highlight must carry one rectangle per selection run",
                rectCount,
                group.getInt("rects"),
            )
            assertEquals(
                "the created highlight must quote exactly the selected words",
                "quick brown fox",
                group.getString("quote"),
            )

            assertNull("the swatch must consume the selection", state().selectionStart)
            reader.awaitState("the reader reports unsaved annotations") { it.hasUnsavedAnnotations == true }
            Await.untilNoThrow("the save affordance for unsaved annotations is shown") {
                onView(withId(R.id.saveAnnotationsFab)).check(matches(isDisplayed()))
            }
            Await.until("a journal file appears under filesDir/annotations") {
                journalDirectory.listFiles()?.any { it.name.endsWith(".journal") } == true
            }
        }
    }

    @Test
    @TestId("SEL-56")
    fun selectionWorksWhileZoomedIn() {
        openLatin { _, page ->
            val control = driver.longPressWord(page, "council")
            SelectionCard.awaitShown()
            val fitWidth = driver.selectionRects(control).single().width()
            SelectionCard.tap(R.id.textSelectionDiscardButton)
            SelectionCard.awaitGone()

            val word = page.rangeOf("council")
            SelectionScroll.placePdfPointAt(
                reader,
                page.pageIndex,
                page.boundsOf(word),
                0.5f,
                ZOOMED_TARGET_Y_FRACTION,
                TARGET_ZOOM,
            )
            val zoomed = state()
            assertTrue(
                "the viewport must settle near ${TARGET_ZOOM}x but it reads ${zoomed.zoom}",
                zoomed.zoom!! in ZOOM_LOWER..ZOOM_UPPER,
            )

            val selected = driver.longPressRange(page, word)
            SelectionCard.awaitShown()

            assertEquals("the clipboard must carry the word that was pressed", "council", copyAndReadClipboard())
            val zoomedWidth = driver.selectionRects(selected).single().width()
            assertTrue(
                "the run rectangle must grow with the zoom, it was $fitWidth at fit and is $zoomedWidth at " +
                    "${zoomed.zoom}",
                zoomedWidth >= fitWidth * MINIMUM_WIDTH_RATIO,
            )
            val rect = driver.selectionRects(selected).single()
            val radius = driver.handleCentre(SelectionHandle.START, selected).y - rect.bottom
            val maximum = MAX_HANDLE_RADIUS_DP * TestEnvironment.targetContext.resources.displayMetrics.density
            assertTrue(
                "the handle radius must stay capped at $MAX_HANDLE_RADIUS_DP dp but it reads $radius pixels " +
                    "against a cap of $maximum",
                radius <= maximum + RADIUS_TOLERANCE_PX,
            )
        }
    }

    @Test
    @TestId("SEL-57")
    fun zoomingAfterASelectionKeepsTheSameCharactersSelected() {
        openLatin { _, page ->
            val before = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            val widthBefore = driver.selectionRects(before).single().width()

            SelectionScroll.apply(
                reader,
                ZOOM_AFTER_SELECTION,
                before.offsetX!!,
                before.offsetY!!,
            )

            val after = state()
            assertEquals(
                "the viewport must reach the requested zoom",
                ZOOM_AFTER_SELECTION,
                after.zoom!!,
                ZOOM_TOLERANCE,
            )
            assertEquals("zooming must not move the selection start", before.selectionStart, after.selectionStart)
            assertEquals("zooming must not move the selection end", before.selectionEnd, after.selectionEnd)
            val widthAfter = driver.selectionRects(after).single().width()
            assertTrue(
                "the run rectangle must grow with the page, it was $widthBefore and is now $widthAfter",
                widthAfter >= widthBefore * 2f,
            )
            assertEquals("the card must still act on the same characters", "brown", copyAndReadClipboard())
        }
    }

    @Test
    @TestId("SEL-59")
    fun selectionAlignsWithTheGlyphsWhenCropMarginsIsOn() {
        openLatin { _, page ->
            enableCropMargins()

            val selected = driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()

            assertEquals("the clipboard must carry the word that was pressed", "brown", copyAndReadClipboard())

            val rect = driver.selectionRects(selected).single()
            val pageBounds = selected.pageRect!!
            assertTrue(
                "the run rectangle $rect must lie inside the on screen page bounds $pageBounds reported by the " +
                    "reader hook",
                rect.left >= pageBounds.left - EDGE_TOLERANCE_PX &&
                    rect.right <= pageBounds.right + EDGE_TOLERANCE_PX &&
                    rect.top >= pageBounds.top - EDGE_TOLERANCE_PX &&
                    rect.bottom <= pageBounds.bottom + EDGE_TOLERANCE_PX,
            )
        }
    }

    private fun enableCropMargins() {
        val before = ReaderTestHooks.cropDetectionRuns
        val textMode = TextModeRobot()
        textMode.openReaderActionsDialog()
        textMode.tapReaderTile(SelectionViews.string(R.string.crop_margins))
        Await.until("crop margin detection finishes a run", CROP_TIMEOUT_MS) {
            ReaderTestHooks.cropDetectionRuns > before && !ReaderTestHooks.cropDetectionRunning
        }
        Await.until("the viewer reports crop margins applied", CROP_TIMEOUT_MS) {
            TestEnvironment.onMainThread {
                TestEnvironment.resumedActivity()
                    ?.findViewById<com.github.barteksc.pdfviewer.PDFView>(R.id.pdfView)
                    ?.isCropMarginsEnabled == true
            }
        }
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    private companion object {
        const val TARGET_ZOOM = 3f
        const val ZOOMED_TARGET_Y_FRACTION = 0.4f
        const val ZOOM_LOWER = 2.8f
        const val ZOOM_UPPER = 3.2f
        const val ZOOM_AFTER_SELECTION = 2.5f
        const val ZOOM_TOLERANCE = 0.05f
        const val MINIMUM_WIDTH_RATIO = 2.5f
        const val MAX_HANDLE_RADIUS_DP = 20f
        const val RADIUS_TOLERANCE_PX = 1f
        const val EDGE_TOLERANCE_PX = 2f
        const val CROP_TIMEOUT_MS = 120_000L
    }
}
