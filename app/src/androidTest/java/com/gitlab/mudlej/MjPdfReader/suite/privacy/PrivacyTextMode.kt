// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.materialswitch.MaterialSwitch
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity

class PrivacyTextMode(private val reader: PrivacyReader) {

    fun openFromReader() {
        reader.openChildScreen(TestEnvironment.targetContext.getString(R.string.text_mode))
        awaitShown()
    }

    fun awaitShown() {
        Screens.awaitResumed(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    fun awaitFinished() {
        Screens.awaitGone(TextModeActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    fun activity(): TextModeActivity =
        TestEnvironment.resumedActivityOfType(TextModeActivity::class.java) as? TextModeActivity
            ?: throw ConditionTimeoutException("No resumed text mode activity")

    fun <T> onActivity(read: (TextModeActivity) -> T): T = TestEnvironment.onMainThread { read(activity()) }

    fun awaitContent() {
        Await.until("text mode renders a page of text", Await.VERY_LONG_TIMEOUT_MS) {
            runCatching {
                onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.textPagesRecyclerView)
                    (0 until list.childCount).any { index ->
                        val text = list.getChildAt(index).findViewById<TextView>(R.id.pageText)
                        text != null && text.visibility == View.VISIBLE && !text.text.isNullOrBlank()
                    }
                }
            }.getOrDefault(false)
        }
    }

    fun controlsVisibility(): Int = onActivity { it.findViewById<View>(R.id.readerControlsCard).visibility }

    fun showControls() {
        if (controlsVisibility() == View.VISIBLE) return
        val bounds = onActivity { activity ->
            val list = activity.findViewById<View>(R.id.textPagesRecyclerView)
            val location = IntArray(2)
            list.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + list.width, location[1] + list.height)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.top + (bounds.height() * TAP_Y_FRACTION).toInt())
        TestEnvironment.uiDevice.waitForIdle()
        Await.until("the text mode controls card is visible") { controlsVisibility() == View.VISIBLE }
    }

    fun pageCounter(): String? = onActivity { it.findViewById<TextView>(R.id.pageButton)?.text?.toString() }

    fun counterFor(oneBasedPage: Int, pageCount: Int): String =
        TestEnvironment.targetContext.getString(R.string.text_mode_page_counter, oneBasedPage, pageCount)

    fun goToPage(oneBasedPage: Int, pageCount: Int) {
        showControls()
        clickControl(R.id.pageButton)
        Dialogs.awaitDialogTitled(R.string.go_to_page)
        onView(withId(R.id.editText)).inRoot(isDialog())
            .perform(replaceText(oneBasedPage.toString()), closeSoftKeyboard())
        onView(withText(string(R.string.go_to))).inRoot(isDialog()).perform(click())
        Await.untilValue(
            "the text mode page counter reaches page $oneBasedPage",
            Await.LONG_TIMEOUT_MS,
            read = { pageCounter() },
        ) { it == counterFor(oneBasedPage, pageCount) }
    }

    fun toggleJoinParagraphs() {
        showControls()
        clickControl(R.id.typographyButton)
        Dialogs.awaitViewOnScreen(R.id.resetSettingsButton)
        onView(withId(R.id.resetSettingsButton)).inRoot(isDialog()).check(matches(isDisplayed()))
        expandSheet()
        val switch = sheetView<MaterialSwitch>(R.id.joinParagraphsSwitch)
        val before = TestEnvironment.onMainThread { switch.isChecked }
        onView(withId(R.id.joinParagraphsSwitch)).inRoot(isDialog()).perform(scrollTo(), click())
        Await.until("the Join Paragraphs switch flips away from $before") {
            TestEnvironment.onMainThread { switch.isChecked } != before
        }
        TestEnvironment.uiDevice.pressBack()
        Await.until("the typography sheet is dismissed") { !isSheetShown() }
        TestEnvironment.waitForIdleSync()
    }

    fun isIncognitoChrome(): Boolean = PrivacyChrome.isIncognitoChrome(activity())

    fun describeChrome(): String = PrivacyChrome.describe(activity())

    private fun expandSheet() {
        val sheet = sheetView<View>(com.google.android.material.R.id.design_bottom_sheet)
        TestEnvironment.onMainThread {
            BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        Await.until("the typography sheet reaches its expanded state") {
            TestEnvironment.onMainThread {
                BottomSheetBehavior.from(sheet).state == BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun isSheetShown(): Boolean = Dialogs.hasViewOnScreen(R.id.resetSettingsButton)

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> sheetView(@IdRes viewId: Int): T {
        Dialogs.awaitViewOnScreen(R.id.resetSettingsButton)
        var captured: View? = null
        Await.untilNoThrow("the typography sheet holds view $viewId") {
            captured = null
            onView(withId(viewId)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                captured = view
            }
            if (captured == null) throw ConditionTimeoutException("No view captured for $viewId")
        }
        return captured as T
    }

    private fun clickControl(@IdRes viewId: Int) {
        onView(withId(viewId)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val TAP_Y_FRACTION = 0.3f
    }
}
