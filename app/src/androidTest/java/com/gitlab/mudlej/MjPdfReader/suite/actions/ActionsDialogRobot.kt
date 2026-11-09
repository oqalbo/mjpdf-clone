// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import java.util.regex.Pattern
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

class ActionsDialogRobot {

    fun open() {
        ToolbarItems.tapActions()
        awaitShown()
    }

    fun isShown(): Boolean = hasTile(sentinelLabel())

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        try {
            Await.until(
                "the reader actions dialog is shown, probed by its always present \"${sentinelLabel()}\" tile",
                timeoutMs,
            ) { isShown() }
        } catch (timeout: ConditionTimeoutException) {
            throw ConditionTimeoutException("${timeout.message}. ${describeDialogState()}")
        }
    }

    fun describeDialogState(): String {
        val root = runCatching { dialogRoot() }
        val labels = runCatching { tileLabels() }
        val sentinelIgnoringDisplay = runCatching {
            onView(withContentDescription(sentinelLabel())).inRoot(isDialog()).check(matches(isEnabled()))
        }
        return "dialog root ${describeOutcome(root) { "matched" }}, " +
            "tiles ${describeOutcome(labels) { it.toString() }}, " +
            "sentinel ignoring isDisplayed ${describeOutcome(sentinelIgnoringDisplay) { "present" }}"
    }

    private fun <T> describeOutcome(outcome: Result<T>, onSuccess: (T) -> String): String =
        outcome.fold(onSuccess) { error ->
            "${error::class.java.simpleName}: ${error.message?.lineSequence()?.firstOrNull().orEmpty().take(160)}"
        }

    fun assertNotShown(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the reader actions dialog stays closed",
            windowMs = windowMs,
        ) { !isShown() }
    }

    fun awaitDismissed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the reader actions dialog is dismissed", timeoutMs) { !isShown() }
    }

    fun dismiss() {
        pressBack()
        awaitDismissed()
    }

    fun hasTile(label: String): Boolean = anyTileNode() && runCatching {
        tileLabels().contains(label)
    }.getOrDefault(false)

    fun awaitTile(label: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the actions dialog holds a tile labelled \"$label\"", timeoutMs) {
            hasTile(label)
        }
    }

    fun assertNoTile(label: String, windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the actions dialog holds no tile labelled \"$label\"",
            windowMs = windowMs,
        ) { !hasTile(label) }
    }

    fun tap(label: String) {
        tileInteraction(label).perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
    }

    fun tileView(label: String): View {
        var captured: View? = null
        tileInteraction(label).perform(scrollTo())
        tileInteraction(label).check { view, error ->
            if (error != null) throw error
            captured = view
        }
        return captured ?: throw ConditionTimeoutException("No actions dialog tile labelled \"$label\"")
    }

    fun tileIsClickable(label: String): Boolean {
        val tile = tileView(label)
        return TestEnvironment.onMainThread { tile.isClickable }
    }

    fun tileAlpha(label: String): Float {
        val tile = tileView(label)
        return TestEnvironment.onMainThread { tile.alpha }
    }

    fun tileIsEnabled(label: String): Boolean {
        val tile = tileView(label)
        return TestEnvironment.onMainThread { tile.isClickable && tile.alpha == FULL_ALPHA }
    }

    fun tileBounds(label: String): Rect {
        val tile = tileView(label)
        return TestEnvironment.onMainThread {
            val location = IntArray(2)
            tile.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + tile.width, location[1] + tile.height)
        }
    }

    fun tileLabels(): List<String> = sections().flatten()

    fun sections(): List<List<String>> {
        val root = dialogRoot()
        return TestEnvironment.onMainThread {
            val grids = LinkedHashMap<View, MutableList<String>>()
            collectTiles(root) { tile, grid ->
                tile.contentDescription?.toString()?.let { grids.getOrPut(grid) { mutableListOf() }.add(it) }
            }
            grids.values.map { it.toList() }
        }
    }

    fun dialogDecorBounds(): Rect {
        val root = dialogRoot()
        return TestEnvironment.onMainThread {
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + root.width, location[1] + root.height)
        }
    }

    fun dialogRoot(): View {
        if (!anyTileNode()) {
            throw ConditionTimeoutException(
                "No reader actions dialog is on screen, the node tree holds no action tile at all. " +
                    "Membership of a named tile is read from the view tree instead, because the dialog " +
                    "scrolls and a tile below the fold is absent from the node tree while still present"
            )
        }
        return Dialogs.dialogRoot()
    }

    private fun collectTiles(root: View, onTile: (View, View) -> Unit) {
        if (root !is ViewGroup) return
        if (isTile(root)) {
            val grid = root.parent?.let { row -> (row as? View)?.parent as? View }
            if (grid != null) onTile(root, grid)
            return
        }
        for (index in 0 until root.childCount) {
            collectTiles(root.getChildAt(index), onTile)
        }
    }

    private fun isTile(view: ViewGroup): Boolean =
        view.contentDescription != null &&
            view.childCount == TILE_CHILD_COUNT &&
            view.getChildAt(0) is ImageView &&
            view.getChildAt(1) is TextView

    private fun tileInteraction(label: String) =
        onView(withContentDescription(label)).inRoot(isDialog())

    private fun anyTileNode(): Boolean = TestEnvironment.uiDevice.hasObject(
        By.clazz(TILE_CLASS).desc(Pattern.compile(".+")).pkg(TestEnvironment.targetPackage)
    )

    private fun sentinelLabel(): String = string(R.string.settings)

    private fun string(resId: Int): String = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivity()?.getString(resId)
            ?: TestEnvironment.targetContext.getString(resId)
    }

    companion object {

        const val FULL_ALPHA = 1.0f
        const val DISABLED_ALPHA = 0.38f
        const val ABSENCE_WINDOW_MS = 1_000L
        const val TILE_CHILD_COUNT = 2
        const val TILE_CLASS = "android.widget.LinearLayout"
    }
}
