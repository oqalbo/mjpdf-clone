// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.zoom.ReaderCanvas
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class RecycleRaceTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val selection = SelectionRobot()

    @After
    fun closeAnyReaderTask() {
        runCatching { MemoryTasks.finishReaderTasks() }
    }

    @Test
    @TestId("MEM-47")
    fun aPagePartRecycledByAnAnnotationEditIsNeverDrawn() {
        val fixture = MemoryFixtures.file(MemoryFixtures.NORMAL)
        val since = CrashGate.timestampNow()

        MemoryTasks.startReader(reader, ReaderRobot.viewIntent(fixture))
        MemoryReader.jumpToPage(reader, EDIT_PAGE_INDEX)
        reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)

        val journal = AnnotationJournalFile.fileOfOpenDocument(reader)
        val view = MemoryReader.pdfView()

        assertTrue(
            "the $EDIT_ROUNDS rounds must fit the $BODY_LINES body lines with at most one line reused. " +
                "testing/fixtures/generators/core.py draws each ${MemoryFixtures.NORMAL} page with " +
                "doc.paragraph at x 72, y height minus 90, size 11 and leading 15, and BODY times three " +
                "wraps to exactly $BODY_LINES lines there, so baselines run from $BODY_TOP_BASELINE down to " +
                "${BODY_TOP_BASELINE - BODY_LEADING * (BODY_LINES - 1)} in a $PAGE_HEIGHT_POINTS point page. " +
                "Rounds are indexed by line rather than stepped by a flat fraction for two reasons: " +
                "longPressInsidePage takes fractions of the page rectangle rather than of the view, so a " +
                "flat step can walk off the end of the text onto blank paper where no selection forms, and " +
                "two rounds on one line risk the second recolouring the first instead of adding, which the " +
                "op assertion below forbids. Raising EDIT_ROUNDS past ${BODY_LINES + 1} reuses lines and " +
                "must be paired with a fixture that carries more of them",
            EDIT_ROUNDS <= BODY_LINES + 1,
        )

        repeat(EDIT_ROUNDS) { round ->
            ReaderCanvas.placeViewportForSetup(EDIT_PAGE_INDEX)
            reader.awaitPageIndex(EDIT_PAGE_INDEX, Await.LONG_TIMEOUT_MS)
            reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
            reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)

            highlightLine(round)
            AnnotationJournalFile.awaitLineCount(journal, round + 1, Await.LONG_TIMEOUT_MS)
            fling(forward = true)
            fling(forward = true)
            fling(forward = false)
            fling(forward = false)
        }

        reader.awaitRenderIdle(Await.VERY_LONG_TIMEOUT_MS)

        assertRecycledBitmapNeverDrawn(since, "$EDIT_ROUNDS highlight edits interleaved with flinging")
        CrashGate.assertNoFatalSince(since)

        val parts = CacheStats.pagePartStats(view)
        assertTrue(
            "the page part cache must hold parts at the moment its bound is read, because an emptied cache " +
                "satisfies any ceiling without testing it. It held $parts",
            parts.total > 0,
        )
        assertTrue(
            "the page part cache must still be inside its bound after repeated invalidation and recycling, " +
                "it held $parts",
            parts.total <= parts.cacheSize,
        )

        val lines = AnnotationJournalFile.awaitLineCount(journal, EDIT_ROUNDS, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "every one of the $EDIT_ROUNDS edits must be journalled, so the run really did invalidate the " +
                "page that many times rather than silently dropping edits made while the page was redrawing",
            EDIT_ROUNDS,
            lines.size,
        )
        assertEquals(
            "every journalled edit must be an add, otherwise a later round recoloured an earlier highlight " +
                "instead of creating a new one and the invalidation count is lower than it looks",
            List(EDIT_ROUNDS) { AnnotationJournalFile.OP_ADD },
            lines.map { it.op },
        )
    }

    @Test
    @TestId("MEM-48")
    @TestDuration(DurationCategory.NORMAL)
    fun aBitmapBeingEncodedIsNotRecycledUnderTheEncoder() {
        val fixture = MemoryFixtures.file(MemoryFixtures.SCANNED_LONG)
        val previews = MemoryReader.previewCacheDirectory()
        runCatching { previews.deleteRecursively() }
        assertEquals(
            "the preview cache must start empty so every file read at the end was written by this run",
            emptyList<String>(),
            previewFiles(previews).map { it.name },
        )

        val since = CrashGate.timestampNow()

        MemoryTasks.startReader(reader, ReaderRobot.viewIntent(fixture))
        val view = MemoryReader.pdfView()

        var flings = 0
        while (flings < ENCODER_FLING_BUDGET && (reader.readStateOrNull()?.page ?: 0) < ENCODER_TARGET_PAGE) {
            fling(forward = true)
            flings++
        }
        val reached = reader.readStateOrNull()?.page ?: 0
        assertTrue(
            "the traversal must have reached page index $ENCODER_TARGET_PAGE for previews to be produced " +
                "faster than the single encoder thread can write them, it reached $reached after $flings flings",
            reached >= ENCODER_TARGET_PAGE,
        )

        val written = awaitPreviewFileCountStable(previews)
        assertTrue(
            "the run must have written preview files, otherwise decoding every file below proves nothing. " +
                "The cache holds $written files",
            written > 0,
        )

        val undecodable = previewFiles(previews).filter { file ->
            val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
            bytes.isEmpty() || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null
        }
        assertEquals(
            "every file in the preview cache must decode to a bitmap. A file that does not is a bitmap that " +
                "was recycled out from under the encoder mid compress, which is exactly what the encodingBitmaps " +
                "pin and the deferredRelease map exist to prevent",
            emptyList<String>(),
            undecodable.map { it.name },
        )

        assertRecycledBitmapNeverDrawn(since, "a fast traversal that outruns the preview encoder")
        CrashGate.assertNoFatalSince(since)

        val stats = CacheStats.previewStats(view)
        val activeBucketPool = stats.poolSizes[stats.activeBucketWidthPx] ?: 0
        assertTrue(
            "the preview store must be live and populated at the moment its pool is read, because a pool " +
                "belonging to an emptied store satisfies any cap without testing it. The store reported $stats",
            stats.entryCount > 0,
        )
        assertTrue(
            "the deferred releases must have returned their bitmaps to the pool rather than dropping them, so " +
                "the active bucket must sit inside its cap of ${stats.perBucketCap}. The store reported $stats",
            activeBucketPool <= stats.perBucketCap,
        )
    }

    private fun highlightLine(round: Int) {
        val horizontalFraction = FIRST_WORD_FRACTION + (round % WORD_COLUMNS) * WORD_STEP_FRACTION
        selection.longPressInsidePage(horizontalFraction, lineFractionOf(round), Await.LONG_TIMEOUT_MS)
        AnnotationCard.awaitShown(Await.LONG_TIMEOUT_MS)
        AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
    }

    private fun lineFractionOf(round: Int): Float {
        val baseline = BODY_TOP_BASELINE - BODY_LEADING * (round % BODY_LINES)
        return (PAGE_HEIGHT_POINTS - (baseline + ABOVE_BASELINE_POINTS)) / PAGE_HEIGHT_POINTS
    }

    private fun fling(forward: Boolean) {
        val device = TestEnvironment.uiDevice
        val x = device.displayWidth / 2
        val far = (device.displayHeight * FLING_FAR_FRACTION).toInt()
        val near = (device.displayHeight * FLING_NEAR_FRACTION).toInt()
        if (forward) {
            device.swipe(x, far, x, near, FLING_STEPS)
        } else {
            device.swipe(x, near, x, far, FLING_STEPS)
        }
    }

    private fun previewFiles(root: File): List<File> =
        if (!root.exists()) emptyList() else root.walkTopDown().filter { it.isFile }.sortedBy { it.name }.toList()

    private fun awaitPreviewFileCountStable(root: File): Int {
        var last = previewFiles(root).size
        Await.untilTrueContinuously(
            condition = "the preview cache file count stops changing",
            stableForMs = ENCODER_STABLE_MS,
            timeoutMs = ENCODER_STABLE_MS + Await.VERY_LONG_TIMEOUT_MS,
            pollMs = ENCODER_POLL_MS,
        ) {
            val current = previewFiles(root).size
            val stable = current == last
            last = current
            stable
        }
        return last
    }

    private fun assertRecycledBitmapNeverDrawn(since: String, what: String) {
        val offending = AppLogcat.appLinesSince(since).filter { it.contains(RECYCLED_BITMAP_MESSAGE) }
        assertEquals(
            "$what must never draw a recycled bitmap. The synchronized guards in AndroidPreviewBitmaps and " +
                "CacheManager exist so that a bitmap being recycled cannot be the one the draw pass is reading. " +
                "The offending lines were:\n" + offending.take(MAX_REPORTED_LINES).joinToString("\n"),
            emptyList<String>(),
            offending.take(MAX_REPORTED_LINES),
        )
    }

    private companion object {

        const val EDIT_PAGE_INDEX = 0
        const val EDIT_ROUNDS = 15
        const val PAGE_HEIGHT_POINTS = 792f
        const val BODY_TOP_BASELINE = 702f
        const val BODY_LEADING = 15f
        const val BODY_LINES = 14
        const val ABOVE_BASELINE_POINTS = 4f
        const val FIRST_WORD_FRACTION = 0.30f
        const val WORD_STEP_FRACTION = 0.06f
        const val WORD_COLUMNS = 4

        const val ENCODER_TARGET_PAGE = 120
        const val ENCODER_FLING_BUDGET = 400
        const val ENCODER_STABLE_MS = 2_000L
        const val ENCODER_POLL_MS = 250L

        const val FLING_FAR_FRACTION = 0.80f
        const val FLING_NEAR_FRACTION = 0.20f
        const val FLING_STEPS = 3

        const val RECYCLED_BITMAP_MESSAGE = "trying to use a recycled bitmap"
        const val MAX_REPORTED_LINES = 20
    }
}
