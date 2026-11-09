// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.home.BreadcrumbAdapter
import com.gitlab.mudlej.MjPdfReader.ui.home.FolderAdapter
import com.gitlab.mudlej.MjPdfReader.ui.home.FolderNode
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeItem
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSection
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeSectionsAdapter
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.home.LibraryAdapter

class HomePage(val tab: HomeTab) {

    fun items(): List<HomeItem> = TestEnvironment.onMainThread {
        libraryAdapter(recycler()).currentList.toList()
    }

    fun titles(): List<String> = items().map { it.title }

    fun itemCount(): Int = items().size
    data class VisibleAnchor(val stableKey: String, val pixelOffset: Int)

    fun scrollToStableKey(stableKey: String, pixelOffset: Int = 0) {
        TestEnvironment.onMainThread {
            val recycler = recycler()
            val index = libraryAdapter(recycler).currentList.indexOfFirst { it.stableKey == stableKey }
            require(index >= 0) { "No $tab item has stable key $stableKey" }
            val sectionCount = sectionAdapters(recycler).sumOf { it.itemCount }
            (recycler.layoutManager as GridLayoutManager)
                .scrollToPositionWithOffset(sectionCount + index, pixelOffset)
        }
    }

    fun firstVisibleAnchor(): VisibleAnchor? = TestEnvironment.onMainThread {
        val recycler = recycler()
        val manager = recycler.layoutManager as GridLayoutManager
        val sectionCount = sectionAdapters(recycler).sumOf { it.itemCount }
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return@onMainThread null
        }
        val adapterPosition = (maxOf(first, sectionCount)..last)
            .firstOrNull { manager.findViewByPosition(it) != null }
            ?: return@onMainThread null
        val item = libraryAdapter(recycler).currentList.getOrNull(adapterPosition - sectionCount)
            ?: return@onMainThread null
        VisibleAnchor(item.stableKey, manager.findViewByPosition(adapterPosition)?.top ?: 0)
    }

    fun hasItem(title: String): Boolean = items().any { it.title == title }

    fun item(title: String): HomeItem = items().firstOrNull { it.title == title }
        ?: throw AssertionError("The $tab tab holds no item titled \"$title\". It holds: ${titles()}")

    fun sections(): List<HomeSection> = TestEnvironment.onMainThread {
        sectionAdapters(recycler()).flatMap { it.currentList }
    }

    fun emptyState(): HomeSection.EmptyState? =
        sections().filterIsInstance<HomeSection.EmptyState>().firstOrNull()

    fun permissionCard(): HomeSection.PermissionCard? =
        sections().filterIsInstance<HomeSection.PermissionCard>().firstOrNull()

    fun hasScanSetupCard(): Boolean = sections().any { it is HomeSection.ScanSetupCard }

    fun hasChipRow(): Boolean = sections().any { it is HomeSection.Chips }

    fun scanProgress(): HomeSection.ScanProgressRow? =
        sections().filterIsInstance<HomeSection.ScanProgressRow>().firstOrNull()

    fun heroItems(): List<HomeItem> =
        sections().filterIsInstance<HomeSection.Hero>().firstOrNull()?.items.orEmpty()

    fun heroTitles(): List<String> = heroItems().map { it.title }

    fun hasHero(): Boolean = sections().any { it is HomeSection.Hero }

    fun folders(): List<FolderNode> = TestEnvironment.onMainThread {
        folderAdapter(recycler())?.currentList?.toList().orEmpty()
    }

    fun folderNames(): List<String> = folders().map { it.name }

    fun folder(name: String): FolderNode = folders().firstOrNull { it.name == name }
        ?: throw AssertionError("The $tab tab shows no folder named \"$name\". It shows: ${folderNames()}")

    fun crumbs(): List<String> = TestEnvironment.onMainThread {
        crumbViews(recycler()).map { it.text.toString() }
    }

    fun crumbIsClickable(label: String): Boolean = TestEnvironment.onMainThread {
        crumbViews(recycler()).firstOrNull { it.text.toString() == label }?.isClickable == true
    }

    fun crumbIsBold(label: String): Boolean = TestEnvironment.onMainThread {
        crumbViews(recycler()).firstOrNull { it.text.toString() == label }?.typeface?.isBold == true
    }

    fun cell(title: String): HomeCell {
        val list = items()
        val index = list.indexOfFirst { it.title == title }
        if (index < 0) {
            throw AssertionError("The $tab tab holds no item titled \"$title\". It holds: ${list.map { it.title }}")
        }
        return HomeCell(this, list[index])
    }

    fun cellAt(index: Int): HomeCell {
        val list = items()
        if (index !in list.indices) {
            throw AssertionError("The $tab tab holds ${list.size} item(s), so index $index is out of range")
        }
        return HomeCell(this, list[index])
    }

    fun tapFolder(name: String): HomePage {
        val node = folder(name)
        val previousCrumbs = crumbs()
        Taps.tapCentreOfAndAwait(
            view = folderViewOf(node),
            condition = "the $tab breadcrumb trail enters folder \"$name\"",
        ) {
            val current = crumbs()
            current != previousCrumbs && current.lastOrNull() == name
        }
        return this
    }

    fun activateFolderForSetup(
        name: String,
        expectedItems: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): HomePage {
        val node = folder(name)
        val previousCrumbs = crumbs()
        val row = folderViewOf(node)
        val clicked = TestEnvironment.onMainThread {
            row.findViewById<View>(R.id.folderCard)?.takeIf { it.isShown }?.performClick() == true
        }
        if (!clicked) {
            throw ConditionTimeoutException("The shown folder row \"$name\" did not accept performClick")
        }
        Await.until(
            "the $tab tab enters folder \"$name\" and lists exactly $expectedItems item(s)",
            timeoutMs,
        ) {
            TestEnvironment.onMainThread {
                val view = recycler()
                val breadcrumb = subAdapters(view).filterIsInstance<BreadcrumbAdapter>().firstOrNull()
                if (breadcrumb != null && breadcrumb.itemCount > 0) {
                    val position = offsetOf(view, breadcrumb)
                    if (view.findViewHolderForAdapterPosition(position) == null) {
                        view.scrollToPosition(position)
                        return@onMainThread false
                    }
                }
                val current = crumbViews(view).map { it.text.toString() }
                current != previousCrumbs && current.lastOrNull() == name &&
                    libraryAdapter(view).currentList.size == expectedItems
            }
        }
        return this
    }

    fun openFolderPath(vararg names: String): HomePage {
        names.forEach { name ->
            Await.until("the folder row \"$name\" is listed on the $tab tab") { name in folderNames() }
            tapFolder(name)
        }
        return this
    }

    fun tapCrumb(label: String): HomePage {
        val crumb = TestEnvironment.onMainThread {
            val views = crumbViews(recycler())
            views.firstOrNull { it.text.toString() == label }
                ?: throw AssertionError(
                    "No breadcrumb reads \"$label\". The trail reads: ${views.map { it.text.toString() }}"
                )
        }
        Taps.tapCentreOf(crumb)
        return this
    }

    fun spanCount(): Int = TestEnvironment.onMainThread {
        (recycler().layoutManager as? GridLayoutManager)?.spanCount
            ?: throw AssertionError("The $tab tab does not use a GridLayoutManager, so it has no span count")
    }

    fun pullToRefresh(): HomePage {
        TestEnvironment.onMainThread { recycler().scrollToPosition(0) }
        TestEnvironment.waitForIdleSync()
        val pull = TestEnvironment.onMainThread {
            val page = pageView()
            val location = IntArray(2)
            page.getLocationOnScreen(location)
            PointF(
                location[0] + page.width / 2f,
                location[1] + page.height * PULL_START_FRACTION,
            ) to page.height * PULL_DISTANCE_FRACTION
        }
        Taps.dragDown(pull.first, pull.second)
        return this
    }

    fun isRefreshing(): Boolean = TestEnvironment.onMainThread { swipeRefresh().isRefreshing }

    fun awaitRefreshFinished(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the $tab pull to refresh spinner stops", timeoutMs) { !isRefreshing() }
    }

    fun awaitTitles(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<String> =
        Await.untilValue("the $tab tab lists exactly $expected", timeoutMs, read = { titles() }) { it == expected }

    fun awaitItemCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<HomeItem> =
        Await.untilValue("the $tab tab lists exactly $expected item(s)", timeoutMs, read = { items() }) {
            it.size == expected
        }

    fun awaitContains(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the $tab tab lists \"$title\"", timeoutMs) { hasItem(title) }
    }

    fun awaitAbsent(title: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the $tab tab no longer lists \"$title\"", timeoutMs) { !hasItem(title) }
    }

    fun awaitFolders(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<String> =
        Await.untilValue("the $tab tab shows folders $expected", timeoutMs, read = { folderNames() }) {
            it == expected
        }

    fun awaitCrumbs(expected: List<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<String> =
        Await.untilValue("the $tab breadcrumb trail reads $expected", timeoutMs, read = { crumbs() }) {
            it == expected
        }

    fun awaitEmptyState(titleRes: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): HomeSection.EmptyState =
        Await.untilValue(
            "the $tab tab shows the empty state titled ${resourceName(titleRes)}",
            timeoutMs,
            read = { emptyState() },
        ) { it != null && it.titleRes == titleRes }!!

    internal fun itemViewOf(index: Int): View {
        val list = items()
        if (index !in list.indices) {
            throw AssertionError("The $tab tab holds ${list.size} item(s), so index $index is out of range")
        }
        return itemViewOf(list[index].stableKey)
    }

    internal fun itemViewOf(stableKey: String): View {
        var requestedPosition = TestEnvironment.onMainThread {
            val view = recycler()
            val adapter = libraryAdapter(view)
            val index = adapter.currentList.indexOfFirst { it.stableKey == stableKey }
            if (index < 0) {
                throw AssertionError("The $tab tab holds no item with stable key $stableKey")
            }
            offsetOf(view, adapter) + index
        }
        TestEnvironment.onMainThread { recycler().scrollToPosition(requestedPosition) }
        return Await.untilNotNull("a bound view holder exists for library item $stableKey on the $tab tab") {
            TestEnvironment.onMainThread {
                val view = recycler()
                val adapter = libraryAdapter(view)
                val index = adapter.currentList.indexOfFirst { it.stableKey == stableKey }
                if (index < 0) return@onMainThread null
                val position = offsetOf(view, adapter) + index
                if (position != requestedPosition) {
                    view.scrollToPosition(position)
                    requestedPosition = position
                    return@onMainThread null
                }
                val expectedTitle = adapter.currentList[index].title
                view.findViewHolderForAdapterPosition(position)?.itemView?.takeIf {
                    it.isShown && it.findViewById<TextView>(R.id.title)?.text?.toString() == expectedTitle
                }
            }
        }
    }

    private fun folderViewOf(node: FolderNode): View {
        var requestedPosition = TestEnvironment.onMainThread {
            val view = recycler()
            val adapter = requireFolderAdapter(view)
            val index = adapter.currentList.indexOfFirst { it.path == node.path }
            if (index < 0) {
                throw AssertionError("The $tab tab holds no folder with path ${node.path}")
            }
            offsetOf(view, adapter) + index
        }
        TestEnvironment.onMainThread { recycler().scrollToPosition(requestedPosition) }
        return Await.untilNotNull("a bound folder row exists for ${node.path} on the $tab tab") {
            TestEnvironment.onMainThread {
                val view = recycler()
                val adapter = requireFolderAdapter(view)
                val index = adapter.currentList.indexOfFirst { it.path == node.path }
                if (index < 0) return@onMainThread null
                val position = offsetOf(view, adapter) + index
                if (position != requestedPosition) {
                    view.scrollToPosition(position)
                    requestedPosition = position
                    return@onMainThread null
                }
                view.findViewHolderForAdapterPosition(position)?.itemView?.takeIf {
                    it.isShown &&
                        it.findViewById<TextView>(R.id.folderName)?.text?.toString() == node.name
                }
            }
        }
    }

    private fun crumbViews(recycler: RecyclerView): List<TextView> {
        val adapter = subAdapters(recycler).filterIsInstance<BreadcrumbAdapter>().firstOrNull()
            ?: return emptyList()
        if (adapter.itemCount == 0) return emptyList()
        val holder = recycler.findViewHolderForAdapterPosition(offsetOf(recycler, adapter)) ?: return emptyList()
        val container = holder.itemView.findViewById<ViewGroup>(R.id.breadcrumbContainer) ?: return emptyList()
        return (0 until container.childCount).mapNotNull { container.getChildAt(it) as? TextView }
    }

    private fun swipeRefresh(): SwipeRefreshLayout {
        val page = pageView()
        return page as? SwipeRefreshLayout
            ?: page.findViewById(R.id.pageSwipeRefresh)
            ?: throw ConditionTimeoutException("The $tab page holds no SwipeRefreshLayout")
    }

    private fun recycler(): RecyclerView = pageView().findViewById(R.id.pageRecyclerView)
        ?: throw ConditionTimeoutException("The $tab page holds no view with id pageRecyclerView")

    private fun pageView(): View {
        val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java)
            ?: throw ConditionTimeoutException("No HomeActivity is resumed, so the $tab page cannot be read")
        val pager = activity.findViewById<ViewPager2>(R.id.homePager)
            ?: throw ConditionTimeoutException("HomeActivity holds no view with id homePager")
        val inner = pager.getChildAt(0) as? RecyclerView
            ?: throw ConditionTimeoutException("ViewPager2 no longer exposes an inner RecyclerView as its first child")
        for (index in 0 until inner.childCount) {
            val child = inner.getChildAt(index)
            if (inner.getChildAdapterPosition(child) == tab.ordinal) return child
        }
        throw ConditionTimeoutException(
            "The $tab page is not attached. offscreenPageLimit is set to keep all ${HomeTab.entries.size} pages " +
                "attached, so either the pager was rebuilt or the tab order changed."
        )
    }

    private fun libraryAdapter(recycler: RecyclerView): LibraryAdapter =
        subAdapters(recycler).filterIsInstance<LibraryAdapter>().firstOrNull()
            ?: throw ConditionTimeoutException(
                "The $tab page has no LibraryAdapter. Its adapters are: " +
                    subAdapters(recycler).joinToString { it::class.java.simpleName }
            )

    private fun sectionAdapters(recycler: RecyclerView): List<HomeSectionsAdapter> =
        subAdapters(recycler).filterIsInstance<HomeSectionsAdapter>()

    private fun folderAdapter(recycler: RecyclerView): FolderAdapter? =
        subAdapters(recycler).filterIsInstance<FolderAdapter>().firstOrNull()

    private fun requireFolderAdapter(recycler: RecyclerView): FolderAdapter =
        folderAdapter(recycler) ?: throw ConditionTimeoutException("The $tab page has no FolderAdapter")

    private fun subAdapters(recycler: RecyclerView): List<RecyclerView.Adapter<*>> {
        val adapter = recycler.adapter
            ?: throw ConditionTimeoutException("The $tab page RecyclerView has no adapter yet")
        return if (adapter is ConcatAdapter) adapter.adapters else listOf(adapter)
    }

    private fun offsetOf(recycler: RecyclerView, target: RecyclerView.Adapter<*>): Int {
        var offset = 0
        for (sub in subAdapters(recycler)) {
            if (sub === target) return offset
            offset += sub.itemCount
        }
        throw ConditionTimeoutException(
            "${target::class.java.simpleName} is not part of the $tab page's ConcatAdapter"
        )
    }

    private fun resourceName(viewId: Int): String =
        runCatching { TestEnvironment.targetContext.resources.getResourceEntryName(viewId) }
            .getOrDefault(viewId.toString())

    private companion object {
        const val PULL_START_FRACTION = 0.15f
        const val PULL_DISTANCE_FRACTION = 0.55f
    }
}
