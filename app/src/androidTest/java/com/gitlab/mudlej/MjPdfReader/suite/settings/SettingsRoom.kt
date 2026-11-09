// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import java.time.LocalDateTime

object SettingsRoom {

    fun seedRecord(
        hash: String,
        fileName: String = "$hash.pdf",
        pageNumber: Int = 0,
        password: String? = null,
    ): PdfRecord {
        val record = PdfRecord(
            hash = hash,
            pageNumber = pageNumber,
            uri = "file:///sdcard/Download/$fileName".toUri(),
            length = 0,
            fileName = fileName,
            password = password,
            lastOpened = LocalDateTime.now(),
            reading = ReadingStatus.UNSET,
            favorite = false,
        )
        Records.database.pdfRecordDao().insert(record)
        return record
    }

    fun seedBookmark(fileHash: String, pageIndex: Int, label: String? = null): UserBookmark {
        val bookmark = UserBookmark(fileHash = fileHash, pageIndex = pageIndex, label = label)
        Records.database.userBookmarkDao().upsert(bookmark)
        return bookmark
    }

    fun bookmarkCount(): Int = Records.database.userBookmarkDao().findAll().size

    fun recordCount(): Int = Records.rowCount()
}
