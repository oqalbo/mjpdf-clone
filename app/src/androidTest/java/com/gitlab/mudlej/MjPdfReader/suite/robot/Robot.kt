// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import com.gitlab.mudlej.MjPdfReader.suite.core.SuiteTiming
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.UiDevice
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

abstract class Robot {

    protected val device: UiDevice get() = TestEnvironment.uiDevice

    protected val targetPackage: String get() = TestEnvironment.targetPackage

    abstract fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS)

    fun waitForIdle() {
        SuiteTiming.measure("ui.idle") {
            device.waitForIdle()
            TestEnvironment.waitForIdleSync()
        }
    }

    fun pressBack() {
        device.pressBack()
    }

    protected fun view(@IdRes viewId: Int): ViewInteraction = onView(withId(viewId))

    protected fun click(@IdRes viewId: Int) {
        view(viewId).perform(ViewActions.click())
    }

    protected fun assertDisplayed(@IdRes viewId: Int) {
        view(viewId).check(matches(isDisplayed()))
    }

    protected fun isDisplayed(@IdRes viewId: Int): Boolean =
        Dialogs.hasViewOnScreen(viewId)

    protected fun awaitDisplayed(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("view ${resourceName(viewId)} is displayed", timeoutMs) { isDisplayed(viewId) }
    }

    protected fun awaitGone(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("view ${resourceName(viewId)} is no longer displayed", timeoutMs) { !isDisplayed(viewId) }
    }

    protected fun <T> readView(@IdRes viewId: Int, read: (View) -> T): T = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No resumed activity while reading view ${resourceName(viewId)}")
        val target = activity.findViewById<View>(viewId)
            ?: throw ConditionTimeoutException("View ${resourceName(viewId)} is not in the resumed activity")
        read(target)
    }

    protected fun bringIntoView(@IdRes viewId: Int) {
        bringIntoView { activity ->
            activity.findViewById(viewId)
                ?: throw ConditionTimeoutException("View ${resourceName(viewId)} is not in the resumed activity")
        }
    }

    protected fun bringIntoView(resolve: (android.app.Activity) -> View) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity()
                ?: throw ConditionTimeoutException("No resumed activity while scrolling")
            val target = resolve(activity)
            val scroller = scrollingAncestorOf(target) ?: return@onMainThread
            val viewport = scroller.height - scroller.paddingTop - scroller.paddingBottom
            val offset = offsetWithin(target, scroller)
            scroller.scrollTo(0, (offset - (viewport - target.height) / 2).coerceAtLeast(0))
        }
        waitForIdle()
    }

    private fun scrollingAncestorOf(view: View): ViewGroup? {
        var parent = view.parent
        while (parent is View) {
            if (parent is ScrollView || parent is NestedScrollView) return parent as ViewGroup
            parent = parent.parent
        }
        return null
    }

    private fun offsetWithin(view: View, ancestor: ViewGroup): Int {
        var offset = 0
        var current: View = view
        while (current !== ancestor) {
            offset += current.top
            current = current.parent as? View ?: break
        }
        return offset
    }

    protected fun resourceName(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())
}
