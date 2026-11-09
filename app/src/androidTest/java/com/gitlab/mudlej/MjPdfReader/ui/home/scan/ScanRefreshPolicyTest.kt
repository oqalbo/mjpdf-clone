// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.core.debug.HomeDebugState
import com.gitlab.mudlej.MjPdfReader.core.debug.MetadataWorkerTestHooks
import com.gitlab.mudlej.MjPdfReader.core.debug.ScanDebugSignals
import com.gitlab.mudlej.MjPdfReader.core.debug.StorageAccessOverride
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.data.entity.fingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ScanRefreshPolicyTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "scan-refresh-policy-${UUID.randomUUID()}"
    private val root = File(base.cacheDir, name)
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
        runBlocking {
            try {
                coordinator?.shutdownForTests()
            } finally {
                database.close()
                root.deleteRecursively()
                base.deleteSharedPreferences(name)
                MetadataWorkerTestHooks.reset()
                StorageAccessOverride.clear()
            }
        }
    }

    @Test fun cached315PdfsInitializeAndQuickRefreshWithoutHomeWalksOrMetadataRedispatch() {
        val source = writePdf(File(root, "book-000.pdf"))
        val rows = listOf(source) + (1 until 315).map { number ->
            val target = File(root, "book-${number.toString().padStart(3, '0')}.pdf")
            File(source.path).copyTo(target)
            check(target.setLastModified(File(source.path).lastModified()))
            source.copy(path = target.path)
        }
        database.scannedPdfEntryDao().upsertAll(rows)
        context.mediaRows = rows
        val homeGeneration = HomeDebugState.generation()
        val settlements = ScanDebugSignals.settlementCommits()
        val directories = ScanDebugSignals.filesystemDirectories()
        val probes = ScanDebugSignals.pathProbes()
        val queries = ScanDebugSignals.mediaStoreQueries()
        val committed = ScanDebugSignals.committedResults()
        MetadataWorkerTestHooks.pauseDispatch()

        val scanner = startCoordinator()
        assertEquals(rows.toSet(), scanner.index.value.entries.toSet())
        assertEquals(ScanPhase.IDLE, scanner.index.value.phase)
        assertEquals(0, context.queries.get())

        for (reason in listOf(ScanRefreshReason.FOREGROUND, ScanRefreshReason.MANUAL)) {
            val before = scanner.index.value
            val queryEntered = CountDownLatch(1)
            val releaseQuery = CountDownLatch(1)
            context.queryResult = {
                queryEntered.countDown()
                check(releaseQuery.await(10, TimeUnit.SECONDS)) { "Quick query was not released" }
                context.cursor()
            }
            try {
                scanner.refresh(reason)
                assertTrue("$reason reaches the provider without a Home collector", queryEntered.await(10, TimeUnit.SECONDS))
                assertTrue(scanner.index.value.generation > before.generation)
                assertEquals(ScanPhase.FINDING, scanner.index.value.phase)
                assertFalse("Cached quick discovery must not request a progress row", scanner.index.value.showProgress)
            } finally {
                releaseQuery.countDown()
            }
            val completed = awaitTerminal(scanner, before.generation)
            assertEquals(ScanPhase.IDLE, completed.phase)
            assertFalse(completed.showProgress)
            assertEquals(315, completed.detailsCompleted)
            assertEquals(315, completed.detailsTotal)
            assertTrue(completed.revision > before.revision)
            assertEquals(rows.toSet(), completed.entries.toSet())
            assertEquals(rows.toSet(), database.scannedPdfEntryDao().findAll().toSet())
        }

        assertEquals(2, context.queries.get())
        assertEquals(2, ScanDebugSignals.mediaStoreQueries() - queries)
        assertEquals(directories, ScanDebugSignals.filesystemDirectories())
        assertEquals(probes, ScanDebugSignals.pathProbes())
        assertEquals(committed, ScanDebugSignals.committedResults())
        rows.forEach { assertEquals("Cached metadata is never dispatched: ${it.path}", 0, ScanDebugSignals.dispatchCount(it.fingerprint)) }
        assertEquals(homeGeneration, HomeDebugState.generation())
        assertEquals("Backend IDLE requires no UI settlement", settlements, ScanDebugSignals.settlementCommits())
    }

    @Test fun oneChangedMediaStorePdfReplacesOnlyItsDurableFingerprintAndMetadata() {
        val rows = (1..5).map { writePdf(File(root, "book-$it.pdf")) }
        database.scannedPdfEntryDao().upsertAll(rows)
        context.mediaRows = rows
        val scanner = startCoordinator()
        val old = rows[2]
        val changed = writePdf(File(old.path), pages = 2)
        assertNotEquals(old.fingerprint, changed.fingerprint)
        context.mediaRows = rows.map { if (it.path == old.path) changed else it }
        val directories = ScanDebugSignals.filesystemDirectories()
        val probes = ScanDebugSignals.pathProbes()
        val committed = ScanDebugSignals.committedResults()
        MetadataWorkerTestHooks.pauseDispatch()
        val before = scanner.index.value.generation

        scanner.refresh(ScanRefreshReason.MANUAL)
        Await.until("the changed on-disk fingerprint is durable before metadata dispatch", Await.LONG_TIMEOUT_MS) {
            val pending = database.scannedPdfEntryDao().findByPath(old.path)
            pending?.fingerprint == changed.fingerprint && pending.metadataState == ScannedPdfMetadataState.PENDING &&
                pending.hash == null && pending.pageCount == 0
        }
        assertEquals(listOf(old.path), database.scannedPdfEntryDao().findPending().map { it.path })
        rows.filter { it.path != old.path }.forEach { assertEquals(it, database.scannedPdfEntryDao().findByPath(it.path)) }
        scanner.resumeMetadataDispatch()
        val completed = awaitTerminal(scanner, before)

        assertEquals(ScanPhase.IDLE, completed.phase)
        assertEquals(changed, database.scannedPdfEntryDao().findByPath(old.path))
        assertEquals(5, database.scannedPdfEntryDao().findAll().size)
        assertTrue(database.scannedPdfEntryDao().findPending().isEmpty())
        assertEquals(1, ScanDebugSignals.dispatchCount(changed.fingerprint))
        rows.forEach { assertEquals(0, ScanDebugSignals.dispatchCount(it.fingerprint)) }
        assertEquals(1, ScanDebugSignals.committedResults() - committed)
        assertEquals(1, ScanDebugSignals.pathProbes() - probes)
        assertEquals(directories, ScanDebugSignals.filesystemDirectories())
        assertEquals(1, context.queries.get())
    }

    @Test fun failedMediaStoreQueriesNeverPruneEvenProvablyMissingCachedFiles() {
        val live = writePdf(File(root, "live.pdf"))
        val missing = writePdf(File(root, "missing.pdf"))
        val rows = listOf(live, missing)
        database.scannedPdfEntryDao().upsertAll(rows)
        val scanner = startCoordinator()
        check(File(missing.path).delete())
        val directories = ScanDebugSignals.filesystemDirectories()
        val probes = ScanDebugSignals.pathProbes()

        for (query in listOf<() -> Cursor?>({ null }, { throw SecurityException("provider unavailable") })) {
            context.queryResult = query
            val before = scanner.index.value.generation
            scanner.refresh(ScanRefreshReason.MANUAL)
            val incomplete = awaitTerminal(scanner, before)

            assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
            assertEquals(setOf(DiscoverySource.MEDIA_STORE), incomplete.sourceFailures)
            assertEquals(rows.toSet(), incomplete.entries.toSet())
            assertEquals(rows.toSet(), database.scannedPdfEntryDao().findAll().toSet())
            assertEquals("Failed discovery must not begin destructive reconciliation", probes, ScanDebugSignals.pathProbes())
        }

        context.queryResult = { context.cursor() }
        val beforeRecovery = scanner.index.value.generation
        scanner.refresh(ScanRefreshReason.MANUAL)
        val recovered = awaitTerminal(scanner, beforeRecovery)
        assertEquals(ScanPhase.IDLE, recovered.phase)
        assertTrue(recovered.sourceFailures.isEmpty())
        assertEquals(listOf(live), database.scannedPdfEntryDao().findAll())
        assertEquals(listOf(live), recovered.entries)
        assertEquals("Only a successful query permits checking unseen cached paths", 2, ScanDebugSignals.pathProbes() - probes)
        assertEquals(directories, ScanDebugSignals.filesystemDirectories())
        assertEquals(3, context.queries.get())
    }

    @Test fun unreadableRootRetainsCacheAndDiscoversLaterRootUntilSuccessfulRescanPrunesMissing() {
        val unreadable = File(root, "blocked")
        val laterRoot = File(root, "readable-later")
        val cached = writePdf(File(unreadable, "cached.pdf"))
        val missing = writePdf(File(laterRoot, "missing.pdf"))
        database.scannedPdfEntryDao().upsertAll(listOf(cached, missing))
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(unreadable.path, laterRoot.path))
        val scanner = startCoordinator()
        assertEquals(listOf(unreadable.path, laterRoot.path), scanner.index.value.scopeRoots)
        check(File(missing.path).delete())
        val added = writePdf(File(laterRoot, "new.pdf"), pages = 2)
        val mode = Os.stat(unreadable.path).st_mode and 0xFFF

        try {
            Os.chmod(unreadable.path, 0)
            assertNull("chmod(0) must make the directory unlistable", unreadable.listFiles())
            val before = scanner.index.value.generation

            scanner.refresh(ScanRefreshReason.FULL_RESCAN)
            val incomplete = awaitTerminal(scanner, before)

            assertEquals(ScanPhase.INCOMPLETE, incomplete.phase)
            assertEquals(setOf(DiscoverySource.FILESYSTEM), incomplete.sourceFailures)
            assertEquals(setOf(cached, missing, added), incomplete.entries.toSet())
            assertEquals(setOf(cached, missing, added), database.scannedPdfEntryDao().findAll().toSet())
            assertTrue(database.scannedPdfEntryDao().findPending().isEmpty())
            assertEquals(1, context.queries.get())
        } finally {
            Os.chmod(unreadable.path, mode)
        }

        val beforeRecovery = scanner.index.value.generation
        scanner.refresh(ScanRefreshReason.FULL_RESCAN)
        val recovered = awaitTerminal(scanner, beforeRecovery)

        assertEquals(ScanPhase.IDLE, recovered.phase)
        assertTrue(recovered.sourceFailures.isEmpty())
        assertEquals(setOf(cached, added), recovered.entries.toSet())
        assertEquals(setOf(cached, added), database.scannedPdfEntryDao().findAll().toSet())
        assertEquals(2, context.queries.get())
    }

    @Test fun scopeExpansionWalksOnlyAddedSubtreesAndKeepsTheGlobalMediaStoreCache() {
        val books = File(root, "books")
        val retained = File(books, "retained")
        val old = writePdf(File(retained, "old.pdf"))
        val outside = writePdf(File(root, "outside/global.pdf")).copy(
            hash = null, pageCount = 0, metadataState = ScannedPdfMetadataState.PENDING,
        )
        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(retained.path))
        database.scannedPdfEntryDao().upsertAll(listOf(old))
        context.mediaRows = listOf(old, outside)
        val scanner = startCoordinator()
        scanner.refresh(ScanRefreshReason.FOREGROUND)
        awaitTerminal(scanner, 0)
        assertEquals(outside, database.scannedPdfEntryDao().findByPath(outside.path))
        val notRewalked = writePdf(File(retained, "unindexed-later.pdf"))
        val added = writePdf(File(books, "added/nested/new.pdf"))
        val directories = ScanDebugSignals.filesystemDirectories()
        val probes = ScanDebugSignals.pathProbes()
        val beforeExpansion = scanner.index.value.generation
        MetadataWorkerTestHooks.pauseDispatch()

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(books.path))
        scanner.post(ScanEvent.ScopeChanged(listOf(retained.path)))
        Await.until("the added subtree is discovered without entering retained roots", Await.LONG_TIMEOUT_MS) {
            val index = scanner.index.value
            index.generation > beforeExpansion && index.phase == ScanPhase.READING_DETAILS && index.showProgress &&
                database.scannedPdfEntryDao().findByPath(added.path)?.metadataState == ScannedPdfMetadataState.PENDING
        }
        assertEquals(3, ScanDebugSignals.filesystemDirectories() - directories)
        assertTrue(
            "Only the new PDF needs discovery and possible reconciliation probes",
            ScanDebugSignals.pathProbes() - probes in 1..2,
        )
        assertEquals(null, database.scannedPdfEntryDao().findByPath(notRewalked.path))
        scanner.resumeMetadataDispatch()
        val expanded = awaitTerminal(scanner, beforeExpansion)
        assertEquals(ScanPhase.IDLE, expanded.phase)
        assertEquals(setOf(old, added), scanner.libraryEntries(expanded).toSet())
        assertEquals(setOf(old, added, outside), database.scannedPdfEntryDao().findAll().toSet())
        assertEquals(1, ScanDebugSignals.dispatchCount(added.fingerprint))
        assertEquals(0, ScanDebugSignals.dispatchCount(old.fingerprint))
        assertEquals(0, ScanDebugSignals.dispatchCount(outside.fingerprint))
        val probesAfterExpansion = ScanDebugSignals.pathProbes()

        preferences.setScanScope(ScanMode.SELECTED_LOCATIONS, setOf(retained.path))
        scanner.post(ScanEvent.ScopeChanged(listOf(books.path)))
        val contracted = awaitTerminal(scanner, expanded.generation)
        assertEquals(ScanPhase.IDLE, contracted.phase)
        assertEquals(listOf(old), scanner.libraryEntries(contracted))
        assertEquals(setOf(old, added, outside), contracted.entries.toSet())
        assertEquals("Contraction must not walk any roots", 3, ScanDebugSignals.filesystemDirectories() - directories)
        assertEquals("Contraction must not probe rows outside its metadata scope", probesAfterExpansion, ScanDebugSignals.pathProbes())
    }

    private fun startCoordinator(): LibraryScanCoordinator {
        val scanner = LibraryScanCoordinator(context, PdfRepository(database))
        coordinator = scanner
        Await.until("Room initialization finishes without any index collector", Await.LONG_TIMEOUT_MS) { scanner.index.value.loaded }
        return scanner
    }

    private fun awaitTerminal(scanner: LibraryScanCoordinator, after: Long): LibraryIndex = Await.untilValue(
        "the backend reaches a terminal phase after generation $after without Home",
        Await.LONG_TIMEOUT_MS,
        read = { scanner.index.value },
    ) { it.loaded && it.generation > after && !it.active }

    private fun writePdf(file: File, pages: Int = 1): ScannedPdfEntry {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val pdf = PdfDocument()
        try {
            repeat(pages) { number ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(200, 300, number + 1).create())
                page.canvas.drawText("Page ${number + 1}", 20f, 30f, Paint())
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
        val fingerprint = SourceFingerprint.normalized(file.path, file.length(), file.lastModified())
        return ScannedPdfEntry(
            fingerprint.path, fingerprint.size, fingerprint.lastModified,
            checkNotNull(DocumentIdentity.of(file)).identity,
            pageCount = pages, metadataState = ScannedPdfMetadataState.COMPLETE,
        )
    }

    private inner class QueryContext(val storage: SharedPreferences) : ContextWrapper(base) {
        @Volatile var mediaRows: List<ScannedPdfEntry> = emptyList()
        @Volatile var queryResult: () -> Cursor? = { cursor() }
        val queries = AtomicInteger()
        private val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?,
            ): Cursor? {
                queries.incrementAndGet()
                return queryResult()
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

        override fun getContentResolver(): ContentResolver = resolver
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = storage
    }
}
