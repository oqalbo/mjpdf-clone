// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeItem
import kotlin.math.abs

class HomeCell internal constructor(
    private val page: HomePage,
    val item: HomeItem,
) {

    fun title(): String = read { it.requireView<TextView>(R.id.title).text.toString() }

    fun alpha(): Float = read { it.alpha }

    fun isChecked(): Boolean = read { (card(it) as Checkable).isChecked }

    fun isProgressBadgeShown(): Boolean = read { it.isShownById(R.id.progressBadge) }

    fun isProgressBarShown(): Boolean = read { it.isShownById(R.id.progress) }

    fun progressPercentText(): String? = read { view ->
        view.findViewById<TextView>(R.id.progressPercent)?.text?.toString()
    }

    fun progressBarValue(): Int = read { view ->
        view.requireView<LinearProgressIndicator>(R.id.progress).progress
    }

    fun isLastOpenedShown(): Boolean = read { it.isShownById(R.id.lastOpenedLabel) }

    fun badges(): List<String> = read { view ->
        val container = view.findViewById<ViewGroup>(R.id.metaBadges)
            ?: throw AssertionError(
                "This cell has no metaBadges container, which means it is a grid cell. " +
                    "Meta badges only exist on list rows, so seed homeViewMode as LIST first."
            )
        if (container.visibility != View.VISIBLE) return@read emptyList()
        (0 until container.childCount).mapNotNull { (container.getChildAt(it) as? TextView)?.text?.toString() }
    }

    fun coverBitmapOrNull(): Bitmap? = read { view ->
        (view.requireView<ImageView>(R.id.cover).drawable as? BitmapDrawable)?.bitmap
    }

    fun awaitCover(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Bitmap =
        Await.untilNotNull("the cover of \"${item.title}\" is bound", timeoutMs) { coverBitmapOrNull() }

    fun awaitNoCover(windowMs: Long = NO_COVER_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the cover of \"${item.title}\" stays unbound",
            windowMs = windowMs,
        ) { coverBitmapOrNull() == null }
    }

    fun coverCentrePixel(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int {
        val bitmap = awaitCover(timeoutMs)
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }

    fun assertCoverCentreIs(expected: Int, tolerance: Int = COLOUR_TOLERANCE) {
        val actual = coverCentrePixel()
        val distance = maxOf(
            abs(Color.red(actual) - Color.red(expected)),
            abs(Color.green(actual) - Color.green(expected)),
            abs(Color.blue(actual) - Color.blue(expected)),
        )
        if (distance > tolerance) {
            throw AssertionError(
                "The cover of \"${item.title}\" samples as ${hex(actual)} at its centre, " +
                    "against an expected ${hex(expected)} with a tolerance of $tolerance per channel"
            )
        }
    }

    fun click() {
        Taps.tapCentreOf(read { card(it) })
    }

    fun longPress() {
        Taps.longPressCentreOf(read { card(it) })
    }

    fun performClickForSetupAndAwaitChecked(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        activateForSetup("performClick", timeoutMs) { it.performClick() }
    }

    fun performLongClickForSetupAndAwaitChecked(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        activateForSetup("performLongClick", timeoutMs) { it.performLongClick() }
    }

    fun tapOptions() {
        val accepted = read { cell ->
            val button = cell.requireView<View>(R.id.optionsButton)
            button.isShown && button.isEnabled && button.performClick()
        }
        if (!accepted) {
            throw ConditionTimeoutException(
                "The shown options button for \"${item.title}\" did not handle performClick"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun <T> read(block: (View) -> T): T {
        val view = page.itemViewOf(item.stableKey)
        return TestEnvironment.onMainThread { block(view) }
    }

    private fun activateForSetup(
        action: String,
        timeoutMs: Long,
        activate: (View) -> Boolean,
    ) {
        Await.until("the shown cell for \"${item.title}\" accepts $action", Await.SHORT_TIMEOUT_MS) {
            val target = read { card(it) }
            TestEnvironment.onMainThread { target.isShown && activate(target) }
        }
        Await.until("the cell for \"${item.title}\" becomes selected after $action", timeoutMs) {
            runCatching { isChecked() }.getOrDefault(false)
        }
    }

    private fun card(view: View): View =
        view.findViewById(R.id.coverCard)
            ?: view.findViewById(R.id.listCard)
            ?: throw ConditionTimeoutException(
                "The cell for \"${item.title}\" holds neither coverCard nor listCard, so it is neither a grid " +
                    "cell nor a list row"
            )

    private fun <T : View> View.requireView(viewId: Int): T = findViewById<T>(viewId)
        ?: throw ConditionTimeoutException(
            "The cell for \"${item.title}\" holds no view with id " +
                TestEnvironment.targetContext.resources.getResourceEntryName(viewId)
        )

    private fun View.isShownById(viewId: Int): Boolean =
        findViewById<View>(viewId)?.visibility == View.VISIBLE

    private fun hex(colour: Int): String = String.format("#%08X", colour)

    private companion object {
        const val COLOUR_TOLERANCE = 12
        const val NO_COVER_WINDOW_MS = 1_500L
    }
}
