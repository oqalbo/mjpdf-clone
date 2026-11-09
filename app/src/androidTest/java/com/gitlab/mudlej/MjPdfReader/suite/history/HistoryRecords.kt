// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.net.Uri
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveDestinations
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import java.io.File
import java.time.LocalDateTime

object HistoryRecords {

    const val COVER_DIRECTORY = "covers"

    val unsetDate: LocalDateTime = LocalDateTime.parse(PdfRecord.UNSET_DATE)

    fun identityOf(fixture: File): String = DocumentIdentity.of(fixture)?.identity
        ?: throw AssertionError(
            "DocumentIdentity could not read ${fixture.absolutePath}, so no test can name the record it produces"
        )

    fun byName(fileName: String): PdfRecord? = Records.all().firstOrNull { it.fileName == fileName }

    fun awaitRecordNamed(fileName: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): PdfRecord =
        Await.untilNotNull("a PdfRecord row named \"$fileName\" exists", timeoutMs) { byName(fileName) }

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<PdfRecord> =
        Await.untilValue(
            "the PdfRecord table holds $expected rows",
            timeoutMs,
            read = { Records.all() },
        ) { it.size == expected }

    fun awaitEmpty(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the PdfRecord table is empty", timeoutMs) { Records.all().isEmpty() }
    }

    fun holdEmpty(windowMs: Long = Records.STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "the PdfRecord table stays empty",
            windowMs = windowMs,
        ) { Records.all().isEmpty() }
    }

    fun seedRecord(
        hash: String,
        uri: Uri,
        fileName: String,
        pageNumber: Int = 0,
        length: Int = 0,
        lastOpened: LocalDateTime = unsetDate,
        password: String? = null,
    ): PdfRecord {
        val record = PdfRecord(
            hash = hash,
            pageNumber = pageNumber,
            uri = uri,
            length = length,
            fileName = fileName,
            password = password,
            lastOpened = lastOpened,
            reading = ReadingStatus.UNSET,
            favorite = false,
        )
        Records.database.pdfRecordDao().insert(record)
        return record
    }

    fun assertLastOpenedStays(
        fileHash: String,
        expected: LocalDateTime,
        windowMs: Long = Records.STABLE_ABSENCE_MS,
    ) {
        Await.assertThroughout(
            condition = "the PdfRecord row for $fileHash keeps its last opened date $expected",
            windowMs = windowMs,
        ) { Records.byHash(fileHash)?.lastOpened == expected }
    }

    fun seedBookmark(fileHash: String, pageIndex: Int, label: String): UserBookmark {
        val bookmark = UserBookmark(
            fileHash = fileHash,
            pageIndex = pageIndex,
            label = label,
            createdAt = LocalDateTime.now(),
            sortOrder = 0,
        )
        Records.database.userBookmarkDao().upsert(bookmark)
        return bookmark
    }

    fun allBookmarks(): List<UserBookmark> = Records.database.userBookmarkDao().findAll()

    fun setHidden(fileHash: String, hidden: Boolean) {
        Records.database.pdfRecordDao().updateHidden(fileHash, hidden)
    }

    fun seedSaveDestination(sourceUri: Uri, destinationUri: String, lastSavedHash: String) {
        Records.database.pdfAnnotationSaveDestinationDao().upsert(
            PdfAnnotationSaveDestination(
                sourceKey = SourceKey.of(sourceUri),
                destinationUri = destinationUri,
                lastSavedHash = lastSavedHash,
                lastSavedAt = LocalDateTime.now(),
            )
        )
    }

    fun saveDestinationForSource(sourceUri: Uri): PdfAnnotationSaveDestination? =
        Records.database.pdfAnnotationSaveDestinationDao().findBySourceKey(SourceKey.of(sourceUri))

    fun saveDestinationForHash(fileHash: String): PdfAnnotationSaveDestination? =
        Records.database.pdfAnnotationSaveDestinationDao().findByLastSavedHash(fileHash)

    fun awaitNoSaveDestinations(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the PdfAnnotationSaveDestination table is empty",
            timeoutMs,
            read = { SaveDestinations.rowCount() },
        ) { it == 0 }
    }

    fun journalFor(sourceUri: Uri): File = AnnotationJournalFile.fileFor(sourceUri.toString())

    fun seedJournal(sourceUri: Uri, line: String): File {
        val journal = journalFor(sourceUri)
        journal.parentFile?.mkdirs()
        journal.writeText("$line\n")
        return journal
    }

    val coverDirectory: File
        get() = File(TestEnvironment.targetContext.cacheDir, COVER_DIRECTORY)

    fun coverFiles(fileHash: String): List<File> =
        coverDirectory.listFiles()?.filter { it.name.startsWith("$fileHash-w") }.orEmpty()

    fun allCoverFiles(): List<File> = coverDirectory.listFiles()?.toList().orEmpty()

    fun seedCover(fileHash: String, bucketWidth: Int = COVER_BUCKET_WIDTH): File {
        val cover = File(coverDirectory, "$fileHash-w$bucketWidth.jpg")
        cover.parentFile?.mkdirs()
        cover.writeBytes(ByteArray(COVER_PLACEHOLDER_BYTES))
        return cover
    }

    fun awaitNoCoverFor(fileHash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no cover file remains for $fileHash", timeoutMs) { coverFiles(fileHash).isEmpty() }
    }

    fun holdNoCoverFor(fileHash: String, windowMs: Long = Records.STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "no cover file is ever written for $fileHash",
            windowMs = windowMs,
        ) { coverFiles(fileHash).isEmpty() }
    }

    fun awaitCoverDirectoryEmpty(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the cover cache directory holds no files", timeoutMs) { allCoverFiles().isEmpty() }
    }

    fun fileUri(file: File): Uri = file.toUri()

    const val COVER_BUCKET_WIDTH = 320
    const val COVER_PLACEHOLDER_BYTES = 64
}
