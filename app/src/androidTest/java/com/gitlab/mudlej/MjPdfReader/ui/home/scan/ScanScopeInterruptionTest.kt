// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.core.debug.StorageAccessOverride
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
@SdkSuppress(minSdkVersion = 29)
class ScanScopeInterruptionTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "scan-interruption-${UUID.randomUUID()}"
    private val root = File(base.cacheDir, name)
    private val releases = mutableListOf<CountDownLatch>()
    private lateinit var database: AppDatabase
    private lateinit var context: QueryContext
    private lateinit var preferences: Preferences
    private var coordinator: LibraryScanCoordinator? = null

    @Before fun setUp() {
        check(root.mkdirs())
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        context = QueryContext(base.getSharedPreferences(name, Context.MODE_PRIVATE))
        preferences = Preferences(context.storage)
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(root.path))
        StorageAccessOverride.force(true)
        MetadataWorkerTestHooks.reset()
    }

    @After fun tearDown() {
        releases.forEach { it.countDown() }
        runBlocking { coordinator?.shutdownForTests() }
        database.close()
        root.deleteRecursively()
        base.deleteSharedPreferences(name)
        MetadataWorkerTestHooks.reset()
        StorageAccessOverride.clear()
    }

    @Test fun scopeExpansionResumesTheUnfinishedExplicitWalkAndAddsOnlyTheNewRoot() {
        val a = entry("a/unindexed.pdf")
        val b = entry("b/unindexed.pdf")
        val aRoot = File(a.path).parent!!
        val bRoot = File(b.path).parent!!
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot))
        MetadataWorkerTestHooks.pauseDispatch()
        val blocked = blockFirstQuery(cooperative = true)
        val scanner = startCoordinator()
        val directories = ScanDebugSignals.filesystemDirectories()
        scanner.refresh(ScanRefreshReason.FULL_RESCAN)
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot, bRoot))
        scanner.post(ScanEvent.ScopeChanged(listOf(aRoot)))
        awaitDetails(scanner, generation, setOf(a.path, b.path))

        assertTrue(blocked.cancelled.await(5, SECONDS))
        assertEquals(2, context.queries.get())
        assertEquals(1, context.maxQueries.get())
        assertEquals(2, ScanDebugSignals.filesystemDirectories() - directories)
        assertEquals(0, context.binds.get())
    }

    @Test fun parentExpansionKeepsCompletedExclusionsWithoutExcludingUnfinishedCoverage() {
        val completed = entry("books/completed/cached.pdf").copy(metadataState = ScannedPdfMetadataState.COMPLETE)
        val mustStayExcluded = entry("books/completed/later.pdf")
        val unfinished = entry("books/unfinished.pdf")
        val added = entry("added.pdf")
        val completedRoot = File(completed.path).parent!!
        val books = File(root, "books").path
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(completedRoot))
        database.scannedPdfEntryDao().upsertAll(listOf(completed))
        context.mediaRows = listOf(completed)
        MetadataWorkerTestHooks.pauseDispatch()
        val blocked = blockFirstQuery(cooperative = true)
        val scanner = startCoordinator()
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(books))
        scanner.post(ScanEvent.ScopeChanged(listOf(completedRoot)))
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(root.path))
        scanner.post(ScanEvent.ScopeChanged(listOf(books)))
        awaitDetails(scanner, generation, setOf(completed.path, unfinished.path, added.path))

        assertEquals(null, database.scannedPdfEntryDao().findByPath(mustStayExcluded.path))
        assertEquals(2, context.queries.get())
        assertEquals(1, context.maxQueries.get())
    }

    @Test fun narrowingAnUnfinishedWalkStopsAllCoverageOutsideTheNewScope() {
        val retained = entry("a/selected/book.pdf")
        val removedParent = entry("a/outside.pdf")
        val removedRoot = entry("b/outside.pdf")
        val a = File(root, "a").path
        val b = File(root, "b").path
        val selected = File(retained.path).parent!!
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(a, b))
        MetadataWorkerTestHooks.pauseDispatch()
        val blocked = blockFirstQuery(cooperative = true)
        val scanner = startCoordinator()
        scanner.refresh(ScanRefreshReason.FULL_RESCAN)
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(selected))
        scanner.post(ScanEvent.ScopeChanged(listOf(a, b)))
        val index = awaitDetails(scanner, generation, setOf(retained.path))

        assertEquals(listOf(selected), index.scopeRoots)
        assertEquals(null, database.scannedPdfEntryDao().findByPath(removedParent.path))
        assertEquals(null, database.scannedPdfEntryDao().findByPath(removedRoot.path))
    }

    @Test fun ordinaryRefreshAfterCancellationTimeoutPreservesExpansionAndCompletedExclusions() {
        val completed = entry("a/completed/cached.pdf").copy(metadataState = ScannedPdfMetadataState.COMPLETE)
        val excluded = entry("a/completed/later.pdf")
        val a = entry("a/unindexed.pdf")
        val b = entry("b/unindexed.pdf")
        val unrequested = entry("outside/unindexed.pdf")
        val completedRoot = File(completed.path).parent!!
        val aRoot = File(a.path).parent!!
        val bRoot = File(b.path).parent!!
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(completedRoot))
        database.scannedPdfEntryDao().upsertAll(listOf(completed))
        context.mediaRows = listOf(completed)
        MetadataWorkerTestHooks.pauseDispatch()
        val blocked = blockFirstQuery(cooperative = false)
        val scanner = startCoordinator()
        val directories = ScanDebugSignals.filesystemDirectories()
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot))
        scanner.post(ScanEvent.ScopeChanged(listOf(completedRoot)))
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot, bRoot))
        scanner.post(ScanEvent.ScopeChanged(listOf(aRoot)))
        var incomplete = awaitTerminal(scanner, generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        assertTrue(blocked.cancelled.await(5, SECONDS))
        for (reason in listOf(ScanRefreshReason.FOREGROUND, ScanRefreshReason.OBSERVER, ScanRefreshReason.MANUAL)) {
            scanner.refresh(reason)
            incomplete = awaitTerminal(scanner, incomplete.generation, 1_500)
            assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        }
        assertEquals(1, context.queries.get())
        assertEquals(directories, ScanDebugSignals.filesystemDirectories())

        val resumed = refreshAfterRelease(scanner, blocked, incomplete.generation)

        assertEquals(ScanPhase.READING_DETAILS, resumed.phase)
        assertEquals(setOf(completed.path, a.path, b.path), resumed.entries.map { it.path }.toSet())
        assertEquals(setOf(aRoot, bRoot), resumed.scopeRoots?.toSet())
        assertEquals(null, database.scannedPdfEntryDao().findByPath(excluded.path))
        assertEquals(null, database.scannedPdfEntryDao().findByPath(unrequested.path))
        assertEquals(2, ScanDebugSignals.filesystemDirectories() - directories)
        assertEquals(2, context.queries.get())
        assertEquals(1, context.maxQueries.get())
        assertEquals(0, context.binds.get())
    }

    @Test fun ordinaryRefreshAfterCancellationTimeoutKeepsOnlyContractedCoverage() {
        val retained = entry("a/selected/book.pdf")
        val removedParent = entry("a/outside.pdf")
        val removedRoot = entry("b/outside.pdf")
        val unrequested = entry("outside/unindexed.pdf")
        val a = File(root, "a").path
        val b = File(root, "b").path
        val selected = File(retained.path).parent!!
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(a, b))
        MetadataWorkerTestHooks.pauseDispatch()
        val blocked = blockFirstQuery(cooperative = false)
        val scanner = startCoordinator()
        val directories = ScanDebugSignals.filesystemDirectories()
        scanner.refresh(ScanRefreshReason.FULL_RESCAN)
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(selected))
        scanner.post(ScanEvent.ScopeChanged(listOf(a, b)))
        val incomplete = awaitTerminal(scanner, generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        assertEquals(listOf(selected), incomplete.scopeRoots)
        scanner.refresh(ScanRefreshReason.MANUAL)
        val retried = awaitTerminal(scanner, incomplete.generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, retried.phase)
        assertEquals(1, context.queries.get())

        val resumed = refreshAfterRelease(scanner, blocked, retried.generation)

        assertEquals(ScanPhase.READING_DETAILS, resumed.phase)
        assertEquals(listOf(selected), resumed.scopeRoots)
        assertEquals(listOf(retained), resumed.entries)
        for (row in listOf(removedParent, removedRoot, unrequested)) {
            assertEquals(null, database.scannedPdfEntryDao().findByPath(row.path))
        }
        assertEquals(1, ScanDebugSignals.filesystemDirectories() - directories)
        assertEquals(2, context.queries.get())
        assertEquals(1, context.maxQueries.get())
    }

    @Test fun fullRescanAcceptedWhileCancellationIsBlockedReplacesTheRetainedExclusions() {
        val completed = entry("a/completed/cached.pdf").copy(metadataState = ScannedPdfMetadataState.COMPLETE)
        val previouslyExcluded = entry("a/completed/later.pdf")
        val a = entry("a/unindexed.pdf")
        val b = entry("b/unindexed.pdf")
        val unrequested = entry("outside/unindexed.pdf")
        val completedRoot = File(completed.path).parent!!
        val aRoot = File(a.path).parent!!
        val bRoot = File(b.path).parent!!
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(completedRoot))
        database.scannedPdfEntryDao().upsertAll(listOf(completed))
        context.mediaRows = listOf(completed)
        MetadataWorkerTestHooks.pauseDispatch()
        val blocked = blockFirstQuery(cooperative = false)
        val scanner = startCoordinator()
        val directories = ScanDebugSignals.filesystemDirectories()
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot))
        scanner.post(ScanEvent.ScopeChanged(listOf(completedRoot)))
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot, bRoot))
        scanner.post(ScanEvent.ScopeChanged(listOf(aRoot)))
        val incomplete = awaitTerminal(scanner, generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        scanner.refresh(ScanRefreshReason.FULL_RESCAN)
        val full = awaitTerminal(scanner, incomplete.generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, full.phase)
        scanner.refresh(ScanRefreshReason.OBSERVER)
        val retried = awaitTerminal(scanner, full.generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, retried.phase)
        assertEquals(1, context.queries.get())

        val resumed = refreshAfterRelease(scanner, blocked, retried.generation)

        assertEquals(ScanPhase.READING_DETAILS, resumed.phase)
        assertEquals(
            setOf(completed.path, previouslyExcluded.path, a.path, b.path),
            resumed.entries.map { it.path }.toSet(),
        )
        assertEquals(null, database.scannedPdfEntryDao().findByPath(unrequested.path))
        assertEquals(3, ScanDebugSignals.filesystemDirectories() - directories)
        assertEquals(2, context.queries.get())
        assertEquals(1, context.maxQueries.get())
    }

    @Test fun finishedFilesystemCoverageIsNotResumedWhileMetadataIsStillPending() {
        val a = entry("a/first.pdf")
        val b = entry("b/added.pdf")
        val aRoot = File(a.path).parent!!
        val bRoot = File(b.path).parent!!
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot))
        MetadataWorkerTestHooks.pauseDispatch()
        val scanner = startCoordinator()
        scanner.refresh(ScanRefreshReason.FULL_RESCAN)
        val previous = awaitDetails(scanner, 0, setOf(a.path))
        val later = entry("a/later.pdf")

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(aRoot, bRoot))
        scanner.post(ScanEvent.ScopeChanged(listOf(aRoot)))
        awaitDetails(scanner, previous.generation, setOf(a.path, b.path))

        assertEquals(null, database.scannedPdfEntryDao().findByPath(later.path))
    }

    @Test fun unconfiguredRefreshIndexesGlobalRowsWithoutWalksMetadataOrProgressCards() {
        val row = entry("global/book.pdf")
        preferences.setScanScope(ScanMode.NOT_CONFIGURED, emptySet())
        context.mediaRows = listOf(row)
        val blocked = blockFirstQuery(cooperative = true)
        val scanner = startCoordinator()
        val directories = ScanDebugSignals.filesystemDirectories()
        scanner.refresh(ScanRefreshReason.FOREGROUND)
        assertTrue(blocked.entered.await(5, SECONDS))

        assertFalse(scanner.index.value.showProgress)
        assertEquals(emptyList<String>(), scanner.index.value.scopeRoots)
        blocked.release.countDown()
        var completed = awaitTerminal(scanner, 0)
        for (reason in listOf(ScanRefreshReason.MANUAL, ScanRefreshReason.FULL_RESCAN)) {
            scanner.refresh(reason)
            completed = awaitTerminal(scanner, completed.generation)
        }

        assertEquals(ScanPhase.IDLE, completed.phase)
        assertEquals(listOf(row), completed.entries)
        assertEquals(listOf(row), database.scannedPdfEntryDao().findAll())
        assertTrue(scanner.libraryEntries(completed).isEmpty())
        assertEquals(0, completed.detailsTotal)
        assertFalse(completed.showProgress)
        assertEquals(ScanMode.NOT_CONFIGURED, preferences.getScanMode())
        assertEquals(directories, ScanDebugSignals.filesystemDirectories())
        assertEquals(0, context.binds.get())
        assertEquals(3, context.queries.get())
    }

    @Test fun ignoredQueryCancellationBoundsCleanupAndRefusesOverlappingProducers() {
        val cached = entry("cached.pdf").copy(metadataState = ScannedPdfMetadataState.COMPLETE)
        database.scannedPdfEntryDao().upsertAll(listOf(cached))
        context.mediaRows = listOf(cached)
        val blocked = blockFirstQuery(cooperative = false)
        val scanner = startCoordinator()
        scanner.refresh()
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation
        val narrowed = File(root, "selected").path
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(narrowed))
        scanner.post(ScanEvent.ScopeChanged(listOf(root.path)))

        val incomplete = awaitTerminal(scanner, generation, timeoutMillis = 1_500)
        assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        assertEquals(listOf(narrowed), incomplete.scopeRoots)
        assertTrue(blocked.cancelled.await(5, SECONDS))
        assertEquals(1L, blocked.returned.count)
        repeat(4) { scanner.refresh() }
        scanner.post(ScanEvent.StorageAccessChanged(false))
        val paused = awaitPhase(scanner, ScanPhase.PAUSED, incomplete.generation)
        assertEquals(listOf(cached), paused.entries)
        assertEquals(1, context.queries.get())

        blocked.release.countDown()
        assertTrue(blocked.returned.await(5, SECONDS))
        Await.until("a later refresh can start once the retired producer has exited", 5_000) {
            scanner.refresh()
            context.queries.get() >= 2
        }
        assertEquals(1, context.maxQueries.get())
    }

    @Test fun aCursorWindowIgnoringCancellationCannotHoldTheOwnerOrSpawnAnotherQuery() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1).also(releases::add)
        context.queryResult = {
            object : CursorWrapper(context.cursor()) {
                override fun moveToNext(): Boolean {
                    entered.countDown()
                    check(release.await(10, SECONDS))
                    return super.moveToNext()
                }
            }
        }
        val scanner = startCoordinator()
        scanner.refresh()
        assertTrue(entered.await(5, SECONDS))
        val generation = scanner.index.value.generation

        scanner.post(ScanEvent.StorageAccessChanged(false))
        val paused = awaitPhase(scanner, ScanPhase.PAUSED, generation)
        scanner.refresh()
        assertEquals(ScanPhase.INCOMPLETE, awaitTerminal(scanner, paused.generation, 1_500).phase)
        assertEquals(1, context.queries.get())
    }

    @Test fun sourceCancellationRetiresTheGenerationInsteadOfLeavingFindingActive() {
        context.queryResult = {
            object : CursorWrapper(context.cursor()) {
                override fun moveToNext(): Boolean = throw CancellationException("cancelled source")
            }
        }
        val scanner = startCoordinator()
        scanner.refresh()

        val incomplete = awaitTerminal(scanner, 0)
        assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        assertEquals(setOf(DiscoverySource.MEDIA_STORE), incomplete.sourceFailures)
        assertEquals(null, preferences.getScanBaselineScope())
    }

    @Test fun persistenceFailurePublishesIncompleteWithoutWaitingForABlockedQuery() {
        val cached = entry("cached.pdf").copy(metadataState = ScannedPdfMetadataState.COMPLETE)
        database.scannedPdfEntryDao().upsertAll(listOf(cached))
        val blocked = blockFirstQuery(cooperative = false)
        val scanner = startCoordinator()
        scanner.refresh()
        assertTrue(blocked.entered.await(5, SECONDS))
        val generation = scanner.index.value.generation
        database.close()

        scanner.post(ScanEvent.FileRemoved(cached.path))
        val incomplete = awaitTerminal(scanner, generation, 1_500)
        assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
        assertEquals(listOf(cached), incomplete.entries)
        assertTrue(incomplete.loaded)
        assertTrue(blocked.cancelled.await(5, SECONDS))
        assertEquals(1L, blocked.returned.count)
    }

    @Test fun shutdownIsBoundedEvenWhenTheProviderIgnoresCancellation() {
        val blocked = blockFirstQuery(cooperative = false)
        val scanner = startCoordinator()
        scanner.refresh()
        assertTrue(blocked.entered.await(5, SECONDS))

        runBlocking { withTimeout(1_500) { scanner.shutdownForTests() } }

        assertTrue(blocked.cancelled.await(5, SECONDS))
        assertEquals(1L, blocked.returned.count)
        assertEquals(1, context.queries.get())
    }

    private fun startCoordinator(): LibraryScanCoordinator =
        LibraryScanCoordinator(context, PdfRepository(database)).also { scanner ->
            coordinator = scanner
            Await.until("Room cache is loaded without Home", 5_000) { scanner.index.value.loaded }
        }

    private fun awaitDetails(scanner: LibraryScanCoordinator, after: Long, paths: Set<String>): LibraryIndex =
        runBlocking {
            withTimeout(5_000) {
                scanner.index.first {
                    it.generation > after && it.phase == ScanPhase.READING_DETAILS &&
                        it.entries.map { row -> row.path }.toSet() == paths
                }
            }
        }

    private fun awaitTerminal(scanner: LibraryScanCoordinator, after: Long, timeoutMillis: Long = 5_000): LibraryIndex =
        runBlocking {
            withTimeout(timeoutMillis) { scanner.index.first { it.loaded && it.generation > after && !it.active } }
        }

    private fun awaitPhase(scanner: LibraryScanCoordinator, phase: ScanPhase, after: Long): LibraryIndex =
        runBlocking {
            withTimeout(1_500) { scanner.index.first { it.generation > after && it.phase == phase } }
        }

    private fun entry(path: String): ScannedPdfEntry {
        val file = File(root, path)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        file.writeText("unindexed PDF fixture")
        val fingerprint = SourceFingerprint.normalized(file.path, file.length(), file.lastModified())
        return ScannedPdfEntry(fingerprint.path, fingerprint.size, fingerprint.lastModified, null)
    }

    private fun blockFirstQuery(cooperative: Boolean): BlockedQuery = BlockedQuery(cooperative).also { blocked ->
        releases += blocked.release
        context.queryResult = { signal ->
            if (context.queries.get() == 1) blocked.await(signal)
            context.cursor()
        }
    }

    private fun refreshAfterRelease(scanner: LibraryScanCoordinator, blocked: BlockedQuery, after: Long): LibraryIndex {
        blocked.release.countDown()
        assertTrue(blocked.returned.await(5, SECONDS))
        return runBlocking {
            withTimeout(5_000) {
                var generation = after
                var next: LibraryIndex
                do {
                    scanner.refresh(ScanRefreshReason.MANUAL)
                    next = scanner.index.first {
                        it.generation > generation && (!it.active || it.phase == ScanPhase.READING_DETAILS)
                    }
                    generation = next.generation
                    if (next.phase == ScanPhase.INCOMPLETE) delay(25)
                } while (next.phase == ScanPhase.INCOMPLETE)
                next
            }
        }
    }

    private class BlockedQuery(private val cooperative: Boolean) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val returned = CountDownLatch(1)

        fun await(signal: CancellationSignal?) {
            signal?.setOnCancelListener {
                cancelled.countDown()
                if (cooperative) release.countDown()
            }
            entered.countDown()
            check(release.await(10, SECONDS)) { "Test query was not released" }
            returned.countDown()
        }
    }

    private inner class QueryContext(val storage: SharedPreferences) : ContextWrapper(base) {
        @Volatile var mediaRows: List<ScannedPdfEntry> = emptyList()
        @Volatile var queryResult: (CancellationSignal?) -> Cursor? = { cursor() }
        val queries = AtomicInteger()
        val maxQueries = AtomicInteger()
        val binds = AtomicInteger()
        private val activeQueries = AtomicInteger()
        private val provider = object : ContentProvider() {
            override fun onCreate() = true

            override fun query(
                uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?,
            ): Cursor? = query(uri, projection, selection, selectionArgs, sortOrder, null)

            override fun query(
                uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?,
            ): Cursor? {
                queries.incrementAndGet()
                val active = activeQueries.incrementAndGet()
                maxQueries.accumulateAndGet(active) { old, next -> maxOf(old, next) }
                return try {
                    queryResult(cancellationSignal)
                } finally {
                    activeQueries.decrementAndGet()
                }
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }.also { it.attachInfo(base, ProviderInfo().apply { authority = "media" }) }
        private val resolver = ContentResolver.wrap(provider)

        fun cursor(): Cursor = MatrixCursor(arrayOf(
            MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
        )).also { cursor ->
            mediaRows.forEach { cursor.addRow(arrayOf<Any>(it.path, it.size, it.lastModified / 1_000L)) }
        }

        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver = resolver
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = storage
        override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
            binds.incrementAndGet()
            return false
        }
    }
}
