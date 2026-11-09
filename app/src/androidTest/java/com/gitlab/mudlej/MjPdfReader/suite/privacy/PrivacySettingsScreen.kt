// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.history.ReadingHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.history.ReadingHistoryAdapter
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage

object PrivacySettingsScreen {

    const val SCROLL_STEP_PX = 400
    const val BACK_PRESSES = 6

    fun pageIntent(page: SettingsPage): Intent =
        Intent(TestEnvironment.targetContext, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_PAGE, page.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openPage(page: SettingsPage) {
        TestEnvironment.targetContext.startActivity(pageIntent(page))
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        TestEnvironment.waitForIdleSync()
    }

    fun openHistoryAndPrivacy() {
        openPage(SettingsPage.HISTORY_PRIVACY)
        scrollTo(string(R.string.reading_history))
    }

    fun openBackup() {
        openPage(SettingsPage.BACKUP)
        scrollTo(string(R.string.backup_export_title))
    }

    fun openReadingHistory() {
        openHistoryAndPrivacy()
        tapRow(string(R.string.reading_history))
        Screens.awaitResumed(ReadingHistoryActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    fun readingHistoryTitles(expectedCount: Int): List<String> = Await.untilNotNull(
        "the reading history adapter has laid out all $expectedCount expected row(s)",
        Await.LONG_TIMEOUT_MS,
    ) {
        TestEnvironment.onMainThread {
            val list = rootOfResumed().findViewById<RecyclerView>(R.id.readingHistoryRecyclerView)
                ?: return@onMainThread null
            val adapter = list.adapter as? ReadingHistoryAdapter ?: return@onMainThread null
            if (!list.isLaidOut || list.layoutManager == null || list.isComputingLayout ||
                adapter.currentList.size != expectedCount
            ) {
                return@onMainThread null
            }
            adapter.currentList.map(ReadingHistoryAdapter::displayNameOf)
        }
    }

    fun leave() {
        repeat(BACK_PRESSES) {
            if (!Screens.isInTask(SettingsActivity::class.java) &&
                !Screens.isInTask(ReadingHistoryActivity::class.java)
            ) {
                return
            }
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.waitForIdleSync()
        }
    }

    fun tapRow(title: String) {
        scrollTo(title)
        val point = TestEnvironment.onMainThread {
            val row = shownTextView(title)
                ?: throw ConditionTimeoutException("The settings screen shows no row titled \"$title\"")
            centreOf(clickableAncestorOf(row))
        }
        TestEnvironment.uiDevice.click(point.first, point.second)
        TestEnvironment.waitForIdleSync()
    }

    fun scrollTo(title: String) {
        Await.until("the settings row \"$title\" is on screen") {
            if (isRowDisplayed(title)) return@until true
            scrollList()
            isRowDisplayed(title)
        }
    }

    fun isRowDisplayed(title: String): Boolean = runCatching {
        TestEnvironment.onMainThread {
            shownTextView(title)?.isShown == true
        }
    }.getOrDefault(false)

    private fun shownTextView(text: String): TextView? =
        Dialogs.firstDescendant(rootOfResumed(), TextView::class.java) { it.isShown && it.text?.toString() == text }

    private fun scrollList() {
        val list = TestEnvironment.onMainThread { Dialogs.firstDescendant(rootOfResumed(), RecyclerView::class.java) } ?: return
        TestEnvironment.onMainThread { list.scrollBy(0, SCROLL_STEP_PX) }
        TestEnvironment.waitForIdleSync()
    }

    private fun rootOfResumed(): View = TestEnvironment.resumedActivity()?.window?.decorView
        ?: throw ConditionTimeoutException("No resumed activity to read")

    private fun clickableAncestorOf(view: View): View {
        var current: View = view
        while (!current.isClickable) {
            current = current.parent as? View ?: return view
        }
        return current
    }

    private fun centreOf(view: View): Pair<Int, Int> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return (location[0] + view.width / 2) to (location[1] + view.height / 2)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
