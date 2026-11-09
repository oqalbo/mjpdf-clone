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
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.LibraryIndex
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRecordRelinkTest {
    @Test fun liveMoveInvalidatesWarmAvailabilityAndRelinksWithoutClobberingANewerRead() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val settings = context.getSharedPreferences("home-record-relink-test", Context.MODE_PRIVATE)
        val directory = File(context.cacheDir, "home-record-relink-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        try {
            val preferences = Preferences(settings).also { it.setHistoryEnabled(true) }
            val repository = PdfRepository(database)
            val controller = HomeLibraryController(repository, preferences, HistoryPolicy(preferences))
            val original = File(directory, "original.pdf").apply { writeText("%PDF-1.4 record-cache") }
            assertTrue(original.setLastModified(1_700_000_000_789L))
            val record = PdfRecord(
                hash = "moving", pageNumber = 7, uri = Uri.fromFile(original), length = 20,
                fileName = "original", password = null, lastOpened = LocalDateTime.of(2026, 1, 1, 12, 0),
                reading = ReadingStatus.READING, favorite = true,
            )
            repository.saveRecordInBackground(record)
            val records = controller.observeRecords().first()
            val initial = LibraryIndex(
                entries = listOf(ScannedPdfEntry(
                    original.path, original.length(), original.lastModified(), record.hash,
                    pageCount = 20, metadataState = ScannedPdfMetadataState.COMPLETE,
                )),
                loaded = true, generation = 1, scopeRoots = listOf(directory.path),
            )
            val cache = HomeRecordCache()
            cache.updateRecords(records.map { HomeRecordCache.Identity(it.hash, it.uri.toString(), it.sourceUri) })
            cache.onLibraryIndex(initial)
            val probe = AvailabilityProbe(context, hasFullAccess = true)
            var probes = 0
            val probeUri: suspend (String) -> Availability = { uri ->
                probes++
                probe.availabilityOf(Uri.parse(uri))
            }
            assertEquals(Availability.AVAILABLE, cache.resolve(probeUri)?.availability?.get(record.uri.toString()))

            val moved = File(directory, "moved.pdf")
            assertTrue(original.renameTo(moved))
            val completed = initial.copy(
                generation = 2, entries = initial.entries.map { it.copy(path = moved.path) },
            )
            assertTrue(cache.onLibraryIndex(completed))
            val validated = requireNotNull(cache.resolve(probeUri))
            assertEquals(2, probes)
            assertEquals(Availability.MISSING, validated.availability[record.uri.toString()])
            val newerRead = record.lastOpened.plusDays(1)
            repository.setLastOpened(record.hash, newerRead)
            repository.setPageNumber(record.hash, 9)
            controller.relinkCompletedIndex(completed, records, validated.availability, probe) { true }
            val healed = withTimeout(5_000) {
                controller.observeRecords().first { it.single().uri == Uri.fromFile(moved) }.single()
            }
            assertEquals("moved", healed.fileName)
            assertEquals(newerRead, healed.lastOpened)
            assertEquals(9, healed.pageNumber)
            assertTrue(healed.favorite)
            assertEquals(ReadingStatus.READING, healed.reading)

            assertFalse(cache.onLibraryIndex(completed.copy(
                revision = 10, entries = completed.entries.map { it.copy(pageCount = 21) },
            )))
            cache.resolve(probeUri)
            assertEquals(2, probes)
        } finally {
            database.close()
            settings.edit().clear().commit()
            directory.deleteRecursively()
        }
    }
}
