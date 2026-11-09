// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import androidx.annotation.StringRes

class BackupException(@StringRes val messageRes: Int, vararg val formatArgs: Any) : Exception() {

    fun render(context: Context): String = context.getString(messageRes, *formatArgs)

    companion object {
        fun render(context: Context, error: Throwable): String {
            return if (error is BackupException) {
                error.render(context)
            } else {
                error.localizedMessage ?: error.javaClass.simpleName
            }
        }
    }
}

data class BackupData(
    val schemaVersion: Int = 0,
    val appVersionCode: Long? = null,
    val exportedAt: String? = null,
    val settings: List<BackupSetting>? = null,
    val pdfRecords: List<BackupPdfRecord>? = null,
    val userBookmarks: List<BackupUserBookmark>? = null,
) {
    val includesHistory: Boolean
        get() = pdfRecords != null || userBookmarks != null
}

data class BackupSetting(
    val key: String? = null,
    val type: String? = null,
    val value: String? = null,
    val values: List<String>? = null,
)

data class BackupPdfRecord(
    val hash: String? = null,
    val pageNumber: Int = 0,
    val length: Int = -1,
    val fileName: String? = null,
    val password: String? = null,
    val lastOpened: String? = null,
    val reading: String? = null,
    val favorite: Boolean = false,
    val cropMargins: String? = null,
    val cropMarginsVersion: Int = 0,
    val autoScrollSpeed: Int? = null,
    val readingDirectionOverride: String? = null,
    val detectedReadingDirection: String? = null,
    val documentTitle: String? = null,
    val uri: String? = null,
    val textModeJoinParagraphs: Boolean? = null,
    val textModeDetectHeadings: Boolean? = null,
    val textModeCodeBlocks: Boolean? = null,
    val hidden: Boolean = false,
    val sourceUri: String? = null,
)

data class BackupUserBookmark(
    val fileHash: String? = null,
    val pageIndex: Int = -1,
    val label: String? = null,
    val createdAt: String? = null,
    val sortOrder: Int = -1,
)

data class BackupExportOptions(
    val includeSettings: Boolean,
    val includeHistory: Boolean,
    val includePasswords: Boolean,
)

data class ExportSummary(
    val settingsCount: Int,
    val recordsCount: Int,
    val bookmarksCount: Int,
)

data class ImportSummary(
    val settingsCount: Int,
    val recordsCount: Int,
    val bookmarksCount: Int,
    val skippedSettingsCount: Int,
)
