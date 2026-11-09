// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.HeapSampler
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.HarnessHostile
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ResourceBombTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Ignore("BUG-027: requires a PDFium decoded-stream limit")
    @Test
    @TestId("SEC-06")
    @HarnessHostile(
        bug = "BUG-027",
        damage = "Rendering bomb.pdf inflates a 4 MB content stream towards 4 GB, the app passes 1.9 GB " +
            "resident in under a second, and lowmemorykiller clears headroom by killing " +
            "androidx.test.services, which is the orchestrator's shell executor, so the whole " +
            "instrumentation dies and every later test in the run is lost. Run this one alone and record " +
            "its result by hand",
    )
    fun aDecompressionBombInAContentStreamDoesNotExhaustMemory() {
        assertBoundedRender(SecurityFixtures.BOMB, HeapSampler.BUDGET_256_MB)
    }

    @Test
    @TestId("SEC-07")
    fun aDecompressionBombInAnImageXObjectDoesNotExhaustMemory() {
        assertBoundedRender(SecurityFixtures.BOMB_IMAGE, HeapSampler.BUDGET_256_MB)
    }

    @Test
    @TestId("SEC-08")
    @TestDuration(DurationCategory.SHORT)
    fun anEnormousDeclaredPageCountDoesNotHangTheLoad() {
        val fixture = SecurityFixtures.file(SecurityFixtures.HUGE_PAGECOUNT)
        val sampler = HeapSampler.start()
        var reported: Int? = null
        var outcome: DocumentLoadState? = null

        try {
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(PAGE_COUNT_TIMEOUT_MS)
                outcome = state.loadState
                reported = state.pageCount
            }
        } finally {
            sampler.close()
        }

        if (outcome != DocumentLoadState.FAILED) {
            assertEquals(
                "the reader must report the page objects that exist, not the /Count the file declares",
                SecurityFixtures.HUGE_PAGECOUNT_REAL_PAGES as Int?,
                reported,
            )
        }
        assertTrue(
            "a lie in the page tree must not cost the heap: ${sampler.describe()}",
            sampler.growthBytes() <= HeapSampler.BUDGET_256_MB,
        )
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    @Test
    @TestId("SEC-09")
    fun leavingTheReaderDuringAHostilePageCountLoadStopsTheWork() {
        val fixture = SecurityFixtures.file(SecurityFixtures.HUGE_PAGECOUNT)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            runCatching {
                reader.awaitState("the load is in flight", Await.SHORT_TIMEOUT_MS) {
                    it.loadState == DocumentLoadState.LOADING
                }
            }
            reader.pressBack()
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.holdNoneFor(fixture, SecurityReader.INERT_WINDOW_MS)
    }

    @Test
    @TestId("SEC-15")
    @TestDuration(DurationCategory.SHORT)
    fun anEnormousPageDimensionDoesNotAllocateAnEnormousBitmap() {
        for (name in HUGE_PAGE_VARIANTS) {
            val fixture = SecurityFixtures.file(name)
            val since = SecurityLogcat.mark()
            val sampler = HeapSampler.start()

            try {
                ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                    reader.awaitShown()
                    val state = reader.awaitLoadSettled(PAGE_COUNT_TIMEOUT_MS)
                    if (state.loadState != DocumentLoadState.FAILED && (state.pageCount ?: 0) > 0) {
                        reader.awaitRenderIdle()
                        setZoom(reader.readState().maxZoom ?: DEFAULT_MAX_ZOOM)
                        reader.awaitMotionSettled()
                        reader.awaitRenderIdle()
                    }
                }
            } finally {
                sampler.close()
            }

            assertTrue(
                "$name must not cost the heap at maximum zoom: ${sampler.describe()}",
                sampler.growthBytes() <= HeapSampler.BUDGET_256_MB,
            )
            val previewFailures = SecurityLogcat.linesSince(since)
                .filter { it.contains(PREVIEW_BITMAP_FAILURE) }
            if (name == SecurityFixtures.HUGE_PAGE) {
                assertTrue(
                    "the square variant fits the preview bucket, which clamps width to at most 720 px, so no " +
                        "preview allocation may fail. Logcat held:\n${previewFailures.joinToString("\n")}",
                    previewFailures.isEmpty(),
                )
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            FileDescriptors.awaitNoneFor(fixture)
        }
    }

    @Ignore("BUG-026: requires a safe native text-extraction limit")
    @Test
    @TestId("SEC-16")
    @HarnessHostile(
        bug = "BUG-026",
        damage = "Page Text on huge-text.pdf takes the app to 2.4 GB and aborts natively, and " +
            "lowmemorykiller clears headroom by killing androidx.test.services, which is the " +
            "orchestrator's shell executor, so the whole instrumentation dies and every later test in " +
            "the run is lost. Run this one alone and record its result by hand",
    )
    fun aPageWithFiftyMillionCharactersDoesNotHangTextExtraction() {
        val fixture = SecurityFixtures.file(SecurityFixtures.HUGE_TEXT)
        val sampler = HeapSampler.start()

        try {
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(PAGE_COUNT_TIMEOUT_MS)
                assertEquals(
                    "the fixture must open, otherwise the extraction paths under test are never reached",
                    DocumentLoadState.LOADED,
                    state.loadState,
                )
                reader.awaitRenderIdle()

                SecurityReader.openAction(R.string.copy_page_text)
                awaitDialogOrError(
                    "${string(R.string.selectable_text)} #1",
                    "the page text dialog",
                )
                TestEnvironment.uiDevice.pressBack()
                TestEnvironment.uiDevice.waitForIdle()

                SecurityReader.openAction(R.string.text_mode)
                Await.until(
                    "text mode shows a page or reports an error within ${EXTRACTION_TIMEOUT_MS}ms",
                    EXTRACTION_TIMEOUT_MS,
                    pollMs = POLL_MS,
                ) {
                    TestEnvironment.resumedActivityOfType(TextModeActivity::class.java) != null ||
                        IntakeDialogs.isShowing(string(R.string.file_opening_error))
                }
                TestEnvironment.uiDevice.pressBack()
                Screens.awaitGone(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
            }
        } finally {
            sampler.close()
        }

        assertTrue(
            "nativeGetPageText allocates (charCount + 1) * 2 bytes with only a null check, so this budget is " +
                "what a caught failure looks like: ${sampler.describe()}",
            sampler.growthBytes() <= HeapSampler.BUDGET_512_MB,
        )
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    private fun assertBoundedRender(name: String, budgetBytes: Long) {
        val fixture = SecurityFixtures.file(name)
        val sampler = HeapSampler.start()
        var reachedIdle = false
        var reportedPageError = false
        var loadFailed = false

        try {
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(BOMB_TIMEOUT_MS)
                loadFailed = state.loadState == DocumentLoadState.FAILED
                if (!loadFailed && (state.pageCount ?: 0) > 0) {
                    Await.until(
                        "$name settles at render idle or reports that the page cannot be loaded",
                        BOMB_TIMEOUT_MS,
                        pollMs = POLL_MS,
                    ) {
                        reachedIdle = reader.readStateOrNull()?.render == RenderActivity.IDLE
                        reportedPageError = Snackbars.currentText()?.contains(PAGE_ERROR_MARKER) == true
                        reachedIdle || reportedPageError
                    }
                }
            }
        } finally {
            sampler.close()
        }

        assertTrue(
            "$name must not cost the heap: ${sampler.describe()}",
            sampler.growthBytes() <= budgetBytes,
        )
        assertTrue(
            "$name must either settle at render idle or report that the page cannot be loaded. A page that " +
                "never reaches idle is a failure",
            loadFailed || reachedIdle || reportedPageError,
        )
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    private fun awaitDialogOrError(dialogTitle: String, what: String) {
        Await.until(
            "$what appears or an error is reported within ${EXTRACTION_TIMEOUT_MS}ms",
            EXTRACTION_TIMEOUT_MS,
            pollMs = POLL_MS,
        ) {
            IntakeDialogs.isShowing(dialogTitle) ||
                IntakeDialogs.isShowing(string(R.string.file_opening_error))
        }
    }

    private fun setZoom(zoom: Float) {
        TestEnvironment.onMainThread {
            val activity = SecurityReader.activityOrNull()
                ?: throw ConditionTimeoutException("No reader is resumed while setting the zoom")
            val pdfView = activity.findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while setting the zoom")
            pdfView.debugSetViewport(zoom, pdfView.currentXOffset, pdfView.currentYOffset)
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val BOMB_TIMEOUT_MS = 30_000L
        const val PAGE_COUNT_TIMEOUT_MS = 20_000L
        const val EXTRACTION_TIMEOUT_MS = 20_000L
        const val POLL_MS = 250L
        const val DEFAULT_MAX_ZOOM = 3.0f
        const val PREVIEW_BITMAP_FAILURE = "Cannot create preview bitmap"
        const val PAGE_ERROR_MARKER = "Cannot load page"

        val HUGE_PAGE_VARIANTS = listOf(
            SecurityFixtures.HUGE_PAGE,
            SecurityFixtures.HUGE_PAGE_ASPECT,
        )
    }
}
