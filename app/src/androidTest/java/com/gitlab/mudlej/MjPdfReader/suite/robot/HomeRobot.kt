// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.test.runner.lifecycle.Stage
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.viewpager2.widget.ViewPager2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.state.HomeState
import com.gitlab.mudlej.MjPdfReader.suite.state.HomeStateSource
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeItem
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryAdapter
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import com.google.android.material.search.SearchView
import com.google.android.material.tabs.TabLayout
import org.hamcrest.CoreMatchers.allOf

class HomeRobot : Robot() {

    fun launchFromLauncher(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): HomeRobot {
        startFromLauncher(timeoutMs)
        return awaitShownAndReturn(timeoutMs)
    }

    fun launchFromLauncher(
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        consumeExpectedModal: () -> Unit,
    ): HomeRobot {
        startFromLauncher(timeoutMs)
        consumeExpectedModal()
        return awaitShownAndReturn(timeoutMs)
    }

    fun startFromLauncher(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): HomeRobot {
        sendLauncherIntent()
        awaitAppInForeground(timeoutMs)
        return this
    }

    fun sendLauncherIntent(): HomeRobot = sendLauncherIntent { }

    fun sendLauncherIntent(configure: (Intent) -> Unit): HomeRobot {
        val context = TestEnvironment.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            ?: throw ConditionTimeoutException("No launcher intent is declared for $targetPackage")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        configure(intent)
        context.startActivity(intent)
        return this
    }

    fun resumeExistingTask(timeoutMs: Long = Await.LONG_TIMEOUT_MS): HomeRobot {
        val context = TestEnvironment.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            ?: throw ConditionTimeoutException("No launcher intent is declared for $targetPackage")
        intent.flags = (intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK) and Intent.FLAG_ACTIVITY_CLEAR_TASK.inv()
        context.startActivity(intent)
        awaitAppInForeground(timeoutMs)
        return awaitShownAndReturn(timeoutMs)
    }

    fun resumeExistingTaskAfter(
        baseline: HomeState,
        expectedStorageAccess: Boolean,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): HomeState {
        val baselineGeneration = baseline.generation
            ?: throw ConditionTimeoutException("The baseline Home state has no render generation: $baseline")
        val baselineAccessRevision = baseline.accessRevision
            ?: throw ConditionTimeoutException("The baseline Home state has no access revision: $baseline")
        val baselineStorageAccess = baseline.storageAccess
            ?: throw ConditionTimeoutException("The baseline Home state has no storage access value: $baseline")
        val expectedAccessRevision = baselineAccessRevision +
            if (expectedStorageAccess == baselineStorageAccess) 0L else 1L

        val foregroundHome = Screens.awaitResumed(HomeActivity::class.java, timeoutMs)
        check(device.pressHome()) { "The system did not accept the Home key" }
        Screens.awaitStage(foregroundHome, Stage.STOPPED, timeoutMs)
        resumeExistingTask(timeoutMs)
        return awaitState(
            "the existing Home task commits generation after $baselineGeneration for access revision " +
                "$expectedAccessRevision with storage access $expectedStorageAccess",
            timeoutMs,
        ) {
            (it.generation ?: 0) > baselineGeneration &&
                it.accessRevision == expectedAccessRevision &&
                it.storageAccess == expectedStorageAccess
        }
    }

    fun awaitAppInForeground(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        val appeared = device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), timeoutMs)
        if (appeared != true) {
            throw ConditionTimeoutException("Timed out after ${timeoutMs}ms waiting for $targetPackage to come to the foreground")
        }
    }

    override fun awaitShown(timeoutMs: Long) {
        Await.until("the resumed Home activity displays its pager", timeoutMs) { isShown() }
    }

    fun awaitShownAndReturn(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): HomeRobot {
        awaitShown(timeoutMs)
        return this
    }

    fun isShown(): Boolean = onMain {
        viewOrNull<ViewPager2>(R.id.homePager)?.let { pager -> isDisplayed().matches(pager) } == true
    }

    fun assertShown(): HomeRobot {
        assertDisplayed(R.id.homePager)
        assertDisplayed(R.id.homeTabs)
        assertDisplayed(R.id.searchBar)
        return this
    }

    fun openOverflowMenu(): HomeRobot {
        val handled = Await.untilValue(
            condition = "the Home Library Menu action is shown, enabled, and clickable",
            read = {
                onMain {
                    val action = viewOrNull<View>(R.id.homeMenuOption) ?: return@onMain null
                    if (!action.isShown || !action.isEnabled || !action.isClickable) return@onMain null
                    action.performClick()
                }
            },
            predicate = { it != null },
        )
        if (handled != true) {
            throw ConditionTimeoutException("Home did not handle the Library Menu action")
        }
        Dialogs.awaitViewOnScreen(R.id.homeMenuScroll)
        return this
    }

    fun tapMenuRow(rowId: Int): HomeRobot {
        Await.untilNoThrow("the home menu can scroll to and tap row $rowId") {
            view(rowId).inRoot(isDialog()).perform(ViewActions.scrollTo(), ViewActions.click())
        }
        waitForIdle()
        return this
    }

    fun isMenuRowDisplayed(rowId: Int): Boolean = isDisplayed(rowId)

    fun awaitMenuRowGone(rowId: Int): HomeRobot {
        awaitGone(rowId)
        return this
    }

    fun dismissOverflowMenu(): HomeRobot {
        pressBack()
        awaitGone(R.id.homeMenuScroll)
        return this
    }

    fun openSearch(): HomeRobot {
        click(R.id.searchBar)
        awaitDisplayed(R.id.searchResultsRecyclerView)
        return this
    }

    fun readStateOrNull(): HomeState? = HomeStateSource.read()

    fun readState(): HomeState = readStateOrNull()
        ?: throw ConditionTimeoutException(
            "The home state hook produced nothing. Either home is not resumed, or the debug hook is absent from this build."
        )

    fun awaitState(
        condition: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        predicate: (HomeState) -> Boolean,
    ): HomeState = Await.untilValue(
        condition = condition,
        timeoutMs = timeoutMs,
        read = { readStateOrNull() },
        predicate = { it != null && predicate(it) },
    )!!

    fun awaitRendered(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): HomeState =
        awaitState("home publishes a render generation", timeoutMs) {
            it.hasHomeGroup && (it.generation ?: 0) >= 1
        }

    fun awaitGenerationAtLeast(generation: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): HomeState =
        awaitState("home reaches render generation $generation", timeoutMs) {
            (it.generation ?: 0) >= generation
        }

    fun generation(): Int = readState().generation ?: 0

    fun awaitScanLoaded(timeoutMs: Long = Await.LONG_TIMEOUT_MS): HomeState =
        awaitState("the library index reports loaded", timeoutMs) { it.scanLoaded == true }

    fun awaitScanCommitted(index: LibraryIndex, timeoutMs: Long = Await.LONG_TIMEOUT_MS): HomeState =
        awaitState("Home commits the current completed backend generation at or after ${index.generation}", timeoutMs) {
            val current = ScanLifecycle.snapshot()
            current.loaded && current.phase == ScanPhase.IDLE && current.sourceFailures.isEmpty() &&
                current.generation >= index.generation && current.revision >= index.revision &&
                it.scanGeneration == current.generation && it.backendRevision == current.revision &&
                it.scanPhase == ScanPhase.IDLE.name && it.scanning == false && it.showProgress == false &&
                (it.generation ?: 0) > 0 && (it.uiRevision ?: 0L) > 0L
        }


    fun awaitGenerationStable(stableForMs: Long = QUIET_RENDER_WINDOW_MS, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int {
        var last = generation()
        Await.untilTrueContinuously(
            condition = "the home render generation stops advancing, last seen $last",
            stableForMs = stableForMs,
            timeoutMs = timeoutMs,
            pollMs = 100L,
        ) {
            val now = generation()
            val stable = now == last
            last = now
            stable
        }
        return last
    }

    fun page(tab: HomeTab): HomePage = HomePage(tab)

    fun recent(): HomePage = page(HomeTab.RECENT)

    fun library(): HomePage = page(HomeTab.LIBRARY)

    fun folders(): HomePage = page(HomeTab.FOLDERS)

    fun tabTexts(): List<String> = onMain {
        val tabs = requireView<TabLayout>(R.id.homeTabs)
        (0 until tabs.tabCount).map { tabs.getTabAt(it)?.text?.toString().orEmpty() }
    }

    fun selectedTabIndex(): Int = onMain { requireView<TabLayout>(R.id.homeTabs).selectedTabPosition }

    fun currentPagerItem(): Int = onMain { requireView<ViewPager2>(R.id.homePager).currentItem }

    fun awaitPagerItem(tab: HomeTab, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the home pager settles on ${tab.name}", timeoutMs) {
            onMain {
                val pager = requireView<ViewPager2>(R.id.homePager)
                pager.currentItem == tab.ordinal &&
                    requireView<TabLayout>(R.id.homeTabs).selectedTabPosition == tab.ordinal &&
                    pager.scrollState == ViewPager2.SCROLL_STATE_IDLE && !pager.isLayoutRequested
            }
        }
    }

    fun selectTab(tab: HomeTab): HomePage {
        onMain {
            val tabs = requireView<TabLayout>(R.id.homeTabs)
            tabs.getTabAt(tab.ordinal)?.select()
                ?: throw ConditionTimeoutException("Home carries no ${tab.name} tab at index ${tab.ordinal}")
        }
        awaitPagerItem(tab)
        waitForIdle()
        return page(tab)
    }

    fun searchResultsAdapter(): LibraryAdapter = onMain {
        requireView<RecyclerView>(R.id.searchResultsRecyclerView).adapter as LibraryAdapter
    }

    fun searchResults(): List<HomeItem> = onMain { searchResultsAdapter().currentList.toList() }

    fun searchTitles(): List<String> = searchResults().map { it.title }

    fun awaitSearchTitles(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<String> =
        Await.untilValue(
            condition = "the search results read $expected",
            timeoutMs = timeoutMs,
            read = { searchTitles() },
        ) { it == expected }

    fun searchResultView(title: String): View {
        val position = onMain {
            val index = searchResultsAdapter().currentList.indexOfFirst { it.title == title }
            if (index < 0) {
                throw AssertionError("No search result reads \"$title\". They read: ${searchTitles()}")
            }
            index
        }
        val recycler = onMain { requireView<RecyclerView>(R.id.searchResultsRecyclerView) }
        onMain { recycler.scrollToPosition(position) }
        waitForIdle()
        return Await.untilNotNull("the search result \"$title\" is bound") {
            onMain { recycler.findViewHolderForAdapterPosition(position)?.itemView }
        }
    }

    fun tapSearchResult(title: String): HomeRobot {
        val row = searchResultView(title)
        Taps.tapCentreOf(onMain { row.findViewById(R.id.listCard) ?: row })
        return this
    }

    fun longPressSearchResult(title: String): HomeRobot {
        val row = searchResultView(title)
        Taps.longPressCentreOf(onMain { row.findViewById(R.id.listCard) ?: row })
        return this
    }

    fun typeSearchQuery(query: String): HomeRobot {
        onView(allOf(withId(com.google.android.material.R.id.open_search_view_edit_text), isDisplayed()))
            .perform(ViewActions.replaceText(query))
        waitForIdle()
        return this
    }

    fun clearSearchQuery(): HomeRobot = typeSearchQuery("")

    fun isSearchShowing(): Boolean = onMain { requireView<SearchView>(R.id.searchView).isShowing }

    fun awaitSearchClosed(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the search view is closed and the search bar is back", timeoutMs) {
            !isSearchShowing() && isSearchBarShown()
        }
    }

    fun isSelectionToolbarShown(): Boolean =
        onMain { viewOrNull<View>(R.id.selectionToolbar)?.visibility == View.VISIBLE }

    fun isSearchBarShown(): Boolean =
        onMain { viewOrNull<View>(R.id.searchBar)?.visibility == View.VISIBLE }

    fun awaitSelectionStarted(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the selection toolbar replaces the search bar", timeoutMs) {
            isSelectionToolbarShown() && !isSearchBarShown()
        }
    }

    fun awaitSelectionEnded(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the selection toolbar is gone and the search bar is back", timeoutMs) {
            !isSelectionToolbarShown() && isSearchBarShown()
        }
    }

    fun selectionToolbarTitle(): String? =
        onMain { viewOrNull<Toolbar>(R.id.selectionToolbar)?.title?.toString() }

    fun awaitSelectionTitle(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the selection toolbar reads \"$expected\"",
            timeoutMs = timeoutMs,
            read = { selectionToolbarTitle() },
        ) { it == expected }
    }

    fun selectionMenuIds(): List<Int> = onMain {
        val toolbar = viewOrNull<Toolbar>(R.id.selectionToolbar) ?: return@onMain emptyList()
        val menu = toolbar.menu
        (0 until menu.size()).filter { menu.getItem(it).isVisible }.map { menu.getItem(it).itemId }
    }

    fun tapSelectionAction(@IdRes itemId: Int): HomeRobot {
        onMain { requireView<Toolbar>(R.id.selectionToolbar).menu.performIdentifierAction(itemId, 0) }
        waitForIdle()
        return this
    }

    fun isActionModeShown(): Boolean = onMain {
        val bar = viewOrNull<View>(androidx.appcompat.R.id.action_mode_bar) ?: return@onMain false
        bar.visibility == View.VISIBLE
    }

    fun awaitActionModeShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("an action mode bar is showing", timeoutMs) { isActionModeShown() }
    }

    fun actionModeMenuIds(): List<Int> = onMain {
        val bar = viewOrNull<ViewGroup>(androidx.appcompat.R.id.action_mode_bar)
            ?: return@onMain emptyList()
        listOf(
            R.id.statusBatchOption,
            R.id.removeRecentBatchOption,
            R.id.hideBatchOption,
            R.id.deleteBatchOption,
        ).filter { itemId -> bar.findViewById<View>(itemId)?.isShown == true }
    }

    fun tapChip(@IdRes chipId: Int): HomeRobot {
        onView(allOf(withId(chipId), isDisplayed())).perform(ViewActions.click())
        waitForIdle()
        return this
    }

    fun tapVisible(@IdRes viewId: Int): HomeRobot {
        onView(allOf(withId(viewId), isDisplayed())).perform(ViewActions.click())
        waitForIdle()
        return this
    }

    fun isVisible(@IdRes viewId: Int): Boolean = isDisplayed(viewId)


    private fun <T> onMain(block: () -> T): T = TestEnvironment.onMainThread(block)

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> viewOrNull(@IdRes viewId: Int): T? {
        val activity = TestEnvironment.resumedActivityOfType(
            com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity::class.java
        ) ?: return null
        return activity.findViewById<View>(viewId) as? T
    }

    private fun <T : View> requireView(@IdRes viewId: Int): T = viewOrNull<T>(viewId)
        ?: throw ConditionTimeoutException("View ${resourceName(viewId)} is not in the resumed HomeActivity")

    private companion object {
        const val QUIET_RENDER_WINDOW_MS = 1_500L
    }
}
