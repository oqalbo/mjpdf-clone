// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPresentationCommitTest {
    private lateinit var database: AppDatabase
    private lateinit var presentation: LibraryPresentation
    private lateinit var libraryController: HomeLibraryController
    private var rows = emptyList<HomeItem>()
    private var sections = emptyList<HomeSection>()
    private val a = ScannedPdfEntry("/books/a.pdf", 100, 1_000, null)
    private val z = a.copy(path = "/books/z.pdf")

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = context.getSharedPreferences("presentation-commit-test", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val pref = Preferences(storage).apply {
            setHomeSort(HomeSortOrder.NAME)
            setListFilter(ListFilter.ALL)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        libraryController = HomeLibraryController(PdfRepository(database), pref, HistoryPolicy(pref))
        presentation = LibraryPresentation(
            pref, libraryController,
            hasFullAccess = { true }, showScanSetup = { false },
        )
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun missingRecordPathsCannotReturnAsScannedFiles() {
        val missing = a.copy(hash = "shared")
        val otherCopy = z.copy(hash = "shared")
        val unknown = a.copy(path = "/books/unknown.pdf", hash = "unknown")
        val records = listOf(
            HomeItem.fromScan(missing).copy(isScanOnly = false, availability = Availability.MISSING),
            HomeItem.fromScan(unknown).copy(isScanOnly = false, availability = Availability.ACCESS_UNKNOWN),
            HomeItem.fromScan(otherCopy).copy(
                uri = Uri.parse("content://media${otherCopy.path}"),
                isScanOnly = false, availability = Availability.MISSING,
            ),
        )
        for (phase in ScanPhase.entries) {
            val original = index(missing, otherCopy, unknown, phase = phase)
            val displayed = libraryController.excludeMissingRecordFiles(records, original)

            assertEquals(listOf(otherCopy, unknown), displayed.entries)
            assertEquals(listOf(missing, otherCopy, unknown), original.entries)
            assertEquals(original.copy(entries = displayed.entries), displayed)
        }
    }

    @Test fun accessUnknownAndRestoredFilesRemainInTheDisplayedIndex() {
        val original = index(a, z, phase = ScanPhase.IDLE)
        for (availability in listOf(Availability.ACCESS_UNKNOWN, Availability.AVAILABLE)) {
            val record = HomeItem.fromScan(a).copy(isScanOnly = false, availability = availability)

            assertEquals(original, libraryController.excludeMissingRecordFiles(listOf(record), original))
        }
    }

    @Test fun activeSnapshotsAppendAndRefreshContentWithoutMovingTheCommittedPrefix() {
        render(index(z))
        val detailed = z.copy(hash = "content-hash", pageCount = 23, metadataState = ScannedPdfMetadataState.COMPLETE)

        render(index(a, detailed, phase = ScanPhase.READING_DETAILS))

        assertEquals(listOf("z", "a"), rows.map { it.title })
        assertEquals("content-hash", rows.first().hash)
        assertEquals(23, rows.first().length)
        assertEquals(HomeItem.fromScan(z).stableKey, rows.first().stableKey)
    }

    @Test fun onlyTheLastUiBlockerAllowsCanonicalizationWithoutAnotherBackendSnapshot() {
        render(index(z))
        presentation.onViewportInteractionChanged(true)
        presentation.onSelectionChanged(true)
        val terminal = index(a, z, phase = ScanPhase.IDLE)

        render(terminal)
        assertEquals(listOf("z", "a"), rows.map { it.title })
        assertFalse(presentation.onViewportInteractionChanged(false))
        render(terminal)
        assertEquals(listOf("z", "a"), rows.map { it.title })
        assertTrue(presentation.onSelectionChanged(false))
        render(terminal)
        assertEquals(listOf("a", "z"), rows.map { it.title })
        assertFalse(presentation.onSelectionChanged(false))
    }

    @Test fun identityReconciliationWaitsForBackendTerminalAndUiIdle() {
        val scanned = a.copy(hash = "shared")
        val record = HomeItem.fromScan(z.copy(hash = "shared")).copy(isScanOnly = false, favorite = true)
        val records = listOf(record)
        presentation.onSelectionChanged(true)

        render(index(scanned, phase = ScanPhase.IDLE), records)
        assertTrue(rows.single().isScanOnly)
        assertFalse(rows.single().favorite)
        assertTrue(presentation.onSelectionChanged(false))
        render(index(scanned, phase = ScanPhase.READING_DETAILS), records)
        assertTrue(rows.single().isScanOnly)
        render(index(scanned, phase = ScanPhase.INCOMPLETE), records)
        assertFalse(rows.single().isScanOnly)
        assertTrue(rows.single().favorite)
    }

    @Test fun pausedSnapshotsDropRemovedRowsButPreserveSurvivorOrderUntilTerminalAndIdle() {
        val removed = a.copy(path = "/books/removed.pdf")
        val b = a.copy(path = "/books/b.pdf")
        render(index(z))
        render(index(a, z, removed))
        assertEquals(listOf("z", "a", "removed"), rows.map { it.title })
        render(index(a, b, z, phase = ScanPhase.PAUSED))
        assertEquals(listOf("z", "a", "b"), rows.map { it.title })

        presentation.onViewportInteractionChanged(true)
        render(index(a, b, z, phase = ScanPhase.INCOMPLETE))
        assertEquals(listOf("z", "a", "b"), rows.map { it.title })
        assertTrue(presentation.onViewportInteractionChanged(false))
        render(index(a, b, z, phase = ScanPhase.INCOMPLETE))
        assertEquals(listOf("a", "b", "z"), rows.map { it.title })
    }

    @Test fun initialTerminalSnapshotWithoutHistoryIsSortedAndScopeFilteredImmediately() {
        render(index(z, a, a.copy(path = "/elsewhere/b.pdf"), phase = ScanPhase.IDLE))

        assertEquals(listOf("a", "z"), rows.map { it.title })
        assertEquals(listOf(HomeSection.Chips), sections)
    }

    @Test fun planningWithoutCommitDoesNotReplaceCommittedPresentation() {
        render(index(z))
        val committed = render(index(a, z))
        val proposed = presentation.plan(emptyList(), index(a, z, phase = ScanPhase.IDLE), rows)

        assertTrue(proposed.canonical)
        assertEquals(listOf("a", "z"), proposed.items.map { it.title })
        assertEquals(listOf("z", "a"), rows.map { it.title })
        assertFalse(presentation.commit(committed))

        render(index(a, z, a.copy(path = "/books/b.pdf")))
        assertEquals(listOf("z", "a", "b"), rows.map { it.title })
    }

    @Test fun appendUsesActualAdapterRowsRatherThanTheLastPlan() {
        render(index(z))
        render(index(a, z))
        rows = rows.reversed()

        render(index(a, z, a.copy(path = "/books/b.pdf")))

        assertEquals(listOf("a", "z", "b"), rows.map { it.title })
    }

    @Test fun onlyExplicitCommitChangesTheCommittedPlan() {
        render(index(z))
        val first = presentation.plan(emptyList(), index(a, z), rows)
        val second = presentation.plan(emptyList(), index(a, z), rows)

        assertEquals(first, second)
        assertTrue(presentation.commit(first))
        assertFalse(presentation.commit(second))
        assertEquals(listOf("z"), rows.map { it.title })
    }

    private fun index(vararg entries: ScannedPdfEntry, phase: ScanPhase = ScanPhase.FINDING) = LibraryIndex(
        entries = entries.toList(), loaded = true, phase = phase, scopeRoots = listOf("/books"),
        revision = 5L,
    )

    private fun render(index: LibraryIndex, records: List<HomeItem> = emptyList()): LibraryPresentation.Plan {
        val plan = presentation.plan(records, index, rows)
        rows = plan.items
        sections = plan.sections
        presentation.commit(plan)
        return plan
    }
}
