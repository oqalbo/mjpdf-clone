// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.graphics.PointF
import android.graphics.RectF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot

class StampRobot(private val selection: SelectionRobot = SelectionRobot()) {

    fun viewRectOf(stamp: PendingStamp, frame: PageFrame): RectF = frame.toViewRect(stamp.rect)

    fun dragCentreBy(frame: PageFrame, dx: Float, dy: Float) {
        val rect = viewRectOf(AnnotationDump.requirePendingStamp(), frame)
        dragFrom(PointF(rect.centerX(), rect.centerY()), dx, dy)
    }

    fun dragBottomRightBy(frame: PageFrame, dx: Float, dy: Float) {
        val rect = viewRectOf(AnnotationDump.requirePendingStamp(), frame)
        dragFrom(PointF(rect.right, rect.bottom), dx, dy)
    }

    fun dragBottomRightTo(frame: PageFrame, target: PointF) {
        val rect = viewRectOf(AnnotationDump.requirePendingStamp(), frame)
        dragFrom(PointF(rect.right, rect.bottom), target.x - rect.right, target.y - rect.bottom)
    }

    fun tapDiscardBadge(frame: PageFrame) {
        val rect = viewRectOf(AnnotationDump.requirePendingStamp(), frame)
        val screen = selection.toScreen(PointF(rect.right, rect.top))
        TestEnvironment.uiDevice.click(screen.x.toInt(), screen.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitStampSettled(previous: PdfRect, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRect =
        Await.untilValue(
            "the pending stamp rectangle changes from $previous",
            timeoutMs,
            read = { AnnotationDump.pendingStamp()?.rect },
        ) { it != null && it != previous }!!

    private fun dragFrom(viewPoint: PointF, dx: Float, dy: Float) {
        val from = selection.toScreen(viewPoint)
        RawInput.dragAlongLine(from, PointF(from.x + dx, from.y + dy))
        TestEnvironment.waitForIdleSync()
    }
}
