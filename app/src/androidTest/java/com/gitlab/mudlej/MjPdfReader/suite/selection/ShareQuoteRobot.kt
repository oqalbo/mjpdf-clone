// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object ShareQuoteRobot {

    val THEME_CHIPS = listOf(
        R.id.themeLight,
        R.id.themeDark,
        R.id.themeSepia,
        R.id.themeRose,
        R.id.themeLavender,
        R.id.themeMint,
    )

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the share quote dialog is shown", timeoutMs) { SelectionViews.exists(R.id.sharePreview) }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the share quote dialog is gone", timeoutMs) { !SelectionViews.exists(R.id.sharePreview) }
    }

    fun bookName(): String = SelectionViews.read(R.id.bookNameInput) { (it as EditText).text.toString() }

    fun author(): String = SelectionViews.read(R.id.authorInput) { (it as EditText).text.toString() }

    fun previewSize(): Pair<Int, Int> = SelectionViews.read(R.id.sharePreview) { view ->
        val drawable = (view as ImageView).drawable
            ?: throw AssertionError("The share preview carries no drawable, so no card was rendered")
        drawable.intrinsicWidth to drawable.intrinsicHeight
    }

    fun visibilityOf(@IdRes viewId: Int): Int = SelectionViews.visibility(viewId)

    fun expandCustomize() {
        if (isThemeChipUsable()) return
        onView(withId(R.id.customizeHeader)).perform(ViewActions.click())
        Await.until("the customize section is expanded and laid out") { isThemeChipUsable() }
    }

    fun chooseTheme(@IdRes chipId: Int) {
        expandCustomize()
        onView(withId(chipId)).perform(ViewActions.scrollTo(), ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun toggle(@IdRes switchId: Int) {
        onView(withId(switchId)).perform(ViewActions.scrollTo(), ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun isChecked(@IdRes switchId: Int): Boolean =
        SelectionViews.read(switchId) { (it as android.widget.CompoundButton).isChecked }

    private fun isThemeChipUsable(): Boolean = runCatching {
        SelectionViews.read(THEME_CHIPS.first()) { it.isShown && it.width > 0 && it.height > 0 }
    }.getOrDefault(false)

    fun share() {
        Dialogs.tapPositive()
        awaitGone()
    }

    fun cancel() {
        onView(withId(android.R.id.button2)).perform(ViewActions.click())
        awaitGone()
    }
}
