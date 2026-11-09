// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "PdfAnnotationSaveDestination")
data class PdfAnnotationSaveDestination(
    @PrimaryKey val sourceKey: String,
    val destinationUri: String,
    val lastSavedHash: String?,
    val lastSavedAt: LocalDateTime,
)
