// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object NavigationRecords {

    private val database: AppDatabase
        get() = AppDatabase.getInstance(TestEnvironment.targetContext.applicationContext)

    fun all(): List<PdfRecord> = database.pdfRecordDao().findAll()

    fun byHash(fileHash: String): PdfRecord? = database.pdfRecordDao().findByHash(fileHash)

    fun rowCount(): Int = all().size

    fun bookmarksOf(fileHash: String): List<UserBookmark> =
        database.userBookmarkDao().findByHash(fileHash)

    fun awaitStoredPage(
        fileHash: String,
        expected: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): PdfRecord = Await.untilValue(
        condition = "the PdfRecord row for $fileHash stores page index $expected",
        timeoutMs = timeoutMs,
        read = { byHash(fileHash) },
    ) { it?.pageNumber == expected }!!

    fun awaitRowExists(
        fileHash: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): PdfRecord = Await.untilValue(
        condition = "a PdfRecord row exists for $fileHash",
        timeoutMs = timeoutMs,
        read = { byHash(fileHash) },
    ) { it != null }!!

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

    const val STABLE_ABSENCE_MS = 1_500L
}
