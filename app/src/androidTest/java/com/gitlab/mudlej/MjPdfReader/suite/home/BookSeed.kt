// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import java.io.File
import java.time.LocalDateTime

val NEVER_OPENED: LocalDateTime = LocalDateTime.parse(PdfRecord.UNSET_DATE)

fun daysAgo(days: Long): LocalDateTime = LocalDateTime.now().minusDays(days)

data class BookSeed(
    val fixture: String,
    val name: String = fixture,
    val record: Boolean = true,
    val indexed: Boolean = true,
    val pageNumber: Int = 0,
    val pages: Int = 0,
    val lastOpened: LocalDateTime = NEVER_OPENED,
    val reading: ReadingStatus = ReadingStatus.UNSET,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val documentTitle: String? = null,
    val bookmarks: List<Int> = emptyList(),
) {
    val stem: String get() = name.removeSuffix(PDF_SUFFIX)

    companion object {
        const val PDF_SUFFIX = ".pdf"
    }
}

data class SeededBook(val seed: BookSeed, val file: File, val hash: String) {

    val fileName: String get() = file.nameWithoutExtension

    val path: String get() = file.absolutePath

    val uri: Uri get() = Uri.fromFile(file)

    val sizeBytes: Long get() = file.length()

    override fun toString(): String = "$fileName at $path as $hash"
}

class SeededLibrary(val directory: File, val books: List<SeededBook>) {

    val hashes: List<String> get() = books.map { it.hash }

    val paths: List<String> get() = books.map { it.path }

    operator fun get(name: String): SeededBook {
        val stem = name.removeSuffix(BookSeed.PDF_SUFFIX)
        return books.firstOrNull { it.fileName == stem }
            ?: books.firstOrNull { it.seed.fixture.removeSuffix(BookSeed.PDF_SUFFIX) == stem }
            ?: throw AssertionError(
                "No seeded book is named \"$name\". This library holds: " +
                    books.joinToString { it.fileName }
            )
    }

    fun hashOf(name: String): String = get(name).hash

    fun fileOf(name: String): File = get(name).file
}
