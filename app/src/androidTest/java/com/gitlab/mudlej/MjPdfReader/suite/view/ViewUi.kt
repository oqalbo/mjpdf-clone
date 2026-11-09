// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.google.android.material.button.MaterialButton

object ViewUi {

    const val ICON_PROBE_SIZE_PX = 48
    const val ICON_GRID = 12
    const val ICON_ALPHA_FLOOR = 128
    const val ICON_MIN_AGREEMENT = 0.95f

    fun reader(): MainActivity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
        ?: throw ConditionTimeoutException("No MainActivity is resumed")

    fun <T> onReader(read: (MainActivity) -> T): T = TestEnvironment.onMainThread { read(reader()) }

    fun <T> onView(@IdRes viewId: Int, read: (View) -> T): T = onReader { activity ->
        val target = activity.findViewById<View>(viewId)
            ?: throw ConditionTimeoutException("View ${name(viewId)} is not in the resumed reader")
        read(target)
    }

    fun visibility(@IdRes viewId: Int): Int = onView(viewId) { it.visibility }

    fun awaitVisibility(@IdRes viewId: Int, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "${name(viewId)} reaches visibility ${visibilityName(expected)}",
            timeoutMs = timeoutMs,
            read = { visibility(viewId) },
        ) { it == expected }
    }

    fun assertVisibility(message: String, @IdRes viewId: Int, expected: Int) {
        val actual = visibility(viewId)
        if (actual != expected) {
            throw AssertionError(
                "$message: ${name(viewId)} must be ${visibilityName(expected)} but is ${visibilityName(actual)}"
            )
        }
    }

    fun textOf(@IdRes viewId: Int): String = onView(viewId) { (it as android.widget.TextView).text.toString() }

    fun isChecked(@IdRes viewId: Int): Boolean = onView(viewId) { (it as MaterialButton).isChecked }

    fun iconMatches(@IdRes viewId: Int, @DrawableRes drawableRes: Int): Boolean =
        iconAgreement(viewId, drawableRes) >= ICON_MIN_AGREEMENT

    fun iconAgreement(@IdRes viewId: Int, @DrawableRes drawableRes: Int): Float {
        val expected = TestEnvironment.onMainThread {
            AppCompatResources.getDrawable(reader(), drawableRes)
        } ?: return 0f
        val actual = onView(viewId) { (it as MaterialButton).icon } ?: return 0f
        return agreementOf(occupancyOf(expected), occupancyOf(actual))
    }

    fun awaitIcon(@IdRes viewId: Int, @DrawableRes drawableRes: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        try {
            Await.until("${name(viewId)} shows the ${name(drawableRes)} icon", timeoutMs) {
                iconMatches(viewId, drawableRes)
            }
        } catch (timeout: ConditionTimeoutException) {
            throw ConditionTimeoutException("${timeout.message}, ${iconDiagnosis(viewId, drawableRes)}")
        }
    }

    fun iconDiagnosis(@IdRes viewId: Int, @DrawableRes drawableRes: Int): String {
        val expected = runCatching {
            val drawable = TestEnvironment.onMainThread { AppCompatResources.getDrawable(reader(), drawableRes) }
            drawable?.let { signatureOf(it) }
        }.getOrNull() ?: "unavailable"
        val actual = runCatching {
            onView(viewId) { (it as MaterialButton).icon }?.let { signatureOf(it) }
        }.getOrNull() ?: "unavailable"
        val agreement = runCatching { iconAgreement(viewId, drawableRes) }.getOrNull()
        return "${name(drawableRes)} renders as [$expected], the button icon renders as [$actual], " +
            "and the two agree on ${agreement ?: "an unknown fraction"} of the probe cells"
    }

    private fun occupancyOf(drawable: Drawable): BooleanArray {
        val bitmap = renderToBitmap(drawable)
        val cell = ICON_PROBE_SIZE_PX / ICON_GRID
        val cells = BooleanArray(ICON_GRID * ICON_GRID)
        for (row in 0 until ICON_GRID) {
            for (column in 0 until ICON_GRID) {
                var covered = 0
                for (y in row * cell until (row + 1) * cell) {
                    for (x in column * cell until (column + 1) * cell) {
                        if (Color.alpha(bitmap.getPixel(x, y)) >= ICON_ALPHA_FLOOR) covered++
                    }
                }
                cells[row * ICON_GRID + column] = covered * 2 >= cell * cell
            }
        }
        return cells
    }

    private fun agreementOf(expected: BooleanArray, actual: BooleanArray): Float {
        val matching = expected.indices.count { expected[it] == actual[it] }
        return matching.toFloat() / expected.size
    }

    private fun signatureOf(drawable: Drawable): String {
        val bitmap = renderToBitmap(drawable)
        var visible = 0
        var minX = ICON_PROBE_SIZE_PX
        var minY = ICON_PROBE_SIZE_PX
        var maxX = -1
        var maxY = -1
        for (y in 0 until ICON_PROBE_SIZE_PX) {
            for (x in 0 until ICON_PROBE_SIZE_PX) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                visible++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        val shape = if (maxX < 0) "no visible pixels" else "$visible px in ($minX,$minY)-($maxX,$maxY)"
        return "${drawable::class.java.simpleName}, $shape"
    }

    fun actionBarShowing(): Boolean = TestEnvironment.onMainThread {
        (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? AppCompatActivity)
            ?.supportActionBar?.isShowing == true
    }

    fun awaitActionBar(showing: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the action bar reports showing=$showing", timeoutMs) { actionBarShowing() == showing }
    }

    fun systemBarVisible(type: Int): Boolean = onReader { activity ->
        ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(type) ?: true
    }

    fun statusBarsVisible(): Boolean = systemBarVisible(WindowInsetsCompat.Type.statusBars())

    fun navigationBarsVisible(): Boolean = systemBarVisible(WindowInsetsCompat.Type.navigationBars())

    fun statusBarInsetHeight(): Int = onReader { activity ->
        ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    }

    fun awaitSystemBarsHidden(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the status and navigation bars report not visible", timeoutMs) {
            !statusBarsVisible() && !navigationBarsVisible()
        }
    }

    fun pdfViewBackgroundColor(): Int = onView(R.id.pdfView) { (it.background as ColorDrawable).color }

    fun awaitPdfViewBackgroundColor(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the PDFView background settles on ${colorName(expected)}",
            timeoutMs = timeoutMs,
            read = { pdfViewBackgroundColor() },
        ) { it == expected }
    }

    fun screenBrightness(): Float = onReader { it.window.attributes.screenBrightness }

    fun keepScreenOnFlag(): Int = onReader {
        it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    }

    fun requestedOrientation(): Int = onReader { it.requestedOrientation }

    fun configurationOrientation(): Int = onReader { it.resources.configuration.orientation }

    fun uiModeNight(): Boolean = onReader {
        val mask = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun hasWindowFocus(): Boolean = onReader { it.hasWindowFocus() }

    fun canvasCentre(): PointF = onView(R.id.pdfView) { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    fun sizeOf(@IdRes viewId: Int): Point = onView(viewId) { Point(it.width, it.height) }

    fun boundsOnScreen(@IdRes viewId: Int): android.graphics.Rect = onView(viewId) { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        android.graphics.Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height,
        )
    }

    fun seekBarProgress(@IdRes viewId: Int): Int = onView(viewId) { (it as android.widget.SeekBar).progress }

    fun scrollHandleOrNull(): View? = onView(R.id.pdfView) { pdfView ->
        (pdfView as ViewGroup).children()
            .firstOrNull { it.contentDescription?.toString() == DefaultScrollHandle.DEBUG_HANDLE_DESCRIPTION }
    }

    fun scrollHandleY(): Float = TestEnvironment.onMainThread {
        scrollHandleOrNull()?.y
            ?: throw ConditionTimeoutException("The scroll handle is not attached to the PDFView")
    }

    fun scrollHandleBoundsOnScreen(): android.graphics.Rect = TestEnvironment.onMainThread {
        val handle = scrollHandleOrNull()
            ?: throw ConditionTimeoutException("The scroll handle is not attached to the PDFView")
        val location = IntArray(2)
        handle.getLocationOnScreen(location)
        android.graphics.Rect(
            location[0],
            location[1],
            location[0] + handle.width,
            location[1] + handle.height,
        )
    }

    fun scrollHandleVisible(): Boolean = TestEnvironment.onMainThread {
        scrollHandleOrNull()?.visibility == View.VISIBLE
    }

    fun visibleOverlayButtonIds(): List<String> = onView(R.id.fullScreenButtonsList) { list ->
        (list as ViewGroup).children()
            .filter { it.visibility == View.VISIBLE }
            .filter { it.id != View.NO_ID }
            .map { name(it.id) }
    }

    fun visibleOverlayButtonDescriptions(): List<String> = onView(R.id.fullScreenButtonsList) { list ->
        (list as ViewGroup).children()
            .filter { it.visibility == View.VISIBLE }
            .map { it.contentDescription?.toString().orEmpty() }
    }

    fun visibleOverlayDynamicDescriptions(): List<String> = onView(R.id.fullScreenButtonsList) { list ->
        (list as ViewGroup).children()
            .filter { it.visibility == View.VISIBLE }
            .filter { it.id == View.NO_ID }
            .map { it.contentDescription?.toString().orEmpty() }
    }

    fun overlayButtonWithDescription(description: String): MaterialButton? =
        onView(R.id.fullScreenButtonsList) { list ->
            (list as ViewGroup).children()
                .filterIsInstance<MaterialButton>()
                .firstOrNull { it.contentDescription?.toString() == description }
        }

    fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    fun string(resId: Int, vararg formatArgs: Any): String =
        TestEnvironment.targetContext.getString(resId, *formatArgs)

    fun name(@IdRes resId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(resId) }
            .getOrDefault(resId.toString())

    fun visibilityName(value: Int): String = when (value) {
        View.VISIBLE -> "VISIBLE"
        View.INVISIBLE -> "INVISIBLE"
        View.GONE -> "GONE"
        else -> value.toString()
    }

    fun colorName(value: Int): String = "0x" + java.lang.Integer.toHexString(value).uppercase()

    fun finishLiveReaders() {
        TestEnvironment.onMainThread {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .plus(
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.PAUSED)
                )
                .plus(
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.STOPPED)
                )
                .filterIsInstance<Activity>()
                .filter { it is MainActivity && !it.isFinishing }
                .forEach { it.finish() }
        }
    }

    private fun ViewGroup.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun renderToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_PROBE_SIZE_PX, ICON_PROBE_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        copy.setBounds(0, 0, ICON_PROBE_SIZE_PX, ICON_PROBE_SIZE_PX)
        copy.setTintList(null)
        copy.draw(canvas)
        return bitmap
    }
}
