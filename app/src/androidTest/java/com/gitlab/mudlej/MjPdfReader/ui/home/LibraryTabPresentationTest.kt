// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTabPresentationTest {
    private lateinit var database: AppDatabase
    private lateinit var tab: LibraryTabController
    private lateinit var view: RecyclerView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val a = ScannedPdfEntry("/books/a.pdf", 100, 1_000, null)
    private val z = a.copy(path = "/books/z.pdf")
    private var selectedKeys = emptySet<String>()
    private var invalidations = 0
    private var downTime = 0L

    @Before fun setUp() = runBlocking(Dispatchers.Main) {
        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.HomeTheme,
        )
        val storage = context.getSharedPreferences("library-tab-presentation-test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val pref = Preferences(storage).apply {
            setHomeSort(HomeSortOrder.NAME)
            setListFilter(ListFilter.ALL)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val functions = object : HomeItemFunctions {
            override fun onItemClicked(item: HomeItem) = Unit
            override fun onItemLongClicked(item: HomeItem) = true
            override fun onItemOptionsClicked(item: HomeItem) = Unit
        }
        tab = LibraryTabController(
            context, pref, CoverCache.getInstance(context), scope, functions,
            selection = { selectedKeys },
            libraryController = HomeLibraryController(PdfRepository(database), pref, HistoryPolicy(pref)),
            hasFullAccess = { true }, onGrantAccessClicked = {}, showScanSetup = { false },
            onScanSetupClicked = {}, onFilterChanged = {}, onPresentationInvalidated = { invalidations++ },
        )
        view = RecyclerView(context)
        tab.attach(view)
        tab.render(emptyList(), index(z))
    }

    @After fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test fun cancelledCallerCannotOverlapOrdinaryAndCanonicalAdapterCommits() = runBlocking(Dispatchers.Main) {
        val orders = mutableListOf<List<String>>()
        tab.libraryAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                orders += tab.currentGridItems().map { it.title }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                orders += tab.currentGridItems().map { it.title }
            }
        })
        val ordinary = launch(start = CoroutineStart.UNDISPATCHED) {
            tab.render(emptyList(), index(a, z))
        }
        ordinary.cancel()
        val canonical = async(start = CoroutineStart.UNDISPATCHED) {
            tab.render(emptyList(), index(a, z, phase = ScanPhase.IDLE))
        }
        assertTrue(orders.isEmpty())
        assertEquals(listOf("z"), tab.currentGridItems().map { it.title })

        ordinary.join()
        canonical.await()

        assertEquals(listOf("z", "a"), orders.first())
        assertEquals(listOf("a", "z"), orders.last())
        assertEquals(listOf("a", "z"), tab.currentGridItems().map { it.title })
    }

    @Test fun touchDownWhileIdleCancelsPendingCanonicalRowsAndDefersReorderingUntilUp() = runBlocking(Dispatchers.Main) {
        tab.render(emptyList(), index(a, z))
        val terminal = index(a, z, phase = ScanPhase.IDLE)
        val observedTouches = mutableListOf<Int>()
        view.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
                observedTouches += event.actionMasked
                return false
            }

            override fun onTouchEvent(view: RecyclerView, event: MotionEvent) = Unit
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit
        })
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            tab.render(emptyList(), terminal)
        }

        dispatchTouch(MotionEvent.ACTION_DOWN)
        pending.await()

        assertEquals(RecyclerView.SCROLL_STATE_IDLE, view.scrollState)
        assertTrue(MotionEvent.ACTION_DOWN in observedTouches)
        assertEquals(1, invalidations)
        assertEquals(listOf("z", "a"), tab.currentGridItems().map { it.title })
        val latest = terminal.copy(entries = terminal.entries + a.copy(path = "/books/b.pdf"), revision = 6L)
        tab.render(emptyList(), latest)
        assertEquals(listOf("z", "a", "b"), tab.currentGridItems().map { it.title })

        dispatchTouch(MotionEvent.ACTION_UP)
        assertEquals(2, invalidations)
        tab.render(emptyList(), latest)
        assertEquals(listOf("a", "b", "z"), tab.currentGridItems().map { it.title })
    }

    @Test fun liftingOnePointerKeepsTheOtherPointerBlockedUntilCancel() = runBlocking(Dispatchers.Main) {
        tab.render(emptyList(), index(a, z))
        val terminal = index(a, z, phase = ScanPhase.IDLE)
        dispatchTouch(MotionEvent.ACTION_DOWN)
        dispatchTouch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0, 1)
        dispatchTouch(MotionEvent.ACTION_POINTER_UP, 0, 1)

        assertEquals(RecyclerView.SCROLL_STATE_IDLE, view.scrollState)
        assertEquals(0, invalidations)
        tab.render(emptyList(), terminal)
        assertEquals(listOf("z", "a"), tab.currentGridItems().map { it.title })

        dispatchTouch(MotionEvent.ACTION_CANCEL, 1)
        assertEquals(1, invalidations)
        tab.render(emptyList(), terminal)
        assertEquals(listOf("a", "z"), tab.currentGridItems().map { it.title })
    }

    @Test fun releasingTouchDoesNotClearSelectionAndStoppingClearsAnUnfinishedGesture() = runBlocking(Dispatchers.Main) {
        tab.render(emptyList(), index(a, z))
        val terminal = index(a, z, phase = ScanPhase.IDLE)
        selectedKeys = setOf(tab.currentGridItems().first().stableKey)
        tab.notifySelectionChanged()
        dispatchTouch(MotionEvent.ACTION_DOWN)
        dispatchTouch(MotionEvent.ACTION_UP)

        assertEquals(0, invalidations)
        tab.render(emptyList(), terminal)
        assertEquals(listOf("z", "a"), tab.currentGridItems().map { it.title })

        dispatchTouch(MotionEvent.ACTION_DOWN)
        selectedKeys = emptySet()
        tab.notifySelectionChanged()
        assertEquals(0, invalidations)
        tab.onHostStopped()
        tab.onHostStarted()
        assertEquals(1, invalidations)
        tab.render(emptyList(), terminal)
        assertEquals(listOf("a", "z"), tab.currentGridItems().map { it.title })
    }

    private fun dispatchTouch(action: Int, vararg pointers: Int) {
        val ids = pointers.takeIf { it.isNotEmpty() } ?: intArrayOf(0)
        val now = SystemClock.uptimeMillis()
        if ((action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(
            downTime, now, action, ids.size,
            Array(ids.size) { index -> MotionEvent.PointerProperties().apply {
                id = ids[index]
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } },
            Array(ids.size) { index -> MotionEvent.PointerCoords().apply {
                x = 20f + index * 4
                y = 20f
                pressure = 1f
                size = 1f
            } },
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun index(vararg entries: ScannedPdfEntry, phase: ScanPhase = ScanPhase.FINDING) = LibraryIndex(
        entries = entries.toList(), loaded = true, phase = phase, scopeRoots = listOf("/books"), revision = 5L,
    )
}
