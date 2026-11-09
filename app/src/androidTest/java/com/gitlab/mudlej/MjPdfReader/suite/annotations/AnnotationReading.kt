// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import java.io.File

object AnnotationReading {

    fun intentFor(file: File, incognito: Boolean = false): Intent = intentFor(Uri.fromFile(file), incognito)

    fun intentFor(uri: Uri, incognito: Boolean = false): Intent =
        ReaderRobot.viewIntent(uri).apply {
            if (incognito) putExtra(PDF.incognitoKey, true)
        }

    fun awaitReaderReady(reader: ReaderRobot): ReaderState = reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    fun viewPointOf(reader: ReaderRobot, rect: PdfRect, horizontalFraction: Float): PointF {
        val state = reader.readState()
        val page = AnnotationGeometry.frameOf(state)
        val point = page.toViewPoint(rect.left + rect.width * horizontalFraction, rect.centerY)
        return AnnotationGeometry.requireOnScreen(state, point, "the pdf rectangle $rect")
    }

    fun findSelectionPoint(
        reader: ReaderRobot,
        selection: SelectionRobot,
        candidates: List<PdfRect>,
        horizontalFraction: Float,
        what: String,
    ): PointF {
        var lastError: Throwable? = null
        candidates.forEach { rect ->
            val point = viewPointOf(reader, rect, horizontalFraction)
            val attempt = runCatching { selection.longPressAtViewPoint(point, Await.SHORT_TIMEOUT_MS) }
            if (attempt.isSuccess) {
                AnnotationCard.awaitShown()
                return point
            }
            lastError = attempt.exceptionOrNull()
        }
        throw ConditionTimeoutException(
            "No long press on $what produced a text selection. The candidate rectangles were derived from the " +
                "fixture's own layout and read $candidates. Last failure was $lastError"
        )
    }
}
