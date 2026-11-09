// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import android.app.Activity
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

data class ScanSnapshot(
    val itemCount: Int,
    val scanning: Boolean,
    val messageShown: Boolean,
    val progress: Int,
    val max: Int,
    val indeterminateShown: Boolean,
)

abstract class AnnotationListRobot(
    private val screen: Class<out Activity>,
    @IdRes private val recyclerId: Int,
) {

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitResumed(screen, timeoutMs)
    }

    fun isShown(): Boolean = TestEnvironment.resumedActivityOfType(screen) != null

    fun awaitClosed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Screens.awaitGone(screen, timeoutMs)
    }

    fun activity(): Activity = TestEnvironment.resumedActivityOfType(screen)
        ?: throw ConditionTimeoutException(
            "No resumed ${screen.simpleName}. The resumed activity is ${Screens.resumedName()}"
        )

    fun awaitScanFinished(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        awaitShown(timeoutMs)
        Await.until("the scan progress bar is gone, which is the end of scan signal", timeoutMs) {
            !scanProgressVisible()
        }
    }

    fun scanProgressVisible(): Boolean = viewVisible(R.id.scanProgressBar)

    fun indeterminateProgressVisible(): Boolean = viewVisible(R.id.progressBar)

    fun scanProgress(): Int = TestEnvironment.onMainThread {
        requireView<ProgressBar>(R.id.scanProgressBar).progress
    }

    fun messageVisible(): Boolean = viewVisible(R.id.message)

    fun messageText(): String? = TestEnvironment.onMainThread {
        requireView<TextView>(R.id.message).text?.toString()
    }

    fun itemCount(): Int = TestEnvironment.onMainThread { recycler().adapter?.itemCount ?: 0 }

    fun items(): List<SweptHighlight> = TestEnvironment.onMainThread {
        val adapter = recycler().adapter
            ?: throw ConditionTimeoutException("${screen.simpleName} has no list adapter yet")
        @Suppress("UNCHECKED_CAST")
        val listAdapter = adapter as? ListAdapter<SweptHighlight, *>
            ?: throw ConditionTimeoutException(
                "${screen.simpleName} binds a ${adapter::class.java.simpleName} rather than the UserNoteAdapter " +
                    "whose current list is the ground truth these tests read"
            )
        listAdapter.currentList.toList()
    }

    fun groupKeys(): List<String> = items().map { it.groupKey }

    fun pageIndexes(): List<Int> = items().map { it.pageIndex }

    fun awaitItemCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the list adapter holds $expected rows",
            timeoutMs = timeoutMs,
            read = { itemCount() },
        ) { it == expected }
    }

    fun scrollTo(position: Int) {
        TestEnvironment.onMainThread { recycler().scrollToPosition(position) }
        TestEnvironment.waitForIdleSync()
        Await.until("row $position is bound after scrolling to it") { itemView(position) != null }
    }

    fun rowPrimaryText(position: Int): String = rowText(position, R.id.primaryText)

    fun rowSecondaryText(position: Int): String = rowText(position, R.id.secondaryText)

    fun rowSecondaryVisible(position: Int): Boolean = rowChildVisible(position, R.id.secondaryText)

    fun rowSecondaryIsItalic(position: Int): Boolean = TestEnvironment.onMainThread {
        requireRowChild<TextView>(position, R.id.secondaryText).typeface?.isItalic == true
    }

    fun rowPagePill(position: Int): String = rowText(position, R.id.notePagePill)

    fun rowLocationPill(position: Int): String = rowText(position, R.id.noteLocationPill)

    fun rowLocationVisible(position: Int): Boolean = rowChildVisible(position, R.id.noteLocationPill)

    fun rowDate(position: Int): String = rowText(position, R.id.noteDate)

    fun rowDateVisible(position: Int): Boolean = rowChildVisible(position, R.id.noteDate)

    fun rowExpandArrowVisibility(position: Int): Int = TestEnvironment.onMainThread {
        requireRowChild<ImageView>(position, R.id.expandArrow).visibility
    }

    fun rowColorStrip(position: Int): Int = TestEnvironment.onMainThread {
        val strip = requireRowChild<View>(position, R.id.colorStrip)
        val background = strip.background
            ?: throw ConditionTimeoutException("Row $position has no colour strip background")
        val drawable = background as? GradientDrawable
            ?: throw ConditionTimeoutException(
                "Row $position paints its colour strip with a ${background::class.java.simpleName}, " +
                    "not the GradientDrawable UserNoteAdapter.bind builds"
            )
        drawable.color?.defaultColor
            ?: throw ConditionTimeoutException("Row $position has a colour strip with no fill colour")
    }

    fun allPagePills(): List<String> = eachRow { rowPagePill(it) }

    fun allPrimaryTexts(): List<String> = eachRow { rowPrimaryText(it) }

    fun allColorStrips(): List<Int> = eachRow { rowColorStrip(it) }

    fun positionOfPagePill(pill: String): Int {
        val pills = allPagePills()
        val index = pills.indexOf(pill)
        if (index < 0) {
            throw ConditionTimeoutException("No row reads \"$pill\". The rows read $pills")
        }
        return index
    }

    fun tapRow(position: Int) = tapRowChild(position, R.id.primaryText)

    fun tapExpandArrow(position: Int) {
        tapRowChild(position, R.id.expandArrow)
        TestEnvironment.waitForIdleSync()
    }

    fun colorFilterRowExists(): Boolean = TestEnvironment.onMainThread {
        activity().findViewById<View>(R.id.colorFilterScroll) != null
    }

    fun colorFilterVisible(): Boolean = viewVisible(R.id.colorFilterScroll)

    fun scanSnapshot(): ScanSnapshot = TestEnvironment.onMainThread {
        val bar = requireView<ProgressBar>(R.id.scanProgressBar)
        ScanSnapshot(
            itemCount = recycler().adapter?.itemCount ?: 0,
            scanning = bar.visibility == View.VISIBLE,
            messageShown = requireView<View>(R.id.message).visibility == View.VISIBLE,
            progress = bar.progress,
            max = bar.max,
            indeterminateShown = requireView<View>(R.id.progressBar).visibility == View.VISIBLE,
        )
    }

    fun scanSnapshotOrNull(): ScanSnapshot? {
        if (TestEnvironment.resumedActivityOfType(screen) == null) return null
        return runCatching { scanSnapshot() }.getOrNull()
    }

    fun colorChipCount(): Int = TestEnvironment.onMainThread {
        requireView<ChipGroup>(R.id.colorFilterChips).childCount
    }

    fun colorChipLabels(): List<String> = TestEnvironment.onMainThread {
        val group = requireView<ChipGroup>(R.id.colorFilterChips)
        (0 until group.childCount).map { (group.getChildAt(it) as Chip).text.toString() }
    }

    fun isChipChecked(label: String): Boolean = TestEnvironment.onMainThread {
        chipNamed(label).isChecked
    }

    fun tapChip(label: String) {
        val bounds = TestEnvironment.onMainThread {
            val chip = chipNamed(label)
            val location = IntArray(2)
            chip.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + chip.width, location[1] + chip.height)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    fun openSearch() {
        onView(withId(R.id.search_in_search_activity)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun typeQuery(query: String) {
        onView(withId(androidx.appcompat.R.id.search_src_text))
            .perform(replaceText(query), closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun closeSearch() {
        onView(withId(androidx.appcompat.R.id.search_close_btn)).perform(click())
        TestEnvironment.waitForIdleSync()
        if (isSearchExpanded()) {
            collapseSearchActionView()
            TestEnvironment.waitForIdleSync()
        }
    }

    fun collapseSearchActionView() {
        val bounds = TestEnvironment.onMainThread {
            val toolbar = actionBarToolbar()
            val description = toolbar.collapseContentDescription
                ?: throw ConditionTimeoutException(
                    "The ${screen.simpleName} action bar reports no collapse content description, so the button " +
                        "that collapses an expanded action view cannot be told from the navigation button"
                )
            val button = (0 until toolbar.childCount)
                .map(toolbar::getChildAt)
                .firstOrNull { it.contentDescription == description }
                ?: throw ConditionTimeoutException(
                    "The ${screen.simpleName} action bar carries no button described \"$description\", so the " +
                        "search action view is not expanded or the collapse affordance is missing"
                )
            val location = IntArray(2)
            button.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + button.width, location[1] + button.height)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun isSearchExpanded(): Boolean = runCatching {
        onView(withId(androidx.appcompat.R.id.search_src_text)).check(matches(isDisplayed()))
    }.isSuccess

    fun isSearchIconShown(): Boolean = runCatching {
        onView(withId(R.id.search_in_search_activity)).check(matches(isDisplayed()))
    }.isSuccess

    fun sortByDate() = tapOverflowItem(R.string.sort_by_creation_date)

    fun export() = tapOverflowItem(R.string.export)

    fun overflowItemLabels(): List<String> {
        val candidates = listOf(R.string.sort_by_creation_date, R.string.export)
        val opened = runCatching {
            openActionBarOverflowOrOptionsMenu(activity())
            TestEnvironment.waitForIdleSync()
        }.isSuccess
        if (!opened) return emptyList()
        val shown = candidates.map(::string).filter { label ->
            runCatching { onView(withText(label)).check(matches(isDisplayed())) }.isSuccess
        }
        TestEnvironment.uiDevice.pressBack()
        TestEnvironment.waitForIdleSync()
        return shown
    }

    fun isOverflowItemShown(@StringRes titleRes: Int): Boolean = overflowItemLabels().contains(string(titleRes))

    fun isOverflowItemChecked(@StringRes titleRes: Int): Boolean {
        val item = menuItem(itemIdOf(titleRes))
        return TestEnvironment.onMainThread { item.isChecked }
    }

    fun isMenuItemVisible(@IdRes itemId: Int): Boolean {
        val item = menuItem(itemId)
        return TestEnvironment.onMainThread { item.isVisible }
    }

    fun filteredResultsSnackbarText(count: Int): String =
        string(R.string.number_of_filtered_results).format(count)

    fun pressBack() {
        TestEnvironment.uiDevice.pressBack()
    }

    fun localNightMode(): Int = TestEnvironment.onMainThread {
        (activity() as androidx.appcompat.app.AppCompatActivity).delegate.localNightMode
    }

    fun launchIntentSaysIncognito(): Boolean = TestEnvironment.onMainThread {
        activity().intent.getBooleanExtra(com.gitlab.mudlej.MjPdfReader.pdf.PDF.incognitoKey, false)
    }

    fun resolvedSurfaceColor(): Int = TestEnvironment.onMainThread {
        com.google.android.material.color.MaterialColors.getColor(
            activity(),
            com.google.android.material.R.attr.colorSurface,
            "${screen.simpleName} resolves no colorSurface on its own theme",
        )
    }

    private fun tapOverflowItem(@StringRes titleRes: Int) {
        openActionBarOverflowOrOptionsMenu(activity())
        TestEnvironment.waitForIdleSync()
        onView(withText(string(titleRes))).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun itemIdOf(@StringRes titleRes: Int): Int = when (titleRes) {
        R.string.sort_by_creation_date -> R.id.sort_by_creation_date
        R.string.export -> R.id.export_list
        else -> throw IllegalArgumentException("No menu item is titled ${string(titleRes)} on this screen")
    }

    private fun menuItem(@IdRes itemId: Int) = TestEnvironment.onMainThread {
        val menu = optionsMenu()
        menu.findItem(itemId)
            ?: throw ConditionTimeoutException(
                "The ${screen.simpleName} options menu carries no item ${resourceName(itemId)}"
            )
    }

    private fun optionsMenu(): Menu {
        val host = activity()
        val field = runCatching { host.javaClass.getDeclaredField(MENU_FIELD_NAME) }.getOrNull()
            ?: throw ConditionTimeoutException(
                "${screen.simpleName} no longer holds a field named $MENU_FIELD_NAME, so the inflated options " +
                    "menu cannot be read. These screens use the window action bar rather than a Toolbar view, " +
                    "so the menu is not reachable through the view tree."
            )
        field.isAccessible = true
        return field.get(host) as? Menu
            ?: throw ConditionTimeoutException(
                "${screen.simpleName} has not inflated its options menu yet, so no item visibility can be read"
            )
    }

    private fun chipNamed(label: String): Chip {
        val group = requireView<ChipGroup>(R.id.colorFilterChips)
        return (0 until group.childCount)
            .map { group.getChildAt(it) as Chip }
            .firstOrNull { it.text.toString() == label }
            ?: throw ConditionTimeoutException(
                "The colour filter row carries no chip labelled \"$label\". Its chips read " +
                    (0 until group.childCount).map { (group.getChildAt(it) as Chip).text.toString() }
            )
    }

    private fun <T> eachRow(read: (Int) -> T): List<T> = (0 until itemCount()).map { position ->
        scrollTo(position)
        read(position)
    }

    private fun actionBarToolbar(): Toolbar =
        Dialogs.firstDescendant(activity().window.decorView, Toolbar::class.java)
            ?: throw ConditionTimeoutException(
                "${screen.simpleName} has no AppCompat Toolbar in its decor view, so the window action bar is not " +
                    "the one AppCompat builds and the collapse button cannot be located"
            )

    private fun recycler(): RecyclerView = activity().findViewById(recyclerId)
        ?: throw ConditionTimeoutException("${screen.simpleName} has no list ${resourceName(recyclerId)}")

    private fun itemView(position: Int): View? = TestEnvironment.onMainThread {
        recycler().findViewHolderForAdapterPosition(position)?.itemView
    }

    private fun <T : View> requireRowChild(position: Int, @IdRes viewId: Int): T {
        val row = itemView(position)
            ?: throw ConditionTimeoutException(
                "Row $position is not bound. A RecyclerView binds only a screenful of rows, so scroll to it first"
            )
        return row.findViewById<T>(viewId)
            ?: throw ConditionTimeoutException("Row $position carries no ${resourceName(viewId)}")
    }

    private fun rowText(position: Int, @IdRes viewId: Int): String = TestEnvironment.onMainThread {
        requireRowChild<TextView>(position, viewId).text?.toString().orEmpty()
    }

    private fun rowChildVisible(position: Int, @IdRes viewId: Int): Boolean = TestEnvironment.onMainThread {
        requireRowChild<View>(position, viewId).visibility == View.VISIBLE
    }

    private fun tapRowChild(position: Int, @IdRes viewId: Int) {
        val bounds = TestEnvironment.onMainThread {
            val view = requireRowChild<View>(position, viewId)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun <T : View> requireView(@IdRes viewId: Int): T = activity().findViewById<T>(viewId)
        ?: throw ConditionTimeoutException("${screen.simpleName} has no ${resourceName(viewId)}")

    private fun viewVisible(@IdRes viewId: Int): Boolean = TestEnvironment.onMainThread {
        requireView<View>(viewId).visibility == View.VISIBLE
    }

    fun string(@StringRes resId: Int): String = TestEnvironment.onMainThread { activity().getString(resId) }

    private fun resourceName(@IdRes viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())

    companion object {
        const val MENU_FIELD_NAME = "actionBarMenu"
    }
}

class HighlightsListRobot : AnnotationListRobot(
    com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity::class.java,
    R.id.userHighlightsRecyclerView,
)

class NotesListRobot : AnnotationListRobot(
    com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity::class.java,
    R.id.userNotesRecyclerView,
)
