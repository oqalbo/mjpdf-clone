// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDateTime

@Entity(tableName = "UserBookmark", primaryKeys = ["fileHash", "pageIndex"], indices = [Index("fileHash")])
data class UserBookmark(
    val fileHash: String,
    val pageIndex: Int,
    val label: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(defaultValue = "-1") val sortOrder: Int = -1,
)
