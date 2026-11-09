// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.actions.FullScreenOverlay
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import java.io.File
import kotlin.math.abs

abstract class ZoomTestCase {

    @get:Rule
    val rules = SuiteRules.standard()

    protected val reader = ReaderRobot()

    protected fun fixture(name: String): File =
        ZoomFixtures.place(name, rules.testDirectory.directory())

    protected fun openReader(name: String = ZoomFixtures.NORMAL, block: () -> Unit) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()
            block()
        }
    }

    protected fun awaitReaderReady(): ReaderState = reader.awaitReadyAtFullQuality()

    protected fun state(): ReaderState = reader.readState()

    protected fun zoom(): Float = state().zoom!!

    protected fun settled(): ReaderState {
        reader.awaitMotionSettled()
        return reader.awaitRenderIdle()
    }

    protected fun settledFullQuality(): ReaderState {
        reader.awaitMotionSettled()
        return reader.awaitFullQualityRender()
    }

    protected fun assertZoom(message: String, expected: Float, actual: Float, tolerance: Float = ZOOM_TOLERANCE) {
        assertNear(message, expected, actual, tolerance)
    }

    protected fun zoomNear(expected: Float, actual: Float, tolerance: Float = ZOOM_TOLERANCE): Boolean =
        abs(expected - actual) <= tolerance

    protected fun assertNear(message: String, expected: Float, actual: Float, tolerance: Float) {
        assertEquals(message, expected.toDouble(), actual.toDouble(), tolerance.toDouble())
    }

    protected fun assertScrollAxisTravel(
        before: ReaderState,
        after: ReaderState,
        wanted: Float,
        tolerance: Float = DRAG_TOLERANCE_PX,
    ) {
        val expected = ViewportBounds.expectedScrollAxisOffset(after, ViewportBounds.scrollAxisOffset(before) + wanted)
        assertTrue(
            "the drag must move the scroll axis to about $expected, but it reads ${ViewportBounds.scrollAxisOffset(after)}",
            abs(ViewportBounds.scrollAxisOffset(after) - expected) <= tolerance,
        )
    }

    protected fun dragForwardUntilPage(page: Int, attempts: Int): ReaderState {
        repeat(attempts) {
            if (state().page == page) return settledFullQuality()
            Swipe.drag(ReaderCanvas.at(0.5f, PAGE_DRAG_FROM_FRACTION), ReaderCanvas.at(0.5f, PAGE_DRAG_TO_FRACTION))
            settledFullQuality()
        }
        val reached = settledFullQuality()
        assertEquals("the reader never reached page index $page after $attempts drags", page as Int?, reached.page)
        return reached
    }

    protected fun enterFullscreen() {
        awaitDisplayed(R.id.toolbarPrimaryActionOption)
        onView(withId(R.id.toolbarPrimaryActionOption)).perform(ViewActions.click())
        Await.until("the action bar reports not showing") { !actionBarShowing() }
    }

    protected fun revealFullscreenOverlay() {
        ReaderCanvas.tapCentre()
        FullScreenOverlay.awaitColumnVisible()
    }

    protected fun hideFullscreenOverlay() {
        ReaderCanvas.tapCentre()
        Await.until("the fullscreen overlay column is hidden again") { !isDisplayed(R.id.fullScreenButtonsLayout) }
    }

    protected fun lockZoom() {
        onView(withId(R.id.toggleZoomLockButton)).perform(ViewActions.click())
        Await.until("the viewer reports zoom locked") { state().zoomLocked == true }
    }

    protected fun lockHorizontalSwipe() {
        onView(withId(R.id.toggleHorizontalSwipeButton)).perform(ViewActions.click())
        Await.until("the viewer reports horizontal swipe locked") { state().horizontalSwipeLocked == true }
    }

    protected fun isChecked(@IdRes viewId: Int): Boolean = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivity()
            ?: throw ConditionTimeoutException("No resumed activity while reading ${resourceName(viewId)}")
        val button = activity.findViewById<com.google.android.material.button.MaterialButton>(viewId)
            ?: throw ConditionTimeoutException("${resourceName(viewId)} is not in the resumed activity")
        button.isChecked
    }

    protected fun actionBarShowing(): Boolean = TestEnvironment.onMainThread {
        (TestEnvironment.resumedActivity() as? AppCompatActivity)?.supportActionBar?.isShowing == true
    }

    protected fun isDisplayed(@IdRes viewId: Int): Boolean =
        runCatching { onView(withId(viewId)).check(matches(isDisplayed())) }.isSuccess

    protected fun awaitDisplayed(@IdRes viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("view ${resourceName(viewId)} is displayed", timeoutMs) { isDisplayed(viewId) }
    }

    protected fun assertDisplayed(@IdRes viewId: Int) {
        assertTrue("view ${resourceName(viewId)} must be displayed", isDisplayed(viewId))
    }

    protected fun assertNotDisplayed(@IdRes viewId: Int) {
        assertTrue("view ${resourceName(viewId)} must not be displayed", !isDisplayed(viewId))
    }

    protected fun scrollHandle(): UiObject2? =
        TestEnvironment.uiDevice.findObject(By.desc(DefaultScrollHandle.DEBUG_HANDLE_DESCRIPTION))

    protected fun scrollHandleDisplayed(): Boolean = scrollHandle() != null

    protected fun resourceName(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())

    companion object {
        const val ZOOM_TOLERANCE = 0.01f
        const val RESTING_ZOOM = 1.0f
        const val MID_ZOOM = 2.0f
        const val MIN_ZOOM = 0.5f
        const val DEFAULT_MAX_ZOOM = 10.0f
        const val DRAG_TOLERANCE_PX = 40f
        const val PAGE_DRAG_FROM_FRACTION = 0.85f
        const val PAGE_DRAG_TO_FRACTION = 0.15f
    }
}
