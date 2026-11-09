// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import java.io.File
import java.time.LocalDateTime

object ResilienceSeed {

    fun identityOf(file: File): String = DocumentIdentity.of(file)?.identity
        ?: throw AssertionError("DocumentIdentity could not be computed for ${file.absolutePath}")

    fun insertRecord(
        hash: String,
        uri: Uri,
        fileName: String,
        pageNumber: Int = 0,
        pages: Int = 0,
        lastOpened: LocalDateTime = LocalDateTime.now(),
    ): PdfRecord {
        val record = PdfRecord(
            hash = hash,
            pageNumber = pageNumber,
            uri = uri,
            length = pages,
            fileName = fileName,
            password = null,
            lastOpened = lastOpened,
            reading = ReadingStatus.UNSET,
            favorite = false,
        )
        Records.database.pdfRecordDao().insert(record)
        return record
    }

    fun insertRecordFor(file: File, pageNumber: Int = 0, pages: Int = 0): PdfRecord =
        insertRecord(identityOf(file), Uri.fromFile(file), file.name, pageNumber, pages)

    fun insertBookmarks(fileHash: String, count: Int): List<UserBookmark> {
        val bookmarks = (0 until count).map { index ->
            UserBookmark(
                fileHash = fileHash,
                pageIndex = index,
                label = "Bookmark ${index + 1}",
                createdAt = LocalDateTime.now().minusMinutes(index.toLong()),
                sortOrder = index,
            )
        }
        Records.database.userBookmarkDao().upsertAll(bookmarks)
        return bookmarks
    }
}
