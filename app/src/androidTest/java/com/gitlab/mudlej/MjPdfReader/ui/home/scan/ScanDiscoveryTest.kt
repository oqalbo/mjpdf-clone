// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home.scan

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ScanDiscoveryTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(base.cacheDir, "discovery-${UUID.randomUUID()}")
    private val columns = arrayOf(
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
    )

    @Before fun createRoot() {
        check(root.mkdirs())
    }

    @After fun removeRoot() {
        root.deleteRecursively()
    }

    @Test fun ordinaryRefreshKeepsKnownGlobalRowsWithoutCheckingFilesOrWalking() = runBlocking {
        val known = ScannedPdfEntry("/storage/unavailable/books/book.PDF", 7, 1_000, "cached")
        val cursor = rows(known)
        val context = QueryContext { cursor }
        val events = mutableListOf<ScanEvent>()

        ScanDiscovery(context, events::add).run(12, knownEntries = listOf(known))

        assertEquals(listOf(known), entries(events))
        assertEquals(listOf(ScanEvent.DiscoverySourceFinished(12, DiscoverySource.MEDIA_STORE)), terminals(events))
        assertEquals(1, context.queries)
        assertNull(context.sortOrder)
        assertTrue(cursor.isClosed)
    }

    @Test fun selectedFilesystemRootsDoNotFilterGlobalMediaStoreRows() = runBlocking {
        val known = ScannedPdfEntry("/outside/library/book.pdf", 7, 1_000, "cached")
        val events = mutableListOf<ScanEvent>()

        ScanDiscovery(QueryContext { rows(known) }, events::add).run(
            12, roots = listOf(root), knownEntries = listOf(known),
        )

        assertEquals(listOf(known), entries(events))
        assertEquals(
            listOf(
                ScanEvent.DiscoverySourceFinished(12, DiscoverySource.MEDIA_STORE),
                ScanEvent.DiscoverySourceFinished(12, DiscoverySource.FILESYSTEM),
            ),
            terminals(events),
        )
    }

    @Test fun staleProviderFieldsPreserveCorrectCachedMetadata() = runBlocking {
        val known = actualEntry().copy(hash = "cached", pageCount = 9, metadataState = ScannedPdfMetadataState.COMPLETE)
        val stale = known.copy(size = 900, lastModified = 2_000)
        val events = mutableListOf<ScanEvent>()

        ScanDiscovery(QueryContext { rows(stale) }, events::add).run(12, knownEntries = listOf(known))

        assertEquals(listOf(known), entries(events))
    }

    @Test fun newAndApparentlyChangedRowsUseActualFileFingerprints() = runBlocking {
        val actual = actualEntry()
        val stale = actual.copy(size = 900, lastModified = 2_000)
        for (known in listOf(emptyList(), listOf(actual.copy(size = 100, hash = "old")))) {
            val events = mutableListOf<ScanEvent>()

            ScanDiscovery(QueryContext { rows(stale) }, events::add).run(12, knownEntries = known)

            assertEquals(listOf(actual), entries(events))
        }
    }

    @Test fun suffixChecksStillRejectNonPdfRowsAndBatchOrderMatchesProviderOrder() = runBlocking {
        val z = ScannedPdfEntry("/books/z.PDF", 7, 1_000, "cached")
        val a = z.copy(path = "/books/a.pdf")
        val text = z.copy(path = "/books/text.txt")
        val events = mutableListOf<ScanEvent>()

        ScanDiscovery(QueryContext { rows(z, text, a) }, events::add).run(12, knownEntries = listOf(z, text, a))

        assertEquals(listOf(z, a), entries(events))
    }

    @Test fun missingNewProviderRowIsNotAcceptedFromProviderFields() = runBlocking {
        val missing = ScannedPdfEntry(File(root, "missing.pdf").path, 7, 1_000, null)
        val events = mutableListOf<ScanEvent>()

        ScanDiscovery(QueryContext { rows(missing) }, events::add).run(12)

        assertTrue(entries(events).isEmpty())
        assertFalse(events.any { it is ScanEvent.DiscoverySourceFailed })
    }

    @Test fun nullCursorAndIncompleteProjectionFailWithoutFilesystemFallback() = runBlocking {
        for (cursor in listOf(null, MatrixCursor(arrayOf(MediaStore.MediaColumns.DATA)))) {
            val context = QueryContext { cursor }
            val events = mutableListOf<ScanEvent>()

            ScanDiscovery(context, events::add).run(12)

            assertTrue(entries(events).isEmpty())
            assertEquals(2, events.size)
            assertTrue(events.first() is ScanEvent.DiscoverySourceFailed)
            assertEquals(DiscoverySource.MEDIA_STORE, (events.first() as ScanEvent.DiscoverySourceFailed).source)
            assertEquals(ScanEvent.DiscoverySourceFinished(12, DiscoverySource.MEDIA_STORE), events.last())
            assertEquals(1, context.queries)
        }
    }

    @Test fun partialCursorFailureRetainsDeliveredBatchesButMarksSourceFailed() = runBlocking {
        for (count in listOf(1, 50)) {
            val known = (1..count).map { ScannedPdfEntry("/books/$it.pdf", 7, 1_000, "cached") }
            val failure = IllegalStateException("cursor failed after $count rows")
            val cursor = object : CursorWrapper(rows(*known.toTypedArray())) {
                override fun moveToNext(): Boolean {
                    if (position == count - 1) throw failure
                    return super.moveToNext()
                }
            }
            val events = mutableListOf<ScanEvent>()

            ScanDiscovery(QueryContext { cursor }, events::add).run(12, knownEntries = known)

            assertEquals(known, entries(events))
            assertSame(failure, events.filterIsInstance<ScanEvent.DiscoverySourceFailed>().single().throwable)
            assertEquals(ScanEvent.DiscoverySourceFinished(12, DiscoverySource.MEDIA_STORE), events.last())
            assertTrue(cursor.isClosed)
        }
    }

    @Test fun unreadableRootDoesNotAbortLaterRootsOrWalksAndFlushesBeforeFailure() = runBlocking {
        val unreadable = File(root, "blocked").also { check(it.mkdirs()) }
        val laterRoot = File(root, "later-root").also { check(it.mkdirs()) }
        val laterWalk = File(root, "later-walk").also { check(it.mkdirs()) }
        val files = listOf(laterRoot, laterWalk).map { directory ->
            File(directory, "book.pdf").also { it.writeText("1234567") }
        }
        val events = mutableListOf<ScanEvent>()
        val mode = Os.stat(unreadable.path).st_mode and 0xFFF

        try {
            Os.chmod(unreadable.path, 0)
            assertNull("chmod(0) must make the directory unlistable", unreadable.listFiles())

            ScanDiscovery(QueryContext { rows() }, events::add).run(
                12,
                walks = listOf(
                    ScanWalk(listOf(unreadable.path, laterRoot.path)),
                    ScanWalk(listOf(laterWalk.path)),
                ),
                knownEntries = emptyList(),
            )

            assertEquals(files.map { it.path }, entries(events).map { it.path })
            val failure = events.filterIsInstance<ScanEvent.DiscoverySourceFailed>().single()
            assertEquals(12L, failure.generation)
            assertEquals(DiscoverySource.FILESYSTEM, failure.source)
            assertEquals(
                listOf(
                    ScanEvent.DiscoverySourceFinished(12, DiscoverySource.MEDIA_STORE),
                    failure,
                    ScanEvent.DiscoverySourceFinished(12, DiscoverySource.FILESYSTEM),
                ),
                terminals(events),
            )
            assertTrue(events.filterIsInstance<ScanEvent.DiscoveryBatch>().all { it.source == DiscoverySource.FILESYSTEM })
            assertEquals(entries(events), entries(events.take(events.indexOf(failure))))
            assertEquals(ScanEvent.DiscoverySourceFinished(12, DiscoverySource.FILESYSTEM), events.last())
        } finally {
            Os.chmod(unreadable.path, mode)
        }
    }

    @Test fun cancellationPropagatesAndPendingBatchTimerCannotEmitAfterReturn() = runBlocking {
        val known = ScannedPdfEntry("/books/book.pdf", 7, 1_000, "cached")
        val cancellation = CancellationException("cancel cursor")
        val cursor = object : CursorWrapper(rows(known)) {
            override fun moveToNext(): Boolean {
                if (position == 0) throw cancellation
                return super.moveToNext()
            }
        }
        val events = CopyOnWriteArrayList<ScanEvent>()
        var thrown: CancellationException? = null

        try {
            ScanDiscovery(QueryContext { cursor }, events::add).run(12, knownEntries = listOf(known))
        } catch (error: CancellationException) {
            thrown = error
        }
        delay(450)

        assertSame(cancellation, thrown)
        assertTrue(events.isEmpty())
        assertTrue(cursor.isClosed)
    }

    private fun actualEntry(): ScannedPdfEntry {
        val file = File(root, "actual.pdf").also { it.writeText("1234567") }
        check(file.setLastModified(1_700_000_000_789L))
        return ScannedPdfEntry(file.path, 7, 1_700_000_000_000L, null)
    }

    private fun rows(vararg entries: ScannedPdfEntry): MatrixCursor = MatrixCursor(columns).also { cursor ->
        entries.forEach { cursor.addRow(arrayOf<Any>(it.path, it.size, it.lastModified / 1_000)) }
    }

    private fun entries(events: List<ScanEvent>): List<ScannedPdfEntry> =
        events.filterIsInstance<ScanEvent.DiscoveryBatch>().flatMap { it.entries }

    private fun terminals(events: List<ScanEvent>): List<ScanEvent> =
        events.filterNot { it is ScanEvent.DiscoveryBatch }

    private inner class QueryContext(private val query: () -> Cursor?) : ContextWrapper(base) {
        var queries = 0
        var sortOrder: String? = "not queried"
        private val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor? {
                queries++
                this@QueryContext.sortOrder = sortOrder
                return query.invoke()
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }.also { it.attachInfo(base, ProviderInfo().apply { authority = "media" }) }
        private val resolver = ContentResolver.wrap(provider)

        override fun getContentResolver(): ContentResolver = resolver
        override fun getApplicationContext(): Context = this
    }
}
