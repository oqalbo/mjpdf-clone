// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.HomeDebugState
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryViewportCommitTest {
    private lateinit var context: Context
    private lateinit var viewport: LibraryViewport
    private lateinit var adapter: LibraryAdapter
    private lateinit var sections: HomeSectionsAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rows = listOf("z", "a").map {
        HomeItem.fromScan(ScannedPdfEntry("/books/$it.pdf", 100, 1_000, null))
    }
    private val progress = listOf(HomeSection.Chips, HomeSection.ScanProgressRow(ScanProgress.Finding(2)))

    @Before fun setUp() = runBlocking(Dispatchers.Main) {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.HomeTheme,
        )
        val storage = context.getSharedPreferences("viewport-commit-test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val pref = Preferences(storage)
        val covers = CoverCache.getInstance(context)
        val functions = object : HomeItemFunctions {
            override fun onItemClicked(item: HomeItem) = Unit
            override fun onItemLongClicked(item: HomeItem) = true
            override fun onItemOptionsClicked(item: HomeItem) = Unit
        }
        adapter = LibraryAdapter(covers, scope, functions, pref)
        sections = HomeSectionsAdapter(covers, scope, functions)
        viewport = LibraryViewport(context, pref, adapter, sections, {}, {})
        viewport.attach(RecyclerView(context))
        assertTrue(viewport.submit(rows, progress, canonical = false))
    }

    @After fun tearDown() {
        scope.cancel()
    }

    @Test fun aNewUiBlockerCancelsCanonicalRowsAndAllowsLatestAppend() = runBlocking(Dispatchers.Main) {
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            viewport.submit(rows.reversed(), listOf(HomeSection.Chips), canonical = true)
        }

        assertTrue(viewport.onInteractionBlocked())
        assertFalse(pending.await())
        assertEquals(rows, adapter.currentList)
        assertEquals(progress, sections.currentList)

        val latest = rows + HomeItem.fromScan(ScannedPdfEntry("/books/b.pdf", 100, 1_000, null))
        assertTrue(viewport.submit(latest, listOf(HomeSection.Chips), canonical = false))
        assertEquals(latest, adapter.currentList)
        assertEquals(listOf(HomeSection.Chips), sections.currentList)
        assertFalse(viewport.onInteractionBlocked())
    }

    @Test fun reattachmentRejectsObsoleteRowCallbacksBeforeSubmittingSections() = runBlocking(Dispatchers.Main) {
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            viewport.submit(rows.reversed(), listOf(HomeSection.Chips), canonical = true)
        }
        viewport.attach(RecyclerView(context))

        assertFalse(pending.await())
        assertEquals(progress, sections.currentList)
        assertTrue(viewport.submit(rows, listOf(HomeSection.Chips), canonical = false))
        assertEquals(rows, adapter.currentList)
        assertEquals(listOf(HomeSection.Chips), sections.currentList)
    }

    @Test fun stoppingDuringSectionCommitRejectsTheAttemptAndRestartCanCatchUp() = runBlocking(Dispatchers.Main) {
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            viewport.submit(rows, listOf(HomeSection.Chips), canonical = false)
        }
        viewport.onHostStopped()

        assertFalse(pending.await())
        assertFalse(viewport.submit(rows.reversed(), progress, canonical = true))
        viewport.onHostStarted()
        assertTrue(viewport.submit(rows.reversed(), progress, canonical = true))
        assertEquals(rows.reversed(), adapter.currentList)
        assertEquals(progress, sections.currentList)
    }

    @Test fun touchDuringSectionDiffRejectsTheWholeTransactionAndAllowsCatchUp() = runBlocking(Dispatchers.Main) {
        cancelDuringSectionDiff(selecting = false)
    }

    @Test fun selectionDuringSectionDiffRejectsTheWholeTransactionAndAllowsCatchUp() = runBlocking(Dispatchers.Main) {
        cancelDuringSectionDiff(selecting = true)
    }

    private suspend fun cancelDuringSectionDiff(selecting: Boolean) = coroutineScope {
        val storage = context.getSharedPreferences("viewport-section-commit-test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val pref = Preferences(storage).apply {
            setHomeSort(HomeSortOrder.NAME)
            setHomeViewMode(HomeViewMode.LIST)
            setListFilter(ListFilter.ALL)
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            var selectedKeys = emptySet<String>()
            val functions = object : HomeItemFunctions {
                override fun onItemClicked(item: HomeItem) = Unit
                override fun onItemLongClicked(item: HomeItem) = true
                override fun onItemOptionsClicked(item: HomeItem) = Unit
            }
            val tab = LibraryTabController(
                context, pref, CoverCache.getInstance(context), scope, functions,
                selection = { selectedKeys },
                libraryController = HomeLibraryController(PdfRepository(database), pref, HistoryPolicy(pref)),
                hasFullAccess = { true }, onGrantAccessClicked = {}, showScanSetup = { false },
                onScanSetupClicked = {}, onFilterChanged = {}, onPresentationInvalidated = {},
            )
            val view = object : RecyclerView(context) {
                override fun isAttachedToWindow() = true
            }.apply { setPadding(0, 16, 0, 0) }
            tab.attach(view)
            val finding = LibraryIndex(
                entries = (0 until 24).map {
                    ScannedPdfEntry("/books/book-${it.toString().padStart(2, '0')}.pdf", 100, 1_000, null)
                },
                loaded = true, phase = ScanPhase.FINDING, foundCount = 24,
                showProgress = true, scopeRoots = listOf("/books"), revision = 1L,
            )
            tab.render(emptyList(), finding)
            val sections = (view.adapter as ConcatAdapter).adapters.first() as HomeSectionsAdapter
            val manager = view.layoutManager as GridLayoutManager
            manager.scrollToPositionWithOffset(sections.itemCount + 6, -11)
            layout(view)
            val anchorKey = tab.currentGridItems()[6].stableKey
            val anchorTop = manager.findViewByPosition(sections.itemCount + 6)!!.top
            val committedRows = tab.currentGridItems().toList()
            val committedSections = sections.currentList.toList()
            val revision = uiRevision(view, finding)
            val terminal = finding.copy(phase = ScanPhase.IDLE, revision = 2L)
            val gate = SectionExecutorGate(sections)
            try {
                val pending = async(start = CoroutineStart.UNDISPATCHED) { tab.render(emptyList(), terminal) }
                val obsoleteSectionDiff = gate.next()
                assertFalse(pending.isCompleted)
                assertEquals(committedRows, tab.currentGridItems())
                assertEquals(committedSections, sections.currentList)

                val downTime = SystemClock.uptimeMillis()
                if (selecting) {
                    selectedKeys = setOf(anchorKey)
                    tab.notifySelectionChanged()
                } else {
                    touch(view, MotionEvent.ACTION_DOWN, downTime)
                }
                assertEquals(RecyclerView.SCROLL_STATE_IDLE, view.scrollState)
                withTimeout(5_000) { pending.await() }
                assertEquals(revision, uiRevision(view, terminal))
                layout(view)
                assertFalse(view.isLayoutRequested)

                obsoleteSectionDiff.run()
                gate.drain()
                assertFalse(view.isLayoutRequested)
                layout(view)
                assertEquals(committedRows, tab.currentGridItems())
                assertEquals(committedSections, sections.currentList)
                assertEquals(anchorTop, manager.findViewByPosition(sections.itemCount + 6)!!.top)
                assertEquals(revision, uiRevision(view, terminal))

                val latest = finding.copy(
                    entries = finding.entries + ScannedPdfEntry("/books/aaa.pdf", 100, 1_000, null),
                    foundCount = 25, revision = 3L,
                )
                val append = async(start = CoroutineStart.UNDISPATCHED) { tab.render(emptyList(), latest) }
                gate.next().run()
                withTimeout(5_000) { append.await() }
                layout(view)
                assertEquals(committedRows, tab.currentGridItems().dropLast(1))
                assertEquals("aaa", tab.currentGridItems().last().title)
                assertEquals(anchorTop, manager.findViewByPosition(sections.itemCount + 6)!!.top)
                assertEquals(revision + 1, uiRevision(view, latest))

                if (selecting) {
                    selectedKeys = emptySet()
                    tab.notifySelectionChanged()
                } else {
                    touch(view, MotionEvent.ACTION_UP, downTime)
                }
                val settled = latest.copy(phase = ScanPhase.IDLE, revision = 4L)
                val catchUp = async(start = CoroutineStart.UNDISPATCHED) { tab.render(emptyList(), settled) }
                gate.next().run()
                withTimeout(5_000) { catchUp.await() }
                gate.drain()
                layout(view)
                assertEquals("aaa", tab.currentGridItems().first().title)
                assertEquals(listOf(HomeSection.Chips), sections.currentList)
                val anchorIndex = tab.currentGridItems().indexOfFirst { it.stableKey == anchorKey }
                assertEquals(anchorTop, manager.findViewByPosition(sections.itemCount + anchorIndex)!!.top)
                assertEquals(revision + 2, uiRevision(view, settled))
            } finally {
                gate.close()
            }
        } finally {
            database.close()
        }
    }

    private fun layout(view: RecyclerView) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 480, 640)
    }

    private fun touch(view: RecyclerView, action: Int, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 20f, 20f, 0)
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun uiRevision(view: RecyclerView, index: LibraryIndex): Long {
        HomeDebugState.republish(view, HomeTab.LIBRARY, searching = false, index = index)
        return view.contentDescription.toString().substringAfter(";uiRevision=").substringBefore(';').toLong()
    }

    private class SectionExecutorGate(adapter: HomeSectionsAdapter) : AutoCloseable {
        private val tasks = Channel<Runnable>(Channel.UNLIMITED)
        private val held = mutableListOf<Runnable>()
        private val differ = ListAdapter::class.java.getDeclaredField("mDiffer").run {
            isAccessible = true
            get(adapter)
        }
        private val config = AsyncListDiffer::class.java.getDeclaredField("mConfig").run {
            isAccessible = true
            get(differ)
        }
        private val backgroundField = AsyncDifferConfig::class.java.getDeclaredField("mBackgroundThreadExecutor").apply {
            isAccessible = true
        }
        private val mainField = AsyncListDiffer::class.java.getDeclaredField("mMainThreadExecutor").apply {
            isAccessible = true
        }
        private val background = backgroundField.get(config)
        private val main = mainField.get(differ)

        init {
            backgroundField.set(config, Executor { tasks.trySend(it).getOrThrow() })
            mainField.set(differ, Executor { it.run() })
        }

        suspend fun next(): Runnable = withTimeout(5_000) {
            val task = tasks.receive()
            var ran = false
            Runnable {
                if (!ran) {
                    ran = true
                    task.run()
                }
            }.also { held += it }
        }

        fun drain() {
            while (true) (tasks.tryReceive().getOrNull() ?: return).run()
        }

        override fun close() {
            backgroundField.set(config, background)
            mainField.set(differ, main)
            held.forEach { it.run() }
            drain()
        }
    }
}
