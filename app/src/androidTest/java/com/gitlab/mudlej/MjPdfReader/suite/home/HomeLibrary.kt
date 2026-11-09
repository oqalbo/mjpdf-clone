// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeFixtures
import java.io.File
import java.time.LocalDateTime

object HomeLibrary {

    const val QUIET_WINDOW_MS = 1_500L

    private val database: AppDatabase get() = AppDatabase.getInstance(TestEnvironment.targetContext)

    fun identityOf(file: File): String {
        if (!file.isFile) {
            throw AssertionError(
                "Cannot compute an identity for ${file.absolutePath} because no readable file is there. " +
                    "Seed the file before the record that points at it."
            )
        }
        return IntakeFixtures.expectedIdentity(file)
    }

    fun seedInto(directory: File, seeds: List<BookSeed>): SeededLibrary {
        val books = seeds.map { seed ->
            val file = IntakeFixtures.copyInto(directory, seed.fixture, seed.name)
            SeededBook(seed, file, identityOf(file))
        }
        insertAll(books)
        return SeededLibrary(directory, books)
    }

    fun seedInPlace(root: File, seeds: List<BookSeed>): SeededLibrary {
        val books = seeds.map { seed ->
            val file = File(root, seed.name)
            if (!file.isFile) {
                throw AssertionError(
                    "Expected ${file.absolutePath} to already exist on the device. " +
                        "python3 testing/runner/run.py pushes testing/fixtures/out/library/TestBooks into /sdcard/TestBooks " +
                        "before the suite starts."
                )
            }
            SeededBook(seed, file, identityOf(file))
        }
        insertAll(books)
        return SeededLibrary(root, books)
    }

    fun insertAll(books: List<SeededBook>) {
        books.filter { it.seed.record }.forEach { insertRecord(it) }
        books.filter { it.seed.indexed }.forEach { insertScannedEntry(it) }
        books.forEach { book -> book.seed.bookmarks.forEach { insertBookmark(book.hash, it) } }
    }

    fun insertRecord(book: SeededBook) {
        val seed = book.seed
        database.pdfRecordDao().insert(
            PdfRecord(
                hash = book.hash,
                pageNumber = seed.pageNumber,
                uri = book.uri,
                length = seed.pages,
                fileName = book.fileName,
                password = null,
                lastOpened = seed.lastOpened,
                reading = seed.reading,
                favorite = seed.favorite,
                documentTitle = seed.documentTitle,
                hidden = seed.hidden,
            )
        )
    }

    fun insertScannedEntry(book: SeededBook) {
        database.scannedPdfEntryDao().upsertAll(listOf(scannedEntryFor(book, book.hash)))
    }

    fun insertScannedEntryWithoutHash(book: SeededBook) {
        database.scannedPdfEntryDao().upsertAll(listOf(scannedEntryFor(book, null)))
    }

    private fun scannedEntryFor(book: SeededBook, hash: String?): ScannedPdfEntry {
        if (!book.file.isFile) {
            throw AssertionError(
                "Cannot seed a ScannedPdfCache row for ${book.path} because no readable file is there. " +
                    "A row whose size and lastModified do not match the file on disk is replaced on the next " +
                    "walk, which drops both the hash and the page count without failing anything, so seed the " +
                    "file first."
            )
        }
        return ScannedPdfEntry(
            path = book.path,
            size = book.file.length(),
            lastModified = book.file.lastModified() / 1_000L * 1_000L,
            hash = hash,
            pageCount = book.seed.pages,
        )
    }

    fun insertBookmark(hash: String, pageIndex: Int, label: String? = null) {
        database.userBookmarkDao().upsert(UserBookmark(hash, pageIndex, label))
    }

    fun records(): List<PdfRecord> = database.pdfRecordDao().findAll()

    fun record(hash: String): PdfRecord? = database.pdfRecordDao().findByHash(hash)

    fun requireRecord(hash: String): PdfRecord = record(hash)
        ?: throw AssertionError(
            "No PdfRecord row exists for $hash. The table holds: " +
                records().joinToString { "${it.hash} at ${it.uri}" }
        )

    fun recordNamed(fileName: String): PdfRecord? =
        records().firstOrNull { it.fileName == fileName.removeSuffix(BookSeed.PDF_SUFFIX) }

    fun recordCount(): Int = records().size

    fun replaceRecordUri(hash: String, uri: Uri): PdfRecord {
        val replacement = requireRecord(hash).copy(uri = uri)
        database.pdfRecordDao().insert(replacement)
        return requireRecord(hash)
    }

    fun scannedEntries(): List<ScannedPdfEntry> = database.scannedPdfEntryDao().findAll()

    fun scannedEntry(path: String): ScannedPdfEntry? = scannedEntries().firstOrNull { it.path == path }

    fun bookmarks(hash: String): List<UserBookmark> = database.userBookmarkDao().findByHash(hash)

    fun clearRecords() {
        database.pdfRecordDao().deleteAll()
    }

    fun deleteRecords(hashes: List<String>) {
        database.pdfRecordDao().deleteByHashes(hashes)
    }

    fun deleteScannedEntries(paths: List<String>) {
        database.scannedPdfEntryDao().deleteByPaths(paths)
    }

    fun awaitRecordCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<PdfRecord> =
        Await.untilValue(
            "the PdfRecord table holds exactly $expected row(s)",
            timeoutMs,
            read = { records() },
        ) { it.size == expected }

    fun awaitRecord(hash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        Await.untilNotNull("a PdfRecord row exists for $hash", timeoutMs) { record(hash) }

    fun awaitNoRecord(hash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no PdfRecord row remains for $hash", timeoutMs) { record(hash) == null }
    }

    fun awaitReading(
        hash: String,
        status: ReadingStatus,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): PdfRecord = awaitColumn(hash, "reading is $status", timeoutMs) { it.reading == status }

    fun awaitHidden(hash: String, hidden: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        awaitColumn(hash, "hidden is $hidden", timeoutMs) { it.hidden == hidden }

    fun awaitNeverOpened(hash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        awaitColumn(hash, "lastOpened is back to the unset date", timeoutMs) { it.lastOpened == NEVER_OPENED }

    fun awaitOpenedAfter(
        hash: String,
        moment: LocalDateTime,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): PdfRecord = awaitColumn(hash, "lastOpened moves past $moment", timeoutMs) { it.lastOpened > moment }

    fun awaitRecordUri(hash: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        awaitColumn(hash, "uri is $expected", timeoutMs) { it.uri.toString() == expected }

    fun awaitRecordFileName(hash: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        awaitColumn(hash, "fileName is $expected", timeoutMs) { it.fileName == expected }

    fun awaitScannedEntry(path: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ScannedPdfEntry =
        Await.untilNotNull("a ScannedPdfCache row exists for $path", timeoutMs) { scannedEntry(path) }

    fun awaitNoScannedEntry(path: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no ScannedPdfCache row remains for $path", timeoutMs) { scannedEntry(path) == null }
    }

    fun awaitNoBookmarks(hash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no UserBookmark row remains for $hash", timeoutMs) { bookmarks(hash).isEmpty() }
    }

    fun holdRecordCount(expected: Int, windowMs: Long = QUIET_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the PdfRecord table stays at $expected row(s)",
            windowMs = windowMs,
        ) { records().size == expected }
    }

    fun holdRecordUnchanged(record: PdfRecord, windowMs: Long = QUIET_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the record for ${record.hash} stays exactly as it was seeded",
            windowMs = windowMs,
        ) { record(record.hash) == record }
    }

    private fun awaitColumn(
        hash: String,
        condition: String,
        timeoutMs: Long,
        predicate: (PdfRecord) -> Boolean,
    ): PdfRecord = Await.untilValue(
        "the record for $hash reports $condition",
        timeoutMs,
        read = { record(hash) },
    ) { it != null && predicate(it) }!!
}
