// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ArtifactBytes
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionDriver
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionPageText
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@LargeTest
@ExpectsProcessDeath
@TestId("RES-54")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SaveProcessKillTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val selection = SelectionRobot()
    private val selectionDriver = SelectionDriver(reader, selection)

    @Test
    fun phaseOneStartSaveAndLeaveItRunning() {
        PhaseRun.requirePhase(SCENARIO, 1)

        val file = seededFixture()
        writeDigestForPhaseTwo(ArtifactBytes.sha256(file))
        rememberSaveDestination(file)
        AnnotationTestHooks.setSaveFaultMode(TestEnvironment.targetContext, AnnotationTestHooks.FAULT_SLOW_WRITE)
        val pageText = SelectionPageText.of(file, HIGHLIGHT_PAGE)
        val highlightTargets = pageText.lineRanges.indices
            .mapNotNull { line ->
                pageText.wordRanges(line)
                    .firstOrNull { range -> pageText.normalized(range).any(Char::isLetterOrDigit) }
            }
            .take(HIGHLIGHT_COUNT)
        assertEquals(
            "page index $HIGHLIGHT_PAGE must expose $HIGHLIGHT_COUNT distinct text lines with word ranges and " +
                "verified pdfium character bounds before phase one can target its highlights",
            HIGHLIGHT_COUNT,
            highlightTargets.size,
        )

        TestEnvironment.targetContext.startActivity(
            ReaderRobot.viewIntent(file).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitReady()

        highlightTargets.forEach { target -> createHighlight(pageText, target) }
        val prepared = reader.readState()
        assertEquals(
            "phase one must create every planned annotation before starting the interrupted save",
            HIGHLIGHT_COUNT as Int?,
            prepared.annotationCount,
        )
        assertEquals(
            "the annotations must be dirty before the save button is pressed",
            true,
            prepared.hasUnsavedAnnotations,
        )
        ReaderProbe.onReaderView(R.id.saveAnnotationsFab) { it.performClick() }
        val blocked = reader.awaitState(
            "the save reports active while the slow-write hook is parked",
            Await.LONG_TIMEOUT_MS,
        ) { state -> AnnotationTestHooks.isSlowWriteBlocked() && state.savingAnnotations == true }
        assertTrue("the slow-write hook must have a blocked writer before phase one exits", AnnotationTestHooks.isSlowWriteBlocked())
        assertEquals("the reader must report an in-flight save at that blocked writer", true, blocked.savingAnnotations)
        ReaderProbe.awaitVisibility(R.id.progressBar, View.VISIBLE)
    }

    @Test
    @TestDuration(DurationCategory.SHORT)
    fun phaseTwoAssertSourceIntact() {
        PhaseRun.requirePhase(SCENARIO, 2)

        val since = CrashGate.timestampNow()
        val file = seededFixture()
        val recorded = readDigestFromPhaseOne()

        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitReady()
        val opened = reader.awaitState("the source document reports its page count", Await.VERY_LONG_TIMEOUT_MS) {
            (it.pageCount ?: 0) > 0
        }
        assertEquals(
            "a save interrupted by a process kill must leave the source openable at its full length",
            ResilienceFixtures.LARGE_PAGE_COUNT as Int?,
            opened.pageCount,
        )

        val now = ArtifactBytes.sha256(file)
        if (now != recorded) {
            val hash = reader.awaitFileHashResolved()
            assertTrue(
                "the source was rewritten by the interrupted save, so it must be a complete document with " +
                    "the highlights in it, but the reader resolved no identity for it",
                hash.isNotEmpty(),
            )
            jumpToPage(HIGHLIGHT_PAGE)
            val annotated = reader.awaitState(
                "the rewritten source reports the highlights the save was writing",
                Await.VERY_LONG_TIMEOUT_MS,
            ) { (it.annotationCount ?: 0) >= HIGHLIGHT_COUNT }
            assertTrue(
                "a rewritten source must carry every highlight, but page index $HIGHLIGHT_PAGE reports " +
                    "${annotated.annotationCount}",
                (annotated.annotationCount ?: 0) >= HIGHLIGHT_COUNT,
            )
        }

        val leftovers = file.parentFile?.listFiles().orEmpty().filter {
            it.name.startsWith(file.name) && (it.name.endsWith(PART_SUFFIX) || it.name.endsWith(PARTIAL_SUFFIX))
        }
        assertTrue(
            "an interrupted save must leave no partial file beside the source, but found ${leftovers.map { it.name }}",
            leftovers.isEmpty(),
        )
        CrashGate.assertNoFatalSince(since)
    }

    private fun writeDigestForPhaseTwo(digest: String) {
        val directory = PhaseRun.stateDirectory()
            ?: throw AssertionError("the phase runner supplied no state directory to carry the pre save digest")
        File(directory, DIGEST_FILE).writeText(digest)
    }

    private fun readDigestFromPhaseOne(): String {
        val directory = PhaseRun.stateDirectory()
            ?: throw AssertionError("the phase runner supplied no state directory to read the pre save digest")
        val file = File(directory, DIGEST_FILE)
        if (!file.isFile) {
            throw AssertionError("phase one wrote no digest at ${file.absolutePath}")
        }
        return file.readText().trim()
    }

    private fun rememberSaveDestination(file: File) {
        val uri = Uri.fromFile(file)
        Records.database.pdfAnnotationSaveDestinationDao().upsert(
            PdfAnnotationSaveDestination(
                sourceKey = SourceKey.of(uri),
                destinationUri = uri.toString(),
                lastSavedHash = null,
                lastSavedAt = LocalDateTime.now(),
            )
        )
    }

    private fun createHighlight(pageText: SelectionPageText, target: IntRange) {
        val targetBounds = pageText.boundsOf(target)
        assertTrue(
            "the target \"${pageText.normalized(target)}\" must have a non-empty extracted text box, but it " +
                "resolved to $targetBounds",
            targetBounds.width > 0f && targetBounds.height > 0f,
        )
        val before = reader.readState().annotationCount ?: 0
        val selected = selectionDriver.longPressRange(pageText, target)
        assertTrue(
            "the long press aimed from verified text bounds must produce a visible selection rectangle",
            selected.selectionRects.orEmpty().isNotEmpty(),
        )
        ReaderProbe.awaitVisibility(R.id.textSelectionActionCard, View.VISIBLE)
        ReaderProbe.tapFirstHighlightSwatch()
        reader.awaitState("the page reports one more annotation than the $before it had", Await.LONG_TIMEOUT_MS) {
            (it.annotationCount ?: 0) > before
        }
    }

    private fun seededFixture(): File = Fixtures.requireSeededFixture(ResilienceFixtures.LARGE, ensureAccess = false)

    private fun awaitReady() {
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)
    }

    private fun jumpToPage(target: Int) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw AssertionError("no reader is resumed while jumping to page index $target")
            activity.findViewById<com.github.barteksc.pdfviewer.PDFView>(R.id.pdfView).jumpTo(target)
        }
        reader.awaitPageIndex(target, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled()
    }

    private companion object {
        const val SCENARIO = "RES-54"
        const val DIGEST_FILE = "res54-source-digest.txt"
        const val HIGHLIGHT_COUNT = 10
        const val HIGHLIGHT_PAGE = 0
        const val PART_SUFFIX = ".part"
        const val PARTIAL_SUFFIX = ".partial"
    }
}
