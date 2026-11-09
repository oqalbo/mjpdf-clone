// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File
import java.time.LocalDateTime

object ScanRecords {

    const val STABLE_WINDOW_MS = 1_500L

    private val database: AppDatabase
        get() = AppDatabase.getInstance(TestEnvironment.targetContext.applicationContext)

    fun byHash(hash: String): PdfRecord? = database.pdfRecordDao().findByHash(hash)

    fun seedOpenedRecord(
        file: File,
        pageNumber: Int = 0,
        length: Int = 1,
    ): PdfRecord {
        val hash = ScanCache.identityOf(file)
        val record = PdfRecord(
            hash = hash,
            pageNumber = pageNumber,
            uri = Uri.fromFile(file),
            length = length,
            fileName = file.nameWithoutExtension,
            password = null,
            lastOpened = LocalDateTime.now(),
            reading = ReadingStatus.READING,
            favorite = false,
        )
        database.pdfRecordDao().insert(record)
        return record
    }

    fun awaitUri(hash: String, expected: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): PdfRecord =
        Await.untilValue(
            condition = "the PdfRecord row for $hash points at $expected",
            timeoutMs = timeoutMs,
            read = { byHash(hash) },
        ) { it?.uri?.toString() == expected }!!

    fun assertUriStays(hash: String, expected: String, windowMs: Long = STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the PdfRecord row for $hash keeps pointing at $expected",
            windowMs = windowMs,
        ) { byHash(hash)?.uri?.toString() == expected }
    }

    fun fileUriOf(file: File): String = Uri.fromFile(file).toString()
}
