// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object SelectionCard {

    val ACTION_BUTTONS = listOf(
        R.id.textSelectionCopyButton,
        R.id.textSelectionShareButton,
        R.id.textSelectionSearchWebButton,
        R.id.textSelectionNoteButton,
        R.id.textSelectionTranslateButton,
        R.id.textSelectionDiscardButton,
    )

    fun visibility(): Int? = TestEnvironment.onMainThread { card()?.visibility }

    fun isShown(): Boolean = visibility() == View.VISIBLE

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the text selection action card is visible", timeoutMs) { isShown() }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the text selection action card is gone", timeoutMs) { visibility() == View.GONE }
    }

    fun assertStaysGone(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the text selection action card stays gone",
            windowMs = windowMs,
        ) { visibility() == View.GONE }
    }

    fun visibilityOf(@IdRes viewId: Int): Int = TestEnvironment.onMainThread {
        requireView<View>(viewId).visibility
    }

    fun isVisible(@IdRes viewId: Int): Boolean = visibilityOf(viewId) == View.VISIBLE

    fun contentDescriptionOf(@IdRes viewId: Int): String? = TestEnvironment.onMainThread {
        requireView<View>(viewId).contentDescription?.toString()
    }

    fun verticalBias(): Float = TestEnvironment.onMainThread {
        val view = card() ?: throw ConditionTimeoutException("The selection action card is not in the resumed activity")
        (view.layoutParams as ConstraintLayout.LayoutParams).verticalBias
    }

    fun boundsOnScreen(): Rect = TestEnvironment.onMainThread {
        val view = card() ?: throw ConditionTimeoutException("The selection action card is not in the resumed activity")
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun swatchCount(): Int = TestEnvironment.onMainThread {
        requireView<Flow>(R.id.textSelectionSwatchFlow).referencedIds.size
    }

    fun tapSwatch(index: Int) {
        val point = TestEnvironment.onMainThread {
            val swatch = swatches().getOrNull(index)
                ?: throw ConditionTimeoutException(
                    "The action card carries no highlight swatch at index $index. " +
                        "InlineAnnotationActionController builds one clickable container per configured colour, " +
                        "so an empty set means the card was read before rebuildHighlightSwatches ran."
                )
            centreOf(swatch)
        }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    fun tap(@IdRes viewId: Int) {
        val point = TestEnvironment.onMainThread { centreOf(requireView(viewId)) }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    private fun swatches(): List<View> {
        val content = contentOrNull()
            ?: throw ConditionTimeoutException("The selection action card has no content container")
        val referenced = (content.findViewById<Flow>(R.id.textSelectionSwatchFlow))?.referencedIds ?: IntArray(0)
        val swatches = mutableListOf<View>()
        for (id in referenced) {
            val view = content.findViewById<View>(id) ?: continue
            if (view is ViewGroup && view.childCount == 1 && view.getChildAt(0) !is ImageView) {
                swatches.add(view)
            }
        }
        return swatches
    }

    private fun centreOf(view: View): PointF {
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException(
                "The view ${view.javaClass.simpleName} has no size on screen, so it cannot be tapped"
            )
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    private fun card(): View? = TestEnvironment.resumedActivity()?.findViewById(R.id.textSelectionActionCard)

    private fun contentOrNull(): ViewGroup? =
        TestEnvironment.resumedActivity()?.findViewById(R.id.textSelectionActionContent)

    private fun <T : View> requireView(@IdRes viewId: Int): T {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No activity is resumed, so the selection card cannot be read")
        return activity.findViewById(viewId)
            ?: throw ConditionTimeoutException(
                "View ${TestEnvironment.targetContext.resources.getResourceEntryName(viewId)} is not in the " +
                    "resumed activity"
            )
    }

    const val ABSENCE_WINDOW_MS = 1_500L
}
