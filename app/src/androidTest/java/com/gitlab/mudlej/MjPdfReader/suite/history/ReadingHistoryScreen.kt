// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.settings.SettingsScreen
import com.gitlab.mudlej.MjPdfReader.ui.history.ReadingHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not

data class HistoryRow(val title: String, val subtitle: String)

object ReadingHistoryScreen {

    fun historyAndPrivacyIntent(): Intent =
        Intent(TestEnvironment.targetContext, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_PAGE, SettingsPage.HISTORY_PRIVACY.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openHistoryAndPrivacy() {
        TestEnvironment.targetContext.startActivity(historyAndPrivacyIntent())
        Screens.awaitResumed(SettingsActivity::class.java)
        SettingsScreen.awaitRow(string(R.string.reading_history))
    }

    fun open(): ReadingHistoryList {
        openHistoryAndPrivacy()
        tapSettingsRow(string(R.string.reading_history))
        Screens.awaitResumed(ReadingHistoryActivity::class.java)
        return ReadingHistoryList
    }

    fun leaveSettings() {
        repeat(SETTINGS_BACK_PRESSES) {
            if (!Screens.isInTask(SettingsActivity::class.java)) return
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.waitForIdleSync()
        }
        Screens.awaitGone(SettingsActivity::class.java)
    }

    fun tapSettingsRow(title: String) {
        SettingsScreen.tapRow(title)
    }

    fun confirmDialogTitleShown(title: String) {
        onView(withText(title)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun confirmDialogMessageShown(message: String) {
        onView(withText(message)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun tapConfirm(label: String) {
        onView(withText(label)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun tapDelete() = tapConfirm(string(R.string.delete))

    fun tapCancel() = tapConfirm(string(R.string.cancel))

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    const val SETTINGS_BACK_PRESSES = 3
}

object ReadingHistoryList {

    fun rows(): List<HistoryRow> = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(ReadingHistoryActivity::class.java)
            ?: return@onMainThread emptyList()
        val list = activity.findViewById<RecyclerView>(R.id.readingHistoryRecyclerView)
            ?: return@onMainThread emptyList()
        (0 until list.childCount).mapNotNull { index ->
            val child = list.getChildAt(index)
            val title = child.findViewById<TextView>(R.id.readingHistoryTitle)?.text?.toString()
                ?: return@mapNotNull null
            val subtitle = child.findViewById<TextView>(R.id.readingHistorySubtitle)?.text?.toString().orEmpty()
            HistoryRow(title, subtitle)
        }
    }

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<HistoryRow> =
        Await.untilValue(
            "the reading history list renders $expected rows",
            timeoutMs,
            read = { rows() },
        ) { it.size == expected }

    fun row(title: String): HistoryRow = rows().firstOrNull { it.title == title }
        ?: throw ConditionTimeoutException(
            "The reading history list has no row titled \"$title\", it holds ${rows()}"
        )

    fun tapRowTitle(title: String) {
        onView(allOf(withId(R.id.readingHistoryTitle), withText(title), isDisplayed())).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun tapDeleteOn(title: String) {
        val point = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(ReadingHistoryActivity::class.java)
                ?: throw ConditionTimeoutException("The Reading History screen is not resumed")
            val list = activity.findViewById<RecyclerView>(R.id.readingHistoryRecyclerView)
                ?: throw ConditionTimeoutException("The Reading History screen has no list")
            val row = (0 until list.childCount)
                .map { list.getChildAt(it) }
                .firstOrNull { it.findViewById<TextView>(R.id.readingHistoryTitle)?.text?.toString() == title }
                ?: throw ConditionTimeoutException("The reading history list has no row titled \"$title\"")
            val button = row.findViewById<View>(R.id.readingHistoryDelete)
                ?: throw ConditionTimeoutException("The row titled \"$title\" carries no delete button")
            val location = IntArray(2)
            button.getLocationOnScreen(location)
            (location[0] + button.width / 2) to (location[1] + button.height / 2)
        }
        TestEnvironment.uiDevice.click(point.first, point.second)
        TestEnvironment.waitForIdleSync()
    }

    fun awaitEmptyState(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilNoThrow("the reading history empty state is shown", timeoutMs) {
            onView(withId(R.id.message)).check(matches(isDisplayed()))
            onView(withId(R.id.message)).check(matches(withText(R.string.reading_history_empty)))
        }
    }

    fun assertEmptyStateHidden() {
        onView(withId(R.id.message)).check(matches(not(isDisplayed())))
    }

    fun childCount(): Int = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(ReadingHistoryActivity::class.java)
            ?: return@onMainThread -1
        activity.findViewById<RecyclerView>(R.id.readingHistoryRecyclerView)?.childCount ?: -1
    }

    fun title(): String? = Screens.titleOfResumed()
}
