// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.viewpager2.widget.ViewPager2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroDebug
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroPageSnapshot
import com.gitlab.mudlej.MjPdfReader.ui.intro.IntroPagerState
import com.gitlab.mudlej.MjPdfReader.ui.intro.MainIntroActivity
import org.hamcrest.CoreMatchers.allOf

class IntroRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) {
        Screens.awaitResumed(MainIntroActivity::class.java, timeoutMs)
        Await.untilValue(
            "the intro pager is bound and settled",
            timeoutMs,
            read = { pagerStateOrNull() },
        ) { it != null && it.pageCount > 0 && it.settled }
    }

    fun isShown(): Boolean = TestEnvironment.resumedActivityOfType(MainIntroActivity::class.java) != null

    fun pagerStateOrNull(): IntroPagerState? = TestEnvironment.onMainThread {
        val pager = viewOrNull<ViewPager2>(R.id.introPager) ?: return@onMainThread null
        IntroDebug.pagerState(pager)
    }

    fun pagerState(): IntroPagerState = pagerStateOrNull()
        ?: throw ConditionTimeoutException(
            "The intro pager hook produced nothing. Either the intro is not resumed, or the debug hook is " +
                "absent from this build."
        )

    fun pageCount(): Int = pagerState().pageCount

    fun currentItem(): Int = pagerState().currentItem

    fun awaitSettledOn(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): IntroPagerState =
        Await.untilValue(
            "the intro pager settles on page $position",
            timeoutMs,
            read = { pagerStateOrNull() },
        ) { it != null && it.currentItem == position && it.settled }!!

    fun pageDescription(): String? = TestEnvironment.onMainThread {
        viewOrNull<View>(R.id.introPager)?.contentDescription?.toString()
    }

    fun awaitPageDescription(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilValue(
            "the intro page hook reads page $position",
            timeoutMs,
            read = { pageDescription() },
        ) { it != null && it.startsWith("$PAGE_DESCRIPTION_PREFIX$position:") }!!

    fun describedPosition(): Int? =
        pageDescription()?.removePrefix(PAGE_DESCRIPTION_PREFIX)?.substringBefore(':')?.toIntOrNull()

    fun describedCount(): Int? =
        pageDescription()?.substringAfterLast(':')?.toIntOrNull()

    fun snapshotOrNull(position: Int): IntroPageSnapshot? = TestEnvironment.onMainThread {
        val pager = viewOrNull<ViewPager2>(R.id.introPager) ?: return@onMainThread null
        IntroDebug.pageSnapshot(pager, position)
    }

    fun awaitSnapshot(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): IntroPageSnapshot =
        Await.untilNotNull("the intro page $position holder is bound", timeoutMs) { snapshotOrNull(position) }

    fun title(position: Int): String = awaitSnapshot(position).title?.toString().orEmpty()

    fun awaitTitle(position: Int, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): String =
        Await.untilValue(
            "the intro page $position reads \"$expected\"",
            timeoutMs,
            read = { snapshotOrNull(position)?.title?.toString() },
        ) { it == expected }!!

    fun awaitPage(position: Int, expectedTitle: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        awaitSettledOn(position, timeoutMs)
        awaitPageDescription(position, timeoutMs)
        awaitTitle(position, expectedTitle, timeoutMs)
    }

    fun dotCount(): Int = TestEnvironment.onMainThread {
        requireView<ViewGroup>(R.id.dotsLayout).childCount
    }

    fun dotDescriptions(): List<String> = TestEnvironment.onMainThread {
        val dots = requireView<ViewGroup>(R.id.dotsLayout)
        (0 until dots.childCount).map { dots.getChildAt(it).contentDescription?.toString().orEmpty() }
    }

    fun activeDotIndexes(): List<Int> = dotDescriptions()
        .mapIndexedNotNull { index, description -> index.takeIf { description == DOT_ACTIVE } }

    fun awaitActiveDot(index: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<Int> =
        Await.untilValue(
            "only intro dot $index is marked active",
            timeoutMs,
            read = { runCatching { activeDotIndexes() }.getOrNull() },
        ) { it == listOf(index) }!!

    fun nextButtonText(): String =
        TestEnvironment.onMainThread { requireView<TextView>(R.id.nextButton).text.toString() }

    fun skipButtonText(): String =
        TestEnvironment.onMainThread { requireView<TextView>(R.id.skipButton).text.toString() }

    fun skipButtonVisibility(): Int =
        TestEnvironment.onMainThread { requireView<View>(R.id.skipButton).visibility }

    fun isActionBarShowing(): Boolean = TestEnvironment.onMainThread {
        val activity = requireActivity() as AppCompatActivity
        activity.supportActionBar?.isShowing == true
    }

    fun tapNext(): IntroRobot {
        onView(allOf(withId(R.id.nextButton), isDisplayed())).perform(ViewActions.click())
        waitForIdle()
        return this
    }

    fun tapSkip(): IntroRobot {
        onView(allOf(withId(R.id.skipButton), isDisplayed())).perform(ViewActions.click())
        waitForIdle()
        return this
    }

    fun advanceTo(position: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): IntroRobot {
        while (currentItem() < position) {
            val next = currentItem() + 1
            tapNext()
            awaitSettledOn(next, timeoutMs)
        }
        return this
    }

    fun swipeForward(): IntroRobot = swipe(forward = true)

    fun swipeBack(): IntroRobot = swipe(forward = false)

    private fun swipe(forward: Boolean): IntroRobot {
        val bounds = pagerBounds()
        val y = bounds.centerY()
        val inset = (bounds.width() * EDGE_INSET_FRACTION).toInt()
        val left = bounds.left + inset
        val right = bounds.right - inset
        if (forward) {
            device.swipe(right, y, left, y, SWIPE_STEPS)
        } else {
            device.swipe(left, y, right, y, SWIPE_STEPS)
        }
        waitForIdle()
        return this
    }

    private fun pagerBounds(): Rect = TestEnvironment.onMainThread {
        val pager = requireView<ViewPager2>(R.id.introPager)
        val location = IntArray(2)
        pager.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + pager.width, location[1] + pager.height)
    }

    private fun requireActivity(): Activity =
        TestEnvironment.resumedActivityOfType(MainIntroActivity::class.java)
            ?: throw ConditionTimeoutException("MainIntroActivity is not the resumed activity")

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> viewOrNull(@IdRes viewId: Int): T? {
        val activity = TestEnvironment.resumedActivityOfType(MainIntroActivity::class.java) ?: return null
        return activity.findViewById<View>(viewId) as? T
    }

    private fun <T : View> requireView(@IdRes viewId: Int): T = viewOrNull<T>(viewId)
        ?: throw ConditionTimeoutException("View ${resourceName(viewId)} is not in the resumed MainIntroActivity")

    companion object {

        const val PAGE_DESCRIPTION_PREFIX = "intro-page:"
        const val DOT_ACTIVE = "intro-dot-active"
        const val DOT_INACTIVE = "intro-dot-inactive"
        const val EXPECTED_PAGE_COUNT = 5

        private const val SWIPE_STEPS = 12
        private const val EDGE_INSET_FRACTION = 0.15f

        val PAGE_TITLE_RES = listOf(
            R.string.intro_welcome_title,
            R.string.intro_library_title,
            R.string.intro_reading_title,
            R.string.intro_annotate_title,
            R.string.intro_custom_title,
        )

        val FIRST_PAGE_FEATURE_RES = listOf(
            R.string.intro_welcome_tagline,
            R.string.intro_welcome_privacy,
            R.string.intro_welcome_clean,
            R.string.intro_welcome_foss,
        )

        fun pageTitles(): List<String> =
            PAGE_TITLE_RES.map { TestEnvironment.targetContext.getString(it) }

        fun pageTitle(position: Int): String = pageTitles()[position]

        fun firstPageFeatures(): List<String> =
            FIRST_PAGE_FEATURE_RES.map { TestEnvironment.targetContext.getString(it) }
    }
}
