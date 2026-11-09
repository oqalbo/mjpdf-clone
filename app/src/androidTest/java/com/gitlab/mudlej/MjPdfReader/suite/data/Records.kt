// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.data

import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object Records {

    const val STABLE_ABSENCE_MS = 1_500L

    val database: AppDatabase
        get() = AppDatabase.getInstance(TestEnvironment.targetContext.applicationContext)

    fun all(): List<PdfRecord> = database.pdfRecordDao().findAll()

    fun byHash(fileHash: String): PdfRecord? = database.pdfRecordDao().findByHash(fileHash)

    fun rowCount(): Int = all().size

    fun storeReadingDirectionOverride(fileHash: String, direction: String?) {
        val updated = database.pdfRecordDao().updateReadingDirectionOverride(fileHash, direction)
        if (updated != 1) {
            throw AssertionError("Expected to update one PdfRecord row for $fileHash, updated $updated")
        }
    }

    fun bookmarksOf(fileHash: String): List<UserBookmark> =
        database.userBookmarkDao().findByHash(fileHash)

    fun awaitRow(
        fileHash: String,
        condition: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        predicate: (PdfRecord?) -> Boolean,
    ): PdfRecord? = Await.untilValue(
        condition = condition,
        timeoutMs = timeoutMs,
        read = { byHash(fileHash) },
        predicate = predicate,
    )

    fun awaitRowExists(fileHash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfRecord =
        awaitRow(fileHash, "a PdfRecord row exists for $fileHash", timeoutMs) { it != null }!!

    fun awaitRowDisappears(fileHash: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        awaitRow(fileHash, "the PdfRecord row for $fileHash is deleted", timeoutMs) { it == null }
    }

    fun assertHolds(
        condition: String,
        windowMs: Long = STABLE_ABSENCE_MS,
        predicate: () -> Boolean,
    ) {
        Await.assertThroughout(
            condition = condition,
            windowMs = windowMs,
            predicate = predicate,
        )
    }

    fun assertRowKeeps(
        fileHash: String,
        condition: String,
        windowMs: Long = STABLE_ABSENCE_MS,
        predicate: (PdfRecord?) -> Boolean,
    ) {
        assertHolds(condition, windowMs) { predicate(byHash(fileHash)) }
    }

    fun assertRowCountStays(expected: Int, windowMs: Long = STABLE_ABSENCE_MS) {
        assertHolds("the PdfRecord table keeps exactly $expected rows", windowMs) { rowCount() == expected }
    }

    fun awaitStoredPage(
        fileHash: String,
        expected: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): PdfRecord = awaitRow(
        fileHash,
        "the PdfRecord row for $fileHash stores page index $expected",
        timeoutMs,
    ) { it?.pageNumber == expected }!!

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Int =
        Await.untilValue(
            condition = "the PdfRecord table holds $expected rows",
            timeoutMs = timeoutMs,
            read = { rowCount() },
        ) { it == expected }

    fun assertNoRowFor(fileHash: String, windowMs: Long = STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "no PdfRecord row exists for $fileHash",
            windowMs = windowMs,
        ) { byHash(fileHash) == null }
    }

    fun assertStoredPageStays(fileHash: String, expected: Int, windowMs: Long = STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "the PdfRecord row for $fileHash keeps page index $expected",
            windowMs = windowMs,
        ) { byHash(fileHash)?.pageNumber == expected }
    }
}
