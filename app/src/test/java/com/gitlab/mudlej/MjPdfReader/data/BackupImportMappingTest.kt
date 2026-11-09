// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.support.FakeFilesContext
import java.io.InputStream
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupImportMappingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recordsWithNullEmptyOrWhitespaceHashesAreDroppedBeforeParsingTheirUris() {
        val parsed = mutableListOf<String>()
        val records = BackupImportMapping.toRecords(
            listOf(null, "", "  ", "valid").map { BackupPdfRecord(hash = it, uri = "content://books/$it") },
            parseUri = { parsed.add(it); Uri.parse(it) },
        )

        assertEquals(listOf("valid"), records.map { it.hash })
        assertEquals(listOf("content://books/valid"), parsed)
        assertEquals("content://books/valid", records.single().uri.toString())
    }

    @Test
    fun validRecordsPreserveTheirReadingAndDocumentSettings() {
        val date = LocalDateTime.of(2026, 10, 1, 12, 30)
        val backup = BackupPdfRecord(hash = "book", pageNumber = 40, length = 120, fileName = "Book",
            password = "secret", lastOpened = date.toString(), reading = "READING", favorite = true,
            cropMargins = "1,2,3,4", cropMarginsVersion = 2, autoScrollSpeed = 7,
            readingDirectionOverride = "rtl", detectedReadingDirection = "ltr", documentTitle = "Title",
            uri = "content://books/one", textModeJoinParagraphs = true, textModeDetectHeadings = false,
            textModeCodeBlocks = true, hidden = true, sourceUri = "https://example.com/book.pdf")
        val record = BackupImportMapping.toRecords(listOf(backup)).single()

        assertEquals("book", record.hash)
        assertEquals(40, record.pageNumber)
        assertEquals(120, record.length)
        assertEquals("Book", record.fileName)
        assertEquals("secret", record.password)
        assertEquals(date, record.lastOpened)
        assertEquals(ReadingStatus.READING, record.reading)
        assertTrue(record.favorite)
        assertEquals("1,2,3,4", record.cropMargins)
        assertEquals(2, record.cropMarginsVersion)
        assertEquals(7, record.autoScrollSpeed)
        assertEquals("rtl", record.readingDirectionOverride)
        assertEquals("ltr", record.detectedReadingDirection)
        assertEquals("Title", record.documentTitle)
        assertEquals("content://books/one", record.uri.toString())
        assertEquals(true, record.textModeJoinParagraphs)
        assertEquals(false, record.textModeDetectHeadings)
        assertEquals(true, record.textModeCodeBlocks)
        assertTrue(record.hidden)
        assertEquals("https://example.com/book.pdf", record.sourceUri)
    }

    @Test
    fun negativeBookmarkPagesAndBlankHashesAreDroppedButPageZeroSurvives() {
        val now = LocalDateTime.of(2026, 1, 2, 3, 4)
        val bookmarks = BackupImportMapping.toBookmarks(listOf(
            BackupUserBookmark("book", -1),
            BackupUserBookmark(null, 0),
            BackupUserBookmark("", 0),
            BackupUserBookmark("  ", 0),
            BackupUserBookmark("book", 0, label = "Start"),
        ), now = { now })

        assertEquals(1, bookmarks.size)
        assertEquals("book", bookmarks.single().fileHash)
        assertEquals(0, bookmarks.single().pageIndex)
        assertEquals("Start", bookmarks.single().label)
        assertEquals(now, bookmarks.single().createdAt)
    }

    @Test
    fun negativeBookmarkSortOrderFallsBackToThePageWhileExplicitOrderSurvives() {
        val date = LocalDateTime.of(2026, 1, 2, 3, 4)
        val bookmarks = BackupImportMapping.toBookmarks(listOf(
            BackupUserBookmark("book", 7, createdAt = date.toString(), sortOrder = -1),
            BackupUserBookmark("book", 8, createdAt = date.toString(), sortOrder = 0),
        ), now = { error("Valid bookmark dates must not consult the clock") })

        assertEquals(listOf(7, 0), bookmarks.map { it.sortOrder })
        assertEquals(listOf(date, date), bookmarks.map { it.createdAt })
    }

    @Test
    fun invalidBookmarkDatesUseTheInjectedClock() {
        val now = LocalDateTime.of(2026, 5, 6, 7, 8)
        val bookmarks = BackupImportMapping.toBookmarks(listOf(null, "", "invalid", "2026-01-02T03:04:00Z")
            .mapIndexed { index, date -> BackupUserBookmark("book", index, createdAt = date) }, now = { now })

        assertEquals(listOf(now, now, now, now), bookmarks.map { it.createdAt })
    }

    @Test
    fun theMinimumLastOpenedDateRoundTripsThroughTheBackupCodec() {
        val data = BackupData(schemaVersion = 1, pdfRecords = listOf(
            BackupPdfRecord(hash = "book", lastOpened = LocalDateTime.MIN.toString()),
        ))
        val decoded = BackupCodec.decode(BackupCodec.encode(data).toString())
        val record = BackupImportMapping.toRecords(decoded.pdfRecords!!).single()

        assertEquals(LocalDateTime.MIN, BackupImportMapping.parseDate(LocalDateTime.MIN.toString()))
        assertEquals(LocalDateTime.MIN, record.lastOpened)
    }

    @Test
    fun offsetLastOpenedDatesAndUnknownReadingStatusesUseTheirFallbacks() {
        val record = BackupImportMapping.toRecords(listOf(BackupPdfRecord(hash = "book",
            lastOpened = "2026-11-03T10:00:00+01:00", reading = "FUTURE_STATUS"))).single()

        assertEquals(LocalDateTime.MIN, record.lastOpened)
        assertEquals(ReadingStatus.UNSET, record.reading)
        assertEquals("", record.uri.toString())
        assertEquals("", record.fileName)
    }

    @Test
    fun theSchemaGateAcceptsOnlyTheSupportedVersion() {
        val errors = listOf(0, 1, 2).map { version ->
            try {
                BackupManager.validateSchemaVersion(version)
                null
            } catch (exception: BackupException) {
                exception.messageRes
            }
        }

        assertEquals(listOf(R.string.backup_error_not_valid, null, R.string.backup_error_newer_version), errors)
    }

    @Test
    fun exactlyThirtyTwoMebibytesAreAccepted() {
        val input = RepeatedByteStream(32 * 1024 * 1024)
        val bytes = BackupManager.readBounded(input)

        assertEquals(32 * 1024 * 1024, bytes.size)
        assertEquals(90.toByte(), bytes.first())
        assertEquals(90.toByte(), bytes.last())
        assertEquals(0, input.remaining)
    }

    @Test
    fun oneByteBeyondThirtyTwoMebibytesIsRejectedAsTooLarge() {
        val input = RepeatedByteStream(32 * 1024 * 1024 + 1)
        val error = assertThrows(BackupException::class.java) { BackupManager.readBounded(input) }

        assertEquals(R.string.backup_error_too_large, error.messageRes)
        assertEquals(0, input.remaining)
    }

    @Test
    fun anEmptyHashRecordStillMakesTheImportIncludeHistory() {
        val data = BackupCodec.decode("""{"schemaVersion":1,"pdfRecords":[{"hash":""}]}""")
        val plan = manager().prepareImport(data)

        assertTrue(plan.includesHistory)
        assertEquals(0, plan.records.size)
        assertEquals(0, plan.bookmarks.size)
        assertNull(plan.settingsPuts)
    }

    @Test
    fun bookmarksWithoutMatchingRecordsRemainInTheImportPlan() {
        val data = BackupData(schemaVersion = 1, pdfRecords = listOf(BackupPdfRecord(hash = "known")),
            userBookmarks = listOf(BackupUserBookmark("orphan", 5, "Chapter", "2026-01-02T03:04", 2)))
        val plan = manager().prepareImport(data)

        assertTrue(plan.includesHistory)
        assertEquals(listOf("known"), plan.records.map { it.hash })
        assertEquals(listOf("orphan"), plan.bookmarks.map { it.fileHash })
        assertEquals(5, plan.bookmarks.single().pageIndex)
        assertEquals("Chapter", plan.bookmarks.single().label)
        assertEquals(2, plan.bookmarks.single().sortOrder)
    }

    private fun manager(): BackupManager = BackupManager(
        FakeFilesContext(temporaryFolder.newFolder("files"), temporaryFolder.newFolder("cache")),
        PdfRepository(AppDatabase_Impl()),
    )

    private class RepeatedByteStream(var remaining: Int) : InputStream() {
        override fun read(): Int = if (remaining == 0) -1 else {
            remaining--
            90
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining == 0) return -1
            val count = minOf(length, remaining)
            buffer.fill(90, offset, offset + count)
            remaining -= count
            return count
        }
    }
}
