// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object BaselineCapture {

    fun screen(): Bitmap {
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()

        var previous = redrawAndCapture()
        repeat(MAX_STABILITY_ATTEMPTS - 1) {
            val current = redrawAndCapture()
            if (PixelComparison.areByteIdentical(previous, current)) {
                previous.recycle()
                return current
            }
            previous.recycle()
            previous = current
        }

        previous.recycle()
        throw BaselineGateFailure(
            "The screen did not produce the same pixels after $MAX_STABILITY_ATTEMPTS consecutive forced " +
                "redraws. Rendering may report idle before its frame is committed, so an unstable frame is " +
                "refused rather than compared or recorded."
        )
    }

    fun view(@IdRes viewId: Int): Bitmap {
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
        return TestEnvironment.onMainThread {
            val target = requireView(viewId)
            drawToBitmap(target, viewId)
        }
    }

    fun crop(source: Bitmap, region: Rect): Bitmap {
        val bounded = Rect(
            region.left.coerceIn(0, source.width),
            region.top.coerceIn(0, source.height),
            region.right.coerceIn(0, source.width),
            region.bottom.coerceIn(0, source.height),
        )
        if (bounded.width() <= 0 || bounded.height() <= 0) {
            throw BaselineGateFailure(
                "The crop region $region is empty once clamped to the ${source.width}x${source.height} " +
                    "capture, so there would be nothing to compare"
            )
        }
        return Bitmap.createBitmap(source, bounded.left, bounded.top, bounded.width(), bounded.height())
    }

    fun systemStatusBarInset(): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException(
                "No activity is resumed, so the system status bar inset cannot be read"
            )
        ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
    }

    private fun drawToBitmap(target: View, @IdRes viewId: Int): Bitmap {
        if (target.width <= 0 || target.height <= 0) {
            throw BaselineGateFailure(
                "View ${name(viewId)} has no size on screen (${target.width}x${target.height}), so a capture " +
                    "of it would be empty"
            )
        }
        val bitmap = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        target.draw(Canvas(bitmap))
        return bitmap
    }

    private fun redrawAndCapture(): Bitmap {
        val drawCompleted = CountDownLatch(1)
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity()
                ?: throw ConditionTimeoutException("No activity is resumed, so the screen cannot be captured")
            val root = activity.window.decorView
            val observer = root.viewTreeObserver
            val callbackPosted = AtomicBoolean(false)
            lateinit var listener: ViewTreeObserver.OnDrawListener
            listener = ViewTreeObserver.OnDrawListener {
                if (callbackPosted.compareAndSet(false, true)) {
                    root.post {
                        if (observer.isAlive) observer.removeOnDrawListener(listener)
                        drawCompleted.countDown()
                    }
                }
            }
            observer.addOnDrawListener(listener)
            activity.findViewById<View>(R.id.pdfView)?.invalidate()
            root.invalidate()
        }
        if (!drawCompleted.await(DRAW_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw BaselineGateFailure(
                "The resumed activity did not complete a forced redraw within $DRAW_TIMEOUT_MS ms, so the " +
                    "screen cannot be proven stable enough for a golden comparison."
            )
        }
        TestEnvironment.waitForIdleSync()
        val captured = Await.untilNotNull(
            "UiAutomation returns a screenshot bitmap",
            Await.DEFAULT_TIMEOUT_MS,
        ) {
            TestEnvironment.instrumentation.uiAutomation.takeScreenshot()
        }
        return BitmapFiles.toArgb(captured).also { converted ->
            if (converted !== captured) captured.recycle()
        }
    }

    private fun requireView(@IdRes viewId: Int): View {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed, so ${name(viewId)} cannot be captured")
        return activity.findViewById(viewId)
            ?: throw ConditionTimeoutException(
                "The resumed ${activity.javaClass.simpleName} carries no view ${name(viewId)}"
            )
    }

    private fun name(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())

    private const val MAX_STABILITY_ATTEMPTS = 4
    private const val DRAW_TIMEOUT_MS = 5_000L
}
