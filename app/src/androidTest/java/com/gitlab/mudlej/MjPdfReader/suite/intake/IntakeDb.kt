// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import java.io.File
import java.time.LocalDateTime

object IntakeDb {

    private val database: AppDatabase get() = Records.database

    fun records(): List<PdfRecord> = Records.all()

    fun record(hash: String): PdfRecord? = Records.byHash(hash)

    fun recordCount(): Int = Records.rowCount()

    fun recordEndingIn(suffix: String): PdfRecord? =
        records().firstOrNull { it.uri.toString().endsWith(suffix) }

    fun bookmarks(): List<UserBookmark> = database.userBookmarkDao().findAll()

    fun saveDestination(sourceKey: String): PdfAnnotationSaveDestination? =
        database.pdfAnnotationSaveDestinationDao().findBySourceKey(sourceKey)

    fun insertRecord(
        hash: String,
        uri: Uri,
        pageNumber: Int = 0,
        fileName: String = "seeded",
        length: Int = 0,
        password: String? = null,
        lastOpened: LocalDateTime = LocalDateTime.now(),
    ): PdfRecord {
        val record = PdfRecord(
            hash,
            pageNumber,
            uri,
            length,
            fileName,
            password,
            lastOpened,
            ReadingStatus.UNSET,
            false,
        )
        database.pdfRecordDao().insert(record)
        return record
    }

    fun insertBookmark(fileHash: String, pageIndex: Int, label: String? = null) {
        database.userBookmarkDao().upsert(UserBookmark(fileHash, pageIndex, label))
    }

    fun insertSaveDestination(sourceKey: String, destinationUri: String, lastSavedHash: String?) {
        database.pdfAnnotationSaveDestinationDao().upsert(
            PdfAnnotationSaveDestination(sourceKey, destinationUri, lastSavedHash, LocalDateTime.now())
        )
    }

    fun insertScannedEntry(path: String, hash: String, file: File? = null) {
        val source = file ?: File(path)
        database.scannedPdfEntryDao().upsertAll(
            listOf(ScannedPdfEntry(path, source.length(), source.lastModified(), hash))
        )
    }

    fun awaitRecordCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<PdfRecord> =
        Await.untilValue(
            "the PdfRecord table holds exactly $expected row(s)",
            timeoutMs,
            read = { records() },
        ) { it.size == expected }

    fun awaitRecord(hash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        Records.awaitRowExists(hash, timeoutMs)

    fun awaitRecordUri(hash: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        Records.awaitRow(hash, "the record for $hash points at $expected", timeoutMs) {
            it != null && it.uri.toString() == expected
        }!!

    fun assertNoRecords() {
        val rows = records()
        if (rows.isNotEmpty()) {
            throw AssertionError(
                "The PdfRecord table must be empty but holds ${rows.size} row(s): " +
                    rows.joinToString { "${it.hash} at ${it.uri}" }
            )
        }
    }

    fun holdEmpty(windowMs: Long = QUIET_WINDOW_MS) {
        Records.assertHolds("the PdfRecord table stays empty", windowMs) { records().isEmpty() }
    }

    fun holdRecordCount(expected: Int, windowMs: Long = QUIET_WINDOW_MS) {
        Records.assertRowCountStays(expected, windowMs)
    }

    const val QUIET_WINDOW_MS = Records.STABLE_ABSENCE_MS
}
