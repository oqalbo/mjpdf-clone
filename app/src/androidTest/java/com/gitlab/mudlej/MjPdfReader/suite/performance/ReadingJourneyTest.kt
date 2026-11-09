// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.performance

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import android.graphics.PointF
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveSheet
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchOracle
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionDriver
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionPageText
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class ReadingJourneyTest : PerformanceTestCase() {

    @Test
    @TestId("PERF-48")
    fun aRepresentativeJourneyProducesNoPdfiumMainThreadWork() {
        SettingsSeeder.seed(Preferences.turnPageByVolumeButtonsKey, true)
        openReader(PerformanceFixtures.HIGHLIGHTS_500) { file ->
            val since = PerfLogcat.since()
            runJourney(file)

            val violations = PerfLogcat.mainThreadViolationsSince(since)
            PerfReport.record("PERF-48", "pdfium main thread violations", violations.size, "count==0")
            assertTrue(
                "Unexpected PDFium work on the main thread (${violations.size} entries):\n${violations.joinToString("\n")}",
                violations.isEmpty(),
            )
            assertEquals(
                "the journey must have finished on the jumped page",
                (JUMP_PAGE - 1) as Int?,
                reader.readState().page,
            )
            assertTrue(
                "the journey must have saved its edits, so the reader must report nothing unsaved",
                reader.readState().hasUnsavedAnnotations == false,
            )
        }
    }

    private fun runJourney(file: java.io.File) {
        repeat(PAGE_TURNS) {
            val before = reader.readState().page ?: 0
            perf.turnPageForward()
            reader.awaitState("the reader advances past page index $before", Await.LONG_TIMEOUT_MS) {
                (it.page ?: before) > before
            }
            reader.awaitFullQualityRender()
        }

        perf.pinchOpen()
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender()
        perf.pinchClose()
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender()

        runSearchAndStep()
        SELECTION_WORDS.take(HIGHLIGHTS).forEach { createHighlight(file, it) }
        saveAnnotations()

        perf.runAction(R.string.user_highlights_title)
        Screens.awaitResumed(UserHighlightsActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitHighlightScanFinished()
        TestEnvironment.uiDevice.pressBack()
        Screens.awaitTopIs(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        openThumbnailGrid()
        scrollThumbnailGrid()
        TestEnvironment.uiDevice.pressBack()
        Screens.awaitTopIs(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        perf.runAction(R.string.text_mode)
        Screens.awaitResumed(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
        repeat(TEXT_MODE_SCROLLS) { scrollTextMode() }
        TestEnvironment.uiDevice.pressBack()
        Screens.awaitTopIs(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        perf.awaitReady()

        perf.goToPage(JUMP_PAGE)
    }

    private fun runSearchAndStep() {
        perf.runAction(R.string.search)
        Await.until("the reader search dialog is shown") {
            TestEnvironment.uiDevice.hasObject(By.text(perf.string(R.string.search_dialog_message)))
        }
        val field = Await.untilNotNull("the search dialog query field is present") { perf.editField() }
        field.text = JOURNEY_QUERY
        perf.confirmDialog()
        repeat(SEARCH_STEPS) { step ->
            val before = Await.untilNotNull("search navigation step ${step + 1} has an available next result") {
                SearchBar.counter()?.takeIf {
                    it.position > 0 && it.total > 1 &&
                        (it.position < it.total || SearchIdle.progress()?.finished == true)
                }
            }
            val handledBefore = SearchOracle.handledNavigationActions()
            SearchBar.tapNext()
            SearchOracle.awaitHandledNavigationActions(handledBefore + 1)
            Await.until("search navigation step ${step + 1} changes the selected result") {
                SearchBar.counter()?.position?.let { it != before.position } == true
            }
            reader.awaitMotionSettled()
        }
        SearchIdle.awaitFinished(Await.VERY_LONG_TIMEOUT_MS)
        SearchBar.tapDone()
        SearchBar.awaitGone()
    }

    private fun createHighlight(file: java.io.File, word: String) {
        reader.awaitMotionSettled()
        val page = SelectionPageText.of(file, reader.readState().page ?: 0)
        val selection = SelectionRobot()
        val driver = SelectionDriver(reader, selection)
        val range = page.rangeOf(word)
        val bounds = RectF(ReaderRobot.pdfViewBounds())
        fun target() = selection.toScreen(driver.viewPointOf(page, range))
        val point = target()
        if (!bounds.contains(point.x, point.y)) {
            val centre = PointF(bounds.centerX(), bounds.centerY())
            val maxDx = bounds.width() * 0.4f
            val maxDy = bounds.height() * 0.4f
            RawInput.dragWithoutFling(
                centre,
                PointF(
                    centre.x + (centre.x - point.x).coerceIn(-maxDx, maxDx),
                    centre.y + (centre.y - point.y).coerceIn(-maxDy, maxDy),
                ),
            )
            reader.awaitMotionSettled()
            reader.awaitFullQualityRender()
        }
        val visiblePoint = target()
        assertTrue(
            "The word '$word' must be inside the reader canvas before selection: $visiblePoint, $bounds",
            bounds.contains(visiblePoint.x, visiblePoint.y),
        )
        driver.longPressWord(page, word)
        val swatch = Await.untilNotNull("the inline annotation card offers a highlight swatch") {
            TestEnvironment.uiDevice.findObject(By.desc(perf.string(R.string.highlight_yellow)))
        }
        swatch.click()
        TestEnvironment.uiDevice.waitForIdle()
        reader.awaitNoSelection(Await.LONG_TIMEOUT_MS)
    }

    private fun awaitHighlightScanFinished() {
        var last = -1
        Await.untilTrueContinuously(
            condition = "the highlights list reaches a stable row count",
            stableForMs = STABLE_WINDOW_MS,
            timeoutMs = Await.VERY_LONG_TIMEOUT_MS,
            pollMs = PerfReader.COARSE_POLL_MS,
        ) {
            val now = TestEnvironment.onMainThread {
                TestEnvironment.resumedActivityOfType(UserHighlightsActivity::class.java)
                    ?.findViewById<RecyclerView>(R.id.userHighlightsRecyclerView)?.adapter?.itemCount ?: 0
            }
            val stable = now > 0 && now == last
            last = now
            stable
        }
    }

    private fun openThumbnailGrid() {
        perf.openGoToPageDialog()
        onView(withText(perf.string(R.string.all_pages))).inRoot(isDialog()).perform(click())
        Await.until("the thumbnail grid is populated", Await.LONG_TIMEOUT_MS) { thumbnailCount() > 0 }
    }

    private fun thumbnailCount(): Int = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivity()?.findViewById<RecyclerView>(R.id.pageGrid)?.adapter?.itemCount ?: 0
    }

    private fun scrollThumbnailGrid() {
        repeat(GRID_FLINGS) {
            TestEnvironment.uiDevice.swipe(
                TestEnvironment.uiDevice.displayWidth / 2,
                (TestEnvironment.uiDevice.displayHeight * FLING_FAR).toInt(),
                TestEnvironment.uiDevice.displayWidth / 2,
                (TestEnvironment.uiDevice.displayHeight * FLING_NEAR).toInt(),
                FLING_STEPS,
            )
            TestEnvironment.uiDevice.waitForIdle()
        }
    }

    private fun scrollTextMode() {
        TestEnvironment.uiDevice.swipe(
            TestEnvironment.uiDevice.displayWidth / 2,
            (TestEnvironment.uiDevice.displayHeight * FLING_FAR).toInt(),
            TestEnvironment.uiDevice.displayWidth / 2,
            (TestEnvironment.uiDevice.displayHeight * FLING_NEAR).toInt(),
            FLING_STEPS,
        )
        TestEnvironment.uiDevice.waitForIdle()
    }

    private fun saveAnnotations() {
        Await.until("the reader reports unsaved annotations", Await.LONG_TIMEOUT_MS) {
            reader.readStateOrNull()?.hasUnsavedAnnotations == true
        }
        TestEnvironment.onMainThread { perf.activity().findViewById<View>(R.id.saveAnnotationsFab).performClick() }
        TestEnvironment.uiDevice.waitForIdle()
        Await.until("the save destination appears or a direct save starts") {
            SaveSheet.isShown() || reader.readStateOrNull()?.let {
                it.savingAnnotations == true || it.hasUnsavedAnnotations == false
            } == true
        }
        if (SaveSheet.isShown()) SaveSheet.chooseUpdateExisting()
        Await.until("the save finishes", SAVE_TIMEOUT_MS, PerfReader.COARSE_POLL_MS) {
            reader.readStateOrNull()?.savingAnnotations == false &&
                reader.readStateOrNull()?.hasUnsavedAnnotations == false
        }
    }

    companion object {

        const val PAGE_TURNS = 10
        const val SEARCH_STEPS = 5
        const val HIGHLIGHTS = 3
        const val TEXT_MODE_SCROLLS = 5
        const val GRID_FLINGS = 4
        const val JUMP_PAGE = 50
        const val JOURNEY_QUERY = "the"

        val SELECTION_WORDS = listOf("reader", "document", "bookkeeping")
        const val STABLE_WINDOW_MS = 1_000L
        const val SAVE_TIMEOUT_MS = 120_000L

        const val FLING_FAR = 0.8f
        const val FLING_NEAR = 0.2f
        const val FLING_STEPS = 5
    }
}
