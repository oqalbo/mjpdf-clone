// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackgroundWorkTest : ResilienceTestCase() {

    private val selection = SelectionRobot()

    @Test
    @TestId("RES-22")
    fun backgroundingDuringAnAnnotationSaveStillCompletesIt() {
        val file = fixture(ResilienceFixtures.LARGE)
        rememberSaveDestination(file)
        AnnotationTestHooks.setSaveFaultMode(TestEnvironment.targetContext, AnnotationTestHooks.FAULT_SLOW_WRITE)
        val startedAt = System.currentTimeMillis()

        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val documentUri = ReaderProbe.documentUriOfReader()
        val journal = ReaderProbe.journalFileFor(documentUri)
        jumpToPage(HIGHLIGHT_PAGE)
        repeat(HIGHLIGHT_COUNT) { createHighlightOnCurrentPage() }

        ReaderProbe.onReaderView(R.id.saveAnnotationsFab) { it.performClick() }
        Await.until("the held save reaches the parked write", Await.LONG_TIMEOUT_MS) {
            AnnotationTestHooks.isSlowWriteBlocked()
        }
        ReaderProbe.awaitVisibility(R.id.progressBar, View.VISIBLE)

        backgroundReader()
        AnnotationTestHooks.releaseSlowWrite()

        Await.until("the source document is rewritten while the app is away", Await.VERY_LONG_TIMEOUT_MS) {
            file.lastModified() >= startedAt && file.length() > 0
        }
        Await.untilFileAbsent(journal, Await.LONG_TIMEOUT_MS)

        returnToReaderTask(file)
        assertEquals(
            "a completed save must take the save action away on return",
            View.GONE,
            ReaderProbe.visibility(R.id.saveAnnotationsFab),
        )
        assertEquals(
            "a completed save must take the discard action away on return",
            View.GONE,
            ReaderProbe.visibility(R.id.discardAnnotationsFab),
        )

        jumpToPage(HIGHLIGHT_PAGE)
        val annotated = reader.awaitState(
            "the saved page reports its $HIGHLIGHT_COUNT written highlights",
            Await.LONG_TIMEOUT_MS,
        ) { it.annotationCount == HIGHLIGHT_COUNT }
        assertEquals(
            "the reopened page must carry every highlight the interrupted save was writing",
            HIGHLIGHT_COUNT as Int?,
            annotated.annotationCount,
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun rememberSaveDestination(file: File) {
        val uri = android.net.Uri.fromFile(file)
        Records.database.pdfAnnotationSaveDestinationDao().upsert(
            PdfAnnotationSaveDestination(
                sourceKey = SourceKey.of(uri),
                destinationUri = uri.toString(),
                lastSavedHash = null,
                lastSavedAt = LocalDateTime.now(),
            )
        )
    }

    private fun createHighlightOnCurrentPage() {
        val before = state().annotationCount ?: 0
        selection.longPressInsidePage(
            BodyTextTargets.horizontalFraction(highlightIndex),
            BodyTextTargets.largeLine(highlightIndex),
        )
        highlightIndex++
        ReaderProbe.awaitVisibility(R.id.textSelectionActionCard, View.VISIBLE)
        ReaderProbe.tapFirstHighlightSwatch()
        reader.awaitState("the page reports one more annotation than the $before it had", Await.LONG_TIMEOUT_MS) {
            (it.annotationCount ?: 0) > before
        }
        assertEquals(
            "each highlight must land on the page under test, not on a neighbouring one",
            HIGHLIGHT_PAGE as Int?,
            state().page,
        )
    }

    private var highlightIndex = 0

    private companion object {
        const val HIGHLIGHT_PAGE = 3
        const val HIGHLIGHT_COUNT = 5
    }
}
