// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object SearchBar {

    val COUNTER = Regex("^(\\d+)/(\\d+)$")

    const val DISABLED_ALPHA = 0.35f

    data class Counter(val position: Int, val total: Int) {
        override fun toString(): String = "$position/$total"
    }

    fun labelOrNull(): String? = TestEnvironment.onMainThread { labelView()?.text?.toString() }

    fun label(): String = labelOrNull()
        ?: throw ConditionTimeoutException("No search results snackbar is on screen")

    fun isShown(): Boolean = labelOrNull() != null

    fun isLabelClickable(): Boolean = TestEnvironment.onMainThread { labelView()?.isClickable == true }

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the search results snackbar is shown", timeoutMs) { isShown() }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the search results snackbar is gone", timeoutMs) { !isShown() }
    }

    fun awaitLabel(expected: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the search snackbar label reads \"$expected\"",
            timeoutMs,
            read = { labelOrNull() },
        ) { it == expected }
    }

    fun resultsLabel(): String = string(R.string.results)

    fun noResultsLabel(): String = string(R.string.search_no_results)

    fun failedToHighlightLabel(): String = string(R.string.failed_to_highlight_search_result)

    fun doneLabel(): String = string(R.string.done)

    fun awaitResultsLabel(timeoutMs: Long = Await.LONG_TIMEOUT_MS) = awaitLabel(resultsLabel(), timeoutMs)

    fun awaitNoResultsLabel(timeoutMs: Long = Await.LONG_TIMEOUT_MS) = awaitLabel(noResultsLabel(), timeoutMs)

    fun counterText(): String? = TestEnvironment.onMainThread {
        counterView()?.takeIf { it.visibility == View.VISIBLE }?.text?.toString()
    }

    fun counter(): Counter? = counterText()?.let { text ->
        COUNTER.matchEntire(text)?.let { match ->
            Counter(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }

    fun requireCounter(): Counter = counter()
        ?: throw ConditionTimeoutException(
            "The search snackbar shows no counter matching ${COUNTER.pattern}, its text is ${counterText()}"
        )

    fun awaitCounter(timeoutMs: Long = Await.LONG_TIMEOUT_MS): Counter =
        Await.untilNotNull("the search snackbar shows a counter", timeoutMs) { counter() }

    fun awaitCounterText(expected: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the search counter reads \"$expected\"",
            timeoutMs,
            read = { counterText() },
        ) { it == expected }
    }

    fun awaitCounterOf(position: Int, total: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) =
        awaitCounterText("$position/$total", timeoutMs)

    fun awaitCounterPosition(position: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Counter =
        Await.untilValue(
            "the search counter reaches position $position",
            timeoutMs,
            read = { counter() },
        ) { it != null && it.position == position }!!

    fun awaitCounterTotalAbove(total: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Counter =
        Await.untilValue(
            "the search counter total grows past $total",
            timeoutMs,
            read = { counter() },
        ) { it != null && it.total > total }!!

    fun awaitCounterStable(stableForMs: Long = COUNTER_STABLE_MS, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Counter {
        var last: Counter? = counter()
        Await.untilTrueContinuously(
            condition = "the search counter stops changing",
            stableForMs = stableForMs,
            timeoutMs = timeoutMs,
        ) {
            val now = counter()
            val held = now != null && now == last
            last = now
            held
        }
        return requireCounter()
    }

    fun counterVisibility(): Int? = TestEnvironment.onMainThread { counterView()?.visibility }

    fun previousVisibility(): Int? = TestEnvironment.onMainThread { arrow(previousDescription())?.visibility }

    fun nextVisibility(): Int? = TestEnvironment.onMainThread { arrow(nextDescription())?.visibility }

    fun previousEnabled(): Boolean? = TestEnvironment.onMainThread { arrow(previousDescription())?.isEnabled }

    fun nextEnabled(): Boolean? = TestEnvironment.onMainThread { arrow(nextDescription())?.isEnabled }

    fun previousAlpha(): Float? = TestEnvironment.onMainThread { arrow(previousDescription())?.alpha }

    fun nextAlpha(): Float? = TestEnvironment.onMainThread { arrow(nextDescription())?.alpha }

    fun navigationGone(): Boolean = TestEnvironment.onMainThread {
        listOf(counterView(), arrow(previousDescription()), arrow(nextDescription()))
            .all { it == null || it.visibility == View.GONE }
    }

    fun navigationAbsent(): Boolean = TestEnvironment.onMainThread {
        counterView() == null && arrow(previousDescription()) == null && arrow(nextDescription()) == null
    }

    fun nextBounds(): Rect = arrowBounds(nextDescription())

    fun previousBounds(): Rect = arrowBounds(previousDescription())

    fun tapNext() {
        tapAt(nextBounds())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapPrevious() {
        tapAt(previousBounds())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapAt(bounds: Rect) {
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
    }

    fun tapDone() {
        val bounds = TestEnvironment.onMainThread {
            val action = actionView()
                ?: throw ConditionTimeoutException("The search snackbar shows no DONE action")
            boundsOnMainThread(action)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun actionText(): String? = TestEnvironment.onMainThread { actionView()?.text?.toString() }

    fun tapLabel() {
        tapLabelWithoutSettling()
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun tapLabelWithoutSettling() {
        val bounds = labelBounds()
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
    }

    fun labelBounds(): Rect = TestEnvironment.onMainThread {
        val label = labelView() ?: throw ConditionTimeoutException("No search results snackbar is on screen")
        boundsOnMainThread(label)
    }

    fun gravity(): Int = TestEnvironment.onMainThread {
        val bar = barView() ?: throw ConditionTimeoutException("No search results snackbar is on screen")
        (bar.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity
            ?: throw ConditionTimeoutException(
                "The search snackbar is not laid out in a FrameLayout, so it carries no gravity to read"
            )
    }

    fun isAnchoredToTop(): Boolean = gravity() and android.view.Gravity.VERTICAL_GRAVITY_MASK ==
        android.view.Gravity.TOP

    fun barBounds(): Rect = TestEnvironment.onMainThread {
        val bar = barView() ?: throw ConditionTimeoutException("No search results snackbar is on screen")
        boundsOnMainThread(bar)
    }

    private fun arrowBounds(description: String): Rect = TestEnvironment.onMainThread {
        val button = arrow(description)
            ?: throw ConditionTimeoutException("The search snackbar carries no \"$description\" button")
        boundsOnMainThread(button)
    }

    private fun boundsOnMainThread(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun labelView(): TextView? {
        val activity = TestEnvironment.resumedActivity() ?: return null
        return activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.takeIf { it.isShown }
    }

    private fun actionView(): Button? {
        val activity = TestEnvironment.resumedActivity() ?: return null
        return activity.findViewById<Button>(com.google.android.material.R.id.snackbar_action)?.takeIf { it.isShown }
    }

    private fun barView(): View? = (contentLayout()?.parent as? View)

    private fun contentLayout(): ViewGroup? = labelView()?.parent as? ViewGroup

    private fun counterView(): TextView? {
        val content = contentLayout() ?: return null
        val previousIndex = indexOfDescription(content, previousDescription()) ?: return null
        return (previousIndex + 1 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<TextView>()
            .firstOrNull()
    }

    private fun arrow(description: String): View? {
        val content = contentLayout() ?: return null
        val index = indexOfDescription(content, description) ?: return null
        return content.getChildAt(index)
    }

    private fun indexOfDescription(content: ViewGroup, description: String): Int? =
        (0 until content.childCount).firstOrNull {
            content.getChildAt(it).contentDescription?.toString() == description
        }

    private fun previousDescription(): String = string(R.string.previous_search_result)

    private fun nextDescription(): String = string(R.string.next_search_result)

    private fun string(resId: Int): String = TestEnvironment.appString(resId)

    private const val COUNTER_STABLE_MS = 700L
}
