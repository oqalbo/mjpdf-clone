// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.graphics.PointF
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import java.io.File

object ActionsHighlights {

    const val SELECTION_HORIZONTAL_FRACTION = 0.4f
    const val SELECTION_VERTICAL_FRACTION = 0.25f
    const val STABLE_WINDOW_MS = 1_500L

    val UNHIGHLIGHTED_TARGETS = listOf(
        0.30f to 0.38f,
        0.45f to 0.45f,
        0.30f to 0.18f,
        0.50f to 0.55f,
    )

    fun journalDirectory(): File = File(TestEnvironment.targetContext.filesDir, "annotations")

    fun journalFile(documentUri: String): File =
        File(journalDirectory(), "${SourceKey.of(Uri.parse(documentUri))}.journal")

    fun journalFileOfOpenDocument(reader: ReaderRobot): File {
        val documentUri = reader.awaitState("the reader reports the document uri it is using") {
            !it.documentUri.isNullOrEmpty()
        }.documentUri!!
        return journalFile(documentUri)
    }

    fun journalLineCount(file: File): Int =
        if (file.isFile) file.readLines().count { it.isNotBlank() } else 0

    fun awaitJournalLineCount(file: File, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the annotation journal ${file.name} holds $expected lines",
            timeoutMs,
            read = { journalLineCount(file) },
        ) { it == expected }
    }

    fun assertJournalLineCountStays(file: File, expected: Int, windowMs: Long = STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the annotation journal ${file.name} stays at $expected lines",
            windowMs = windowMs,
        ) { journalLineCount(file) == expected }
    }

    fun createOne(reader: ReaderRobot, selection: SelectionRobot) {
        selection.longPressInsidePage(SELECTION_HORIZONTAL_FRACTION, SELECTION_VERTICAL_FRACTION)
        awaitActionCard()
        tapFirstSwatch()
        reader.awaitState("the reader reports unsaved annotations") { it.hasUnsavedAnnotations == true }
    }

    fun startSelectionOnUnhighlightedText(selection: SelectionRobot) {
        var lastError: Throwable? = null
        UNHIGHLIGHTED_TARGETS.forEach { (horizontal, vertical) ->
            val attempt = runCatching {
                selection.longPressInsidePage(horizontal, vertical, Await.SHORT_TIMEOUT_MS)
            }
            if (attempt.isSuccess) {
                awaitActionCard()
                return
            }
            lastError = attempt.exceptionOrNull()
        }
        throw AssertionError(
            "No long press target produced a fresh text selection. A press landing on text the document " +
                "already carries a highlight over is converted by InlineAnnotationActionController into the " +
                "existing highlight's card, which clears the text selection, so a new edit needs untouched " +
                "text. Last failure was $lastError"
        )
    }

    fun clickFirstSwatchDirectly() {
        val clicked = TestEnvironment.onMainThread {
            val content = contentOrNull()
                ?: throw ConditionTimeoutException("The annotation action card has no content container")
            val swatch = swatchesIn(content).firstOrNull()
                ?: throw ConditionTimeoutException(
                    "The action card carries no highlight swatch, so there is nothing to apply"
                )
            swatch.performClick()
        }
        if (!clicked) {
            throw AssertionError(
                "The highlight swatch refused the click, so no colour was applied and the edit branch under " +
                    "test never ran"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    fun awaitActionCard(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the inline annotation action card is visible", timeoutMs) {
            TestEnvironment.onMainThread {
                cardOrNull()?.visibility == View.VISIBLE
            }
        }
    }

    fun tapFirstSwatch() {
        val point = TestEnvironment.onMainThread {
            val content = contentOrNull()
                ?: throw ConditionTimeoutException("The annotation action card has no content container")
            val swatch = swatchesIn(content).firstOrNull()
                ?: throw ConditionTimeoutException(
                    "The action card carries no highlight swatch. InlineAnnotationActionController builds one " +
                        "clickable container per configured colour, so an empty set means the card was read " +
                        "before rebuildHighlightSwatches ran."
                )
            centreOf(swatch)
        }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    private fun cardOrNull(): View? {
        val activity = TestEnvironment.resumedActivity() ?: return null
        return activity.findViewById(R.id.textSelectionActionCard)
    }

    private fun contentOrNull(): ViewGroup? {
        val activity = TestEnvironment.resumedActivity() ?: return null
        return activity.findViewById(R.id.textSelectionActionContent)
    }

    private fun swatchesIn(content: ViewGroup): List<View> =
        (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filter { child ->
                child.isClickable &&
                    child.contentDescription != null &&
                    child is ViewGroup &&
                    child.childCount == 1 &&
                    child.getChildAt(0) !is ImageView
            }

    private fun centreOf(view: View): PointF {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException("A highlight swatch has no size on screen, so it cannot be tapped")
        }
        return PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }
}
