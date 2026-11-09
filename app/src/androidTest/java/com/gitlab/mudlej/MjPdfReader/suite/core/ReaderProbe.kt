// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.helper.widget.Flow
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import java.io.File
import java.lang.ref.WeakReference

object ReaderProbe {

    const val TAP_HOLD_MS = 60L

    private val liveStages = listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)

    fun liveReaders(): List<MainActivity> = TestEnvironment.onMainThread {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        liveStages
            .flatMap { stage -> monitor.getActivitiesInStage(stage) }
            .filterIsInstance<MainActivity>()
            .distinct()
    }

    fun liveReaderOrNull(): MainActivity? = liveReaders().firstOrNull()

    fun resumedReaderOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    fun requireResumedReader(): MainActivity = resumedReaderOrNull()
        ?: throw ConditionTimeoutException("No MainActivity is resumed")

    fun awaitResumedReader(timeoutMs: Long = Await.LONG_TIMEOUT_MS): MainActivity =
        Await.untilNotNull("a reader activity is resumed", timeoutMs) { resumedReaderOrNull() }

    fun resumedReaderIdentity(): Int = System.identityHashCode(requireResumedReader())

    fun viewModel(activity: MainActivity = requireResumedReader()): ReaderViewModel =
        TestEnvironment.onMainThread { ViewModelProvider(activity)[ReaderViewModel::class.java] }

    fun <T> onReader(read: (MainActivity) -> T): T = TestEnvironment.onMainThread { read(requireResumedReader()) }

    fun <T> onReaderView(viewId: Int, read: (View) -> T): T = onReader { activity ->
        val view = activity.findViewById<View>(viewId)
            ?: throw ConditionTimeoutException("View $viewId is not in the resumed reader")
        read(view)
    }

    fun visibility(viewId: Int): Int = onReaderView(viewId) { it.visibility }

    fun awaitVisibility(viewId: Int, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "view $viewId reaches visibility $expected",
            timeoutMs = timeoutMs,
            read = { runCatching { visibility(viewId) }.getOrDefault(View.GONE) },
        ) { it == expected }
    }

    fun assertVisibilityHolds(
        viewId: Int,
        expected: Int,
        stableForMs: Long = 500L,
    ) {
        Await.assertThroughout(
            condition = "view $viewId keeps visibility $expected",
            windowMs = stableForMs,
        ) { visibility(viewId) == expected }
    }

    fun actionBarTitle(): String = onReader { activity ->
        activity.findViewById<android.widget.TextView>(R.id.actionbarTitle).text.toString()
    }

    fun keepScreenOnFlagSet(): Boolean = onReader { activity ->
        val flags = activity.window.attributes.flags
        flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }

    fun systemBarsHidden(): Boolean = onReader { activity ->
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        insets != null &&
            !insets.isVisible(WindowInsetsCompat.Type.statusBars()) &&
            !insets.isVisible(WindowInsetsCompat.Type.navigationBars())
    }

    fun canvasCentreOnScreen(): PointF = onReaderView(R.id.pdfView) { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    fun canvasPointOnScreen(xFraction: Float, yFraction: Float): PointF = onReaderView(R.id.pdfView) { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        PointF(location[0] + view.width * xFraction, location[1] + view.height * yFraction)
    }

    fun tapCanvas() {
        RawInput.pressAndHold(canvasCentreOnScreen(), TAP_HOLD_MS)
    }

    fun journalFileFor(uri: Uri): File =
        File(File(TestEnvironment.targetContext.filesDir, "annotations"), "${SourceKey.of(uri)}.journal")

    fun documentUriOfReader(): Uri = viewModel().doc.uri
        ?: throw ConditionTimeoutException("The reader holds no document URI yet")

    fun readerTaskCount(): Int {
        val manager = TestEnvironment.targetContext.getSystemService(ActivityManager::class.java)
        return manager.appTasks.count { task ->
            runCatching { task.taskInfo.baseIntent.component?.className }.getOrNull() == MainActivity::class.java.name
        }
    }

    fun finishAndRemoveReaderTasks() {
        val manager = TestEnvironment.targetContext.getSystemService(ActivityManager::class.java)
        manager.appTasks.forEach { task ->
            runCatching {
                if (task.taskInfo.baseIntent.component?.className == MainActivity::class.java.name) {
                    task.finishAndRemoveTask()
                }
            }
        }
    }

    fun finishLiveReaders() {
        TestEnvironment.onMainThread {
            liveReaders().filterNot { it.isFinishing }.forEach { it.finish() }
        }
    }

    fun assertCollected(reference: WeakReference<out Activity>, what: String) {
        com.gitlab.mudlej.MjPdfReader.suite.memory.Heap.clearAndroidPieAccessibilityPool()
        try {
            Await.untilCollected(reference)
        } catch (timeout: ConditionTimeoutException) {
            throw AssertionError(
                "$what was still reachable after five seconds of forced collection, which is the retention " +
                    "this test exists to catch. ${timeout.message}"
            )
        }
    }

    fun swatchIdsOnSelectionCard(): List<Int> = onReaderView(R.id.textSelectionSwatchFlow) { view ->
        (view as Flow).referencedIds.toList()
    }

    fun tapFirstHighlightSwatch() {
        val ids = swatchIdsOnSelectionCard()
        if (ids.isEmpty()) {
            throw ConditionTimeoutException(
                "The selection action card carries no highlight swatches, so no highlight can be created"
            )
        }
        val centre = onReader { activity ->
            val swatch = activity.findViewById<View>(ids.first())
                ?: throw ConditionTimeoutException("Swatch ${ids.first()} is not in the resumed reader")
            val location = IntArray(2)
            swatch.getLocationOnScreen(location)
            PointF(location[0] + swatch.width / 2f, location[1] + swatch.height / 2f)
        }
        RawInput.pressAndHold(centre, TAP_HOLD_MS)
    }

    fun startReader(intent: Intent) {
        TestEnvironment.targetContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
