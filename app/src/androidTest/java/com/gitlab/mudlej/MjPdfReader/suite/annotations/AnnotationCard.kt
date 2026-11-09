// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.graphics.PointF
import android.view.View
import androidx.constraintlayout.helper.widget.Flow
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette

object AnnotationCard {

    fun isShown(): Boolean = TestEnvironment.onMainThread {
        card()?.visibility == View.VISIBLE
    }

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the inline annotation action card is visible", timeoutMs) { isShown() }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the inline annotation action card leaves the screen", timeoutMs) { !isShown() }
    }

    fun assertStaysGone(windowMs: Long = AnnotationJournalFile.STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the inline annotation action card never appears",
            windowMs = windowMs,
        ) { !isShown() }
    }

    fun isButtonShown(viewId: Int): Boolean = TestEnvironment.onMainThread {
        buttonOrNull(viewId)?.visibility == View.VISIBLE
    }

    fun awaitButtonShown(viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the card button ${resourceName(viewId)} is visible", timeoutMs) { isButtonShown(viewId) }
    }

    fun buttonDescription(viewId: Int): String? = TestEnvironment.onMainThread {
        buttonOrNull(viewId)?.contentDescription?.toString()
    }

    fun swatchDescriptions(): List<String> = TestEnvironment.onMainThread {
        swatchViews().map { it.contentDescription?.toString().orEmpty() }
    }

    fun swatchCount(): Int = TestEnvironment.onMainThread { swatchViews().size }

    fun awaitSwatches(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<String> {
        Await.untilValue(
            "the card offers $expected highlight swatches",
            timeoutMs,
            read = { swatchCount() },
        ) { it == expected }
        return swatchDescriptions()
    }

    fun tapSwatch(index: Int) {
        val point = TestEnvironment.onMainThread { centreOf(requireSwatch(index)) }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    fun tapSwatch(palette: HighlightPalette) {
        tapSwatch(indexOfSwatch(descriptionOf(palette)))
    }

    fun clickSwatchWithoutTouch(palette: HighlightPalette) {
        clickSwatchWithoutTouch(indexOfSwatch(descriptionOf(palette)))
    }

    fun clickSwatchWithoutTouch(index: Int) {
        val accepted = TestEnvironment.onMainThread { requireSwatch(index).performClick() }
        if (!accepted) {
            throw AssertionError(
                "Highlight swatch $index refused the click, so no colour was applied and the branch under " +
                    "test never ran"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    fun tapButton(viewId: Int) {
        val point = TestEnvironment.onMainThread { centreOf(requireButton(viewId)) }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    fun clickButtonWithoutTouch(viewId: Int) {
        val accepted = TestEnvironment.onMainThread { requireButton(viewId).performClick() }
        if (!accepted) {
            throw AssertionError(
                "The card button ${resourceName(viewId)} refused the click, so the branch under test never ran"
            )
        }
        TestEnvironment.waitForIdleSync()
    }

    fun descriptionOf(palette: HighlightPalette): String =
        TestEnvironment.appString(palette.labelRes)

    fun indexOfSwatch(description: String): Int {
        val descriptions = swatchDescriptions()
        val index = descriptions.indexOf(description)
        if (index < 0) {
            throw ConditionTimeoutException(
                "The card carries no swatch described \"$description\". It offers $descriptions"
            )
        }
        return index
    }

    private fun requireSwatch(index: Int): View {
        val swatches = swatchViews()
        if (index !in swatches.indices) {
            throw ConditionTimeoutException(
                "The card offers ${swatches.size} swatches, so index $index cannot be tapped. " +
                    "InlineAnnotationActionController builds one per configured colour in rebuildHighlightSwatches."
            )
        }
        return swatches[index]
    }

    private fun requireButton(viewId: Int): View {
        val button = buttonOrNull(viewId)
            ?: throw ConditionTimeoutException("The reader carries no card button ${resourceName(viewId)}")
        if (button.visibility != View.VISIBLE) {
            throw ConditionTimeoutException(
                "The card button ${resourceName(viewId)} is not visible, so there is nothing to press"
            )
        }
        return button
    }

    private fun swatchViews(): List<View> {
        val activity = TestEnvironment.resumedActivity() ?: return emptyList()
        val flow = activity.findViewById<Flow>(R.id.textSelectionSwatchFlow) ?: return emptyList()
        val content = activity.findViewById<View>(R.id.textSelectionActionContent) ?: return emptyList()
        return flow.referencedIds.toList().mapNotNull { id -> content.findViewById<View>(id) }
    }

    private fun card(): View? = TestEnvironment.resumedActivity()?.findViewById(R.id.textSelectionActionCard)

    private fun buttonOrNull(viewId: Int): View? = TestEnvironment.resumedActivity()?.findViewById(viewId)

    private fun centreOf(view: View): PointF {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException("View ${resourceName(view.id)} has no size on screen, so it cannot be tapped")
        }
        return PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    fun resourceName(viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())
}
