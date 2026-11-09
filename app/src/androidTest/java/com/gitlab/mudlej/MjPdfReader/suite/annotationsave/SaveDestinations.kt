// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import java.time.LocalDateTime

object SaveDestinations {

    const val TABLE = "PdfAnnotationSaveDestination"
    const val STABLE_WINDOW_MS = 1_500L

    fun sourceKeyOf(documentUri: String): String = SourceKey.of(Uri.parse(documentUri))

    fun forSource(documentUri: String): PdfAnnotationSaveDestination? =
        Records.database.pdfAnnotationSaveDestinationDao().findBySourceKey(sourceKeyOf(documentUri))

    fun rowCount(): Int {
        val cursor = Records.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $TABLE")
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun sourceKeys(): List<String> {
        val cursor = Records.database.openHelper.readableDatabase.query("SELECT sourceKey FROM $TABLE")
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(it.getString(0))
            }
        }
    }

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Int =
        Await.untilValue(
            "the $TABLE table holds $expected rows",
            timeoutMs,
            read = { rowCount() },
        ) { it == expected }

    fun awaitRowForSource(documentUri: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PdfAnnotationSaveDestination =
        Await.untilNotNull("a save destination row exists for the open document", timeoutMs) {
            forSource(documentUri)
        }

    fun assertRowCountStays(expected: Int, windowMs: Long = STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the $TABLE table keeps exactly $expected rows",
            windowMs = windowMs,
        ) { rowCount() == expected }
    }

    fun persistedWriteGrantExists(uri: Uri): Boolean =
        TestEnvironment.targetContext.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isWritePermission }

    fun persistedGrantExists(uri: Uri): Boolean =
        TestEnvironment.targetContext.contentResolver.persistedUriPermissions.any { it.uri == uri }

    fun withinOneMinuteOfNow(moment: LocalDateTime): Boolean {
        val now = LocalDateTime.now()
        return !moment.isAfter(now.plusMinutes(1)) && !moment.isBefore(now.minusMinutes(1))
    }
}
