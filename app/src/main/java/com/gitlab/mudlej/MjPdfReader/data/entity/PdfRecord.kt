// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.entity

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gitlab.mudlej.MjPdfReader.core.io.getFileName
import java.io.File
import java.time.LocalDateTime

@Entity(tableName = "PdfRecord")
data class PdfRecord(
    @PrimaryKey
    val hash: String,

    val pageNumber: Int,

    @ColumnInfo(defaultValue = UNSET_VALUE)
    var uri: Uri,

    @ColumnInfo(defaultValue = UNSET_LENGTH)
    val length: Int,

    @ColumnInfo(defaultValue = UNSET_VALUE)
    val fileName: String,

    val password: String?,

    @ColumnInfo(defaultValue = UNSET_DATE)
    var lastOpened: LocalDateTime,

    @ColumnInfo(defaultValue = UNSET_READING_STATUS)
    var reading: ReadingStatus,

    @ColumnInfo(defaultValue = UNSET_FAVORITE)
    var favorite: Boolean,

    @ColumnInfo(defaultValue = UNSET_CROP_MARGINS)
    val cropMargins: String? = null,

    @ColumnInfo(defaultValue = UNSET_CROP_MARGINS_VERSION)
    val cropMarginsVersion: Int = 0,

    @ColumnInfo(defaultValue = UNSET_AUTO_SCROLL_SPEED)
    val autoScrollSpeed: Int? = null,

    @ColumnInfo(defaultValue = UNSET_READING_DIRECTION)
    val readingDirectionOverride: String? = null,

    @ColumnInfo(defaultValue = UNSET_READING_DIRECTION)
    val detectedReadingDirection: String? = null,

    @ColumnInfo(defaultValue = UNSET_DOCUMENT_TITLE)
    val documentTitle: String? = null,

    @ColumnInfo(defaultValue = UNSET_HIDDEN)
    var hidden: Boolean = false,

    @ColumnInfo(defaultValue = UNSET_TEXT_MODE_REFLOW)
    val textModeJoinParagraphs: Boolean? = null,

    @ColumnInfo(defaultValue = UNSET_TEXT_MODE_REFLOW)
    val textModeDetectHeadings: Boolean? = null,

    @ColumnInfo(defaultValue = UNSET_TEXT_MODE_REFLOW)
    val textModeCodeBlocks: Boolean? = null,

    @ColumnInfo(defaultValue = UNSET_SOURCE_URI)
    val sourceUri: String? = null,
) {

    companion object {

        fun from(context: Context, entry: Map.Entry<String, File>): PdfRecord {
            return PdfRecord(
                entry.key,
                0,
                entry.value.toUri(),
                0,
                getFileName(context, entry.value.toUri()),
                null,
                LocalDateTime.now(),
                ReadingStatus.UNSET,
                false
            )
        }


        const val UNSET_NAME = "Unknown Name"
        const val UNSET_DATE = "-999999999-01-01T00:00" // LocalDateTime.MIN
        const val UNSET_VALUE = ""
        const val UNSET_LENGTH = "-1"
        const val UNSET_PAGE_NUMBER = "0"
        const val UNSET_READING_STATUS = "UNSET"
        const val UNSET_FAVORITE = false.toString()
        const val UNSET_CROP_MARGINS = "NULL"
        const val UNSET_CROP_MARGINS_VERSION = "0"
        const val UNSET_AUTO_SCROLL_SPEED = "NULL"
        const val UNSET_READING_DIRECTION = "NULL"
        const val UNSET_DOCUMENT_TITLE = "NULL"
        const val UNSET_HIDDEN = "false"
        const val UNSET_TEXT_MODE_REFLOW = "NULL"
        const val UNSET_SOURCE_URI = "NULL"
    }
}
