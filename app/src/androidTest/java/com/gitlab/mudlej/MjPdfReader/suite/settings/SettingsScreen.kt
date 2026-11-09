// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import org.hamcrest.CoreMatchers.allOf
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import com.google.android.material.button.MaterialButtonToggleGroup

data class SettingsRow(
    val key: String?,
    val title: String,
    val summary: String?,
    val enabled: Boolean,
    val selectable: Boolean,
    val checked: Boolean?,
    val widgetLayoutResource: Int,
    val className: String,
) {
    fun summaryOrEmpty(): String = summary.orEmpty()
}

object SettingsScreen {

    const val TIME_PICKER_TAG = "autoBackupTimePicker"

    fun intent(): Intent = Intent(TestEnvironment.targetContext, SettingsActivity::class.java)

    fun pageIntent(page: SettingsPage): Intent = intent().putExtra(SettingsActivity.EXTRA_PAGE, page.name)

    fun activityOrNull(): Activity? = TestEnvironment.resumedActivityOfType(SettingsActivity::class.java)

    fun activity(): AppCompatActivity = activityOrNull() as? AppCompatActivity
        ?: throw ConditionTimeoutException("No settings screen is resumed")

    fun awaitResumed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Activity =
        Await.untilNotNull("the settings screen is resumed", timeoutMs) { activityOrNull() }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the settings screen is no longer resumed", timeoutMs) { activityOrNull() == null }
    }

    fun title(): String = TestEnvironment.onMainThread { activity().title?.toString().orEmpty() }

    fun awaitTitle(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the settings screen is titled \"$expected\"",
            timeoutMs,
            read = { runCatching { title() }.getOrNull() },
        ) { it == expected }
    }

    fun rows(): List<SettingsRow> = TestEnvironment.onMainThread {
        val collected = mutableListOf<SettingsRow>()
        collectLeaves(requireFragment().preferenceScreen, collected)
        collected
    }

    fun rowTitles(): List<String> = rows().map { it.title }

    fun rowOrNull(title: String): SettingsRow? = rows().firstOrNull { it.title == title }

    fun row(title: String): SettingsRow = rowOrNull(title)
        ?: throw ConditionTimeoutException(
            "The settings screen shows no row titled \"$title\", it shows ${rowTitles()}"
        )

    fun hasRow(title: String): Boolean = runCatching { rowOrNull(title) != null }.getOrDefault(false)

    fun awaitRow(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): SettingsRow =
        Await.untilNotNull("the settings screen shows a row titled \"$title\"", timeoutMs) {
            runCatching { rowOrNull(title) }.getOrNull()
        }

    fun awaitRowSummary(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS, predicate: (String?) -> Boolean): String? =
        Await.untilValue(
            "the \"$title\" row summary settles",
            timeoutMs,
            read = { runCatching { rowOrNull(title)?.summary }.getOrNull() },
            predicate = predicate,
        )

    fun awaitRowSummaryIs(title: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        awaitRowSummary(title, timeoutMs) { it == expected }
    }

    fun awaitRowSummaryContains(title: String, fragment: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        awaitRowSummary(title, timeoutMs) { it != null && it.contains(fragment) }
    }

    fun awaitChecked(title: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the \"$title\" switch reads checked $expected",
            timeoutMs,
            read = { runCatching { rowOrNull(title)?.checked }.getOrNull() },
        ) { it == expected }
    }

    fun awaitEnabled(title: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the \"$title\" row reads enabled $expected",
            timeoutMs,
            read = { runCatching { rowOrNull(title)?.enabled }.getOrNull() },
        ) { it == expected }
    }

    fun sectionTitles(): List<String> = TestEnvironment.onMainThread {
        val collected = mutableListOf<String>()
        collectCategories(requireFragment().preferenceScreen, collected)
        collected
    }

    fun tapRow(title: String) {
        awaitRow(title)
        scrollToRow(title)
        onView(allOf(withId(android.R.id.title), withText(title), isDisplayed())).perform(TouchActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun openPage(page: SettingsPage) {
        tapRow(SettingsStrings.pageTitle(page))
        awaitTitle(SettingsStrings.pageTitle(page))
    }

    fun back() {
        TestEnvironment.uiDevice.pressBack()
        TestEnvironment.waitForIdleSync()
    }

    fun awaitRootList(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the settings root list is showing", timeoutMs) {
            runCatching { rowTitles().containsAll(SettingsPage.entries.map { SettingsStrings.pageTitle(it) }) }
                .getOrDefault(false)
        }
    }

    fun tapUpArrow() {
        val bounds = TestEnvironment.onMainThread {
            val descriptions = upArrowDescriptions()
            val view = Dialogs.firstDescendant(activity().window.decorView, View::class.java) {
                it.isShown && it.contentDescription?.toString() in descriptions
            }
                ?: throw ConditionTimeoutException("The settings toolbar shows no up arrow")
            boundsOf(view)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.waitForIdleSync()
    }

    fun isSearchActionPresent(): Boolean = TestEnvironment.onMainThread {
        activity().findViewById<View>(R.id.searchSettingsOption)?.isShown == true
    }

    fun expandSearch() {
        Await.until("the settings search action is visible") { isSearchActionPresent() }
        onView(withId(R.id.searchSettingsOption)).perform(TouchActions.click())
        TestEnvironment.waitForIdleSync()
        Await.until("the settings search field is attached", Await.DEFAULT_TIMEOUT_MS) { searchViewOrNull() != null }
        dropSearchFocus()
    }

    fun dropSearchFocus() {
        TestEnvironment.onMainThread { searchViewOrNull()?.clearFocus() }
        TestEnvironment.waitForIdleSync()
    }

    fun typeSearchQuery(query: String) {
        TestEnvironment.onMainThread {
            val field = searchViewOrNull()
                ?: throw ConditionTimeoutException("The settings search field is not attached")
            field.setQuery(query, false)
        }
        TestEnvironment.waitForIdleSync()
    }

    fun search(query: String) {
        expandSearch()
        typeSearchQuery(query)
    }

    fun searchViewOrNull(): SearchView? = TestEnvironment.onMainThread {
        activityOrNull()?.let { Dialogs.firstDescendant(it.window.decorView, SearchView::class.java) }
    }

    fun searchQueryOrNull(): String? = TestEnvironment.onMainThread {
        activityOrNull()?.let { Dialogs.firstDescendant(it.window.decorView, SearchView::class.java) }?.query?.toString()
    }

    fun tapThemeSegment(rowTitle: String, segmentId: Int) {
        scrollToRow(rowTitle)
        val bounds = TestEnvironment.onMainThread {
            val segment = rowView(rowTitle).findViewById<View>(segmentId)
                ?: throw ConditionTimeoutException("The \"$rowTitle\" row carries no segment $segmentId")
            boundsOf(segment)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.waitForIdleSync()
    }

    fun checkedThemeSegment(rowTitle: String): Int {
        scrollToRow(rowTitle)
        return TestEnvironment.onMainThread {
            val group = rowView(rowTitle).findViewById<MaterialButtonToggleGroup>(R.id.theme_choice_group)
                ?: throw ConditionTimeoutException("The \"$rowTitle\" row carries no theme strip")
            group.checkedButtonId
        }
    }

    fun awaitCheckedThemeSegment(rowTitle: String, segmentId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the \"$rowTitle\" strip checks segment $segmentId",
            timeoutMs,
            read = { runCatching { checkedThemeSegment(rowTitle) }.getOrNull() },
        ) { it == segmentId }
    }

    fun nightModeOfActivity(): Int = TestEnvironment.onMainThread {
        activity().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    fun awaitNightMode(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            "the settings screen renders with uiMode night $expected",
            timeoutMs,
            read = { runCatching { nightModeOfActivity() }.getOrNull() },
        ) { it == expected }
    }

    fun scrollListToEnd() {
        TestEnvironment.onMainThread {
            val list = listView()
            val last = (list.adapter?.itemCount ?: 1) - 1
            (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(last, 0)
                ?: list.scrollToPosition(last)
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun rowView(title: String): View {
        val list = listView()
        val position = adapterPositionOf(title)
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .firstOrNull { list.getChildAdapterPosition(it) == position }
            ?: throw ConditionTimeoutException(
                "The row titled \"$title\" sits at adapter position $position but never bound to a view"
            )
    }

    private fun scrollToRow(title: String) {
        val position = TestEnvironment.onMainThread { adapterPositionOf(title) }
        TestEnvironment.onMainThread {
            val list = listView()
            (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                ?: list.scrollToPosition(position)
        }
        TestEnvironment.waitForIdleSync()
    }

    private fun adapterPositionOf(title: String): Int {
        val fragment = requireFragment()
        val preference = findPreference(fragment.preferenceScreen, title)
            ?: throw ConditionTimeoutException("The settings screen holds no preference titled \"$title\"")
        val callback = fragment.listView.adapter as? PreferenceGroup.PreferencePositionCallback
            ?: throw ConditionTimeoutException(
                "The preference list adapter does not report positions, so a row cannot be scrolled to"
            )
        val position = callback.getPreferenceAdapterPosition(preference)
        if (position == RecyclerView.NO_POSITION) {
            throw ConditionTimeoutException("The row titled \"$title\" is not in the preference list adapter")
        }
        return position
    }

    private fun listView(): RecyclerView = requireFragment().listView

    fun fragmentOrNull(): PreferenceFragmentCompat? = TestEnvironment.onMainThread {
        val activity = activityOrNull() as? AppCompatActivity ?: return@onMainThread null
        activity.supportFragmentManager.findFragmentById(R.id.fragment_container_view) as? PreferenceFragmentCompat
    }

    private fun requireFragment(): PreferenceFragmentCompat {
        val activity = activity()
        val fragment: Fragment? = activity.supportFragmentManager.findFragmentById(R.id.fragment_container_view)
        return fragment as? PreferenceFragmentCompat
            ?: throw ConditionTimeoutException(
                "The settings activity is not hosting a preference fragment, it holds ${fragment?.javaClass?.name}"
            )
    }

    private fun collectLeaves(group: PreferenceGroup, into: MutableList<SettingsRow>) {
        for (index in 0 until group.preferenceCount) {
            when (val child = group.getPreference(index)) {
                is PreferenceCategory -> collectLeaves(child, into)
                is PreferenceGroup -> collectLeaves(child, into)
                else -> into.add(child.toRow())
            }
        }
    }

    private fun collectCategories(group: PreferenceGroup, into: MutableList<String>) {
        for (index in 0 until group.preferenceCount) {
            val child = group.getPreference(index)
            if (child is PreferenceCategory) {
                into.add(child.title?.toString().orEmpty())
                collectCategories(child, into)
            } else if (child is PreferenceGroup) {
                collectCategories(child, into)
            }
        }
    }

    private fun findPreference(group: PreferenceGroup, title: String): Preference? {
        for (index in 0 until group.preferenceCount) {
            val child = group.getPreference(index)
            if (child !is PreferenceCategory && child.title?.toString() == title) return child
            if (child is PreferenceGroup) findPreference(child, title)?.let { return it }
        }
        return null
    }

    private fun Preference.toRow(): SettingsRow = SettingsRow(
        key = key,
        title = title?.toString().orEmpty(),
        summary = summary?.toString(),
        enabled = isEnabled,
        selectable = isSelectable,
        checked = (this as? TwoStatePreference)?.isChecked,
        widgetLayoutResource = widgetLayoutResource,
        className = javaClass.name,
    )

    private fun upArrowDescriptions(): List<String> = listOf(
        TestEnvironment.targetContext.getString(androidx.appcompat.R.string.abc_action_bar_up_description),
        "Navigate up",
    )

    private fun boundsOf(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException("A settings view has no size on screen, so it cannot be tapped")
        }
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }
}
