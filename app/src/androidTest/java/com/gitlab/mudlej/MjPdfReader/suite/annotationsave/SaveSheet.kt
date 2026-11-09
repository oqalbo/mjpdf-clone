// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object SaveSheet {

    const val ABSENCE_WINDOW_MS = 1_500L

    fun title(): String = string(R.string.save_highlights)

    fun updateExistingLabel(): String = string(R.string.update_existing_file)

    fun saveAsNewCopyLabel(): String = string(R.string.save_as_new_copy)

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        AnnotationDialogs.awaitTitle(title(), timeoutMs)
    }

    fun isShown(): Boolean = TestEnvironment.uiDevice.hasObject(By.text(title()))

    fun assertNeverShown(windowMs: Long = ABSENCE_WINDOW_MS) {
        AnnotationDialogs.assertTitleNeverShown(title(), windowMs)
    }

    fun optionLabels(): List<String> {
        var labels: List<String>? = null
        onView(isAssignableFrom(android.widget.ListView::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val adapter = (view as android.widget.ListView).adapter
                ?: throw AssertionError("the save destination list has no adapter")
            labels = (0 until adapter.count).map { adapter.getItem(it).toString() }
        }
        return checkNotNull(labels)
    }

    fun chooseUpdateExisting() = tapItem(updateExistingLabel())

    fun chooseSaveAsNewCopy() = tapItem(saveAsNewCopyLabel())

    fun dismissWithBack() {
        TestEnvironment.uiDevice.pressBack()
        AnnotationDialogs.awaitTitleGone(title())
    }

    fun tapItem(label: String) {
        onView(withText(label)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(label)).inRoot(isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
