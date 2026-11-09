// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.content.Intent
import android.app.ActivityManager
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateSource
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderQuality
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

class ReaderRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        awaitDisplayed(R.id.pdfView, timeoutMs)
    }

    fun acceptCleartextWarning(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilNoThrow("the cleartext warning can be accepted", timeoutMs) {
            onView(withText(R.string.proceed_anyway)).inRoot(isDialog()).perform(click())
        }
        TestEnvironment.waitForIdleSync()
    }

    fun finishAndRemoveTask(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        TestEnvironment.onMainThread {
            TestEnvironment.targetContext.getSystemService(ActivityManager::class.java)
                ?.appTasks
                ?.filter { it.taskInfo.baseIntent.component?.packageName == targetPackage }
                ?.forEach { it.finishAndRemoveTask() }
        }
        Screens.awaitGone(MainActivity::class.java, timeoutMs)
    }

    fun withExternalIntent(intent: Intent, body: () -> Unit) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TestEnvironment.targetContext.startActivity(intent)
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        try {
            body()
        } finally {
            finishAndRemoveTask()
        }
    }

    fun readStateOrNull(): ReaderState? = ReaderStateSource.read()

    fun readState(): ReaderState = readStateOrNull()
        ?: throw ConditionTimeoutException(
            "The reader state hook produced nothing. Either no reader is resumed, or the debug hook is absent from this build."
        )

    fun awaitState(
        condition: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        predicate: (ReaderState) -> Boolean,
    ): ReaderState = Await.untilValue(
        condition = condition,
        timeoutMs = timeoutMs,
        read = { readStateOrNull() },
        predicate = { it != null && predicate(it) },
    )!!

    fun awaitLoaded(timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState =
        awaitState("the document reports load state Loaded", timeoutMs) {
            it.loadState == DocumentLoadState.LOADED
        }

    fun awaitLoadSettled(timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState =
        awaitState("the document load leaves Loading", timeoutMs) {
            it.loadState != null && it.loadState != DocumentLoadState.LOADING
        }

    fun awaitRenderIdle(timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState =
        awaitState("rendering is idle", timeoutMs) { it.render == RenderActivity.IDLE }

    fun awaitFullQualityRender(timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState =
        awaitState("rendering is idle at full quality", timeoutMs) {
            it.render == RenderActivity.IDLE && it.quality == RenderQuality.FULL
        }

    fun awaitReady(shownTimeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState {
        awaitShown(shownTimeoutMs)
        awaitLoaded()
        return awaitRenderIdle()
    }

    fun awaitReadyAtFullQuality(shownTimeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState {
        awaitShown(shownTimeoutMs)
        awaitLoaded()
        return awaitFullQualityRender()
    }

    fun awaitPageIndex(pageIndex: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState =
        awaitState("the reader reaches page index $pageIndex", timeoutMs) { it.page == pageIndex }

    fun awaitMotionSettled(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState =
        awaitState("no animation or fling is running", timeoutMs) { it.isSettled }

    fun awaitZoom(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS, predicate: (Float) -> Boolean): ReaderState =
        awaitState("zoom reaches the expected value", timeoutMs) { it.zoom?.let(predicate) == true }

    fun awaitFileHashResolved(timeoutMs: Long = Await.LONG_TIMEOUT_MS): String =
        awaitState("the document identity resolves", timeoutMs) { !it.fileHash.isNullOrEmpty() }.fileHash!!

    fun awaitSelection(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState =
        awaitState("a text selection exists", timeoutMs) { it.hasSelection }

    fun awaitNoSelection(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ReaderState =
        awaitState("no text selection exists", timeoutMs) { !it.hasSelection }

    fun swipeForward(): ReaderRobot = dragPage(DRAG_FAR_FRACTION, DRAG_NEAR_FRACTION)

    fun swipeBackward(): ReaderRobot = dragPage(DRAG_NEAR_FRACTION, DRAG_FAR_FRACTION)

    private fun dragPage(fromFraction: Float, toFraction: Float): ReaderRobot {
        val width = device.displayWidth.toFloat()
        val height = device.displayHeight.toFloat()
        val state = readState()
        val (from, to) = if (state.swipeVertical == false && state.readingDirectionRtl == true) {
            toFraction to fromFraction
        } else {
            fromFraction to toFraction
        }
        val start = if (state.swipeVertical == false) {
            PointF(width * horizontalFraction(from, to), height / 2f)
        } else {
            PointF(width / 2f, height * from)
        }
        val end = if (state.swipeVertical == false) {
            PointF(width * horizontalFraction(to, from), height / 2f)
        } else {
            PointF(width / 2f, height * to)
        }
        RawInput.dragWithoutFling(
            start,
            end,
        )
        awaitMotionSettled()
        return this
    }

    private fun horizontalFraction(value: Float, other: Float): Float = when {
        value > other -> HORIZONTAL_DRAG_FAR_FRACTION
        value < other -> HORIZONTAL_DRAG_NEAR_FRACTION
        else -> value
    }

    fun pagePointAtFraction(xFraction: Float, yFraction: Float): Point {
        val pageRect = readState().pageRect
            ?: throw ConditionTimeoutException("The reader state hook reports no page rectangle")
        val bounds = pdfViewBounds()
        return Point(
            bounds.left + (pageRect.left + pageRect.width() * xFraction).toInt(),
            bounds.top + (pageRect.top + pageRect.height() * yFraction).toInt(),
        )
    }

    fun tapAtPageFraction(xFraction: Float, yFraction: Float, syncMainThread: Boolean = true): Point {
        val point = pagePointAtFraction(xFraction, yFraction)
        TestEnvironment.uiDevice.click(point.x, point.y)
        TestEnvironment.uiDevice.waitForIdle()
        if (syncMainThread) TestEnvironment.waitForIdleSync()
        return point
    }

    fun assertPageCount(expected: Int) {
        assertEquals("reader page count", expected as Int?, readState().pageCount)
    }

    fun assertFullyRendered() {
        val state = readState()
        assertTrue("Expected an idle full quality render but read: $state", state.isFullyRendered)
    }

    companion object {

        const val DRAG_FAR_FRACTION = 0.72f
        const val DRAG_NEAR_FRACTION = 0.32f
        const val HORIZONTAL_DRAG_FAR_FRACTION = 0.88f
        const val HORIZONTAL_DRAG_NEAR_FRACTION = 0.12f

        fun viewIntent(uri: Uri, mimeType: String = "application/pdf"): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                setClass(TestEnvironment.targetContext, MainActivity::class.java)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        fun viewIntent(file: File): Intent = viewIntent(Uri.fromFile(file))

        fun pdfViewBounds(): Rect = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No resumed reader activity")
            val view = activity.findViewById<View>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView")
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
    }
}
