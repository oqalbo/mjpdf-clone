// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeItemTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun progressDistinguishesUnopenedFromOpenedFirstPageAndClampsCompletedPages() {
        val unopened = homeRecordItem(files.newFile("book.pdf")).copy(length = 200)
        val opened = unopened.copy(lastOpened = LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(0, unopened.progressPercent)
        assertEquals(1, opened.progressPercent)
        assertEquals(100, opened.copy(pageNumber = 200).progressPercent)
        assertEquals(100, opened.copy(pageNumber = 201).progressPercent)
        assertEquals(0, opened.copy(length = 0).progressPercent)
        assertEquals(50, opened.copy(pageNumber = 99).progressPercent)
    }

    @Test fun annotatedTitleRequiresAnEnabledNonblankDocumentTitle() {
        val file = files.newFile("book-annotated.pdf").apply { writeBytes(ByteArray(23)) }
        val record = PdfRecord(
            "hash", 0, Uri.fromFile(file), 10, "book-annotated", null,
            LocalDateTime.parse(PdfRecord.UNSET_DATE), ReadingStatus.UNSET, false,
            documentTitle = "Document title",
        )
        fun title(source: PdfRecord, enabled: Boolean) = HomeItem.from(source, enabled, Availability.AVAILABLE, "%s (annotated)")
        assertEquals("Document title (annotated)", title(record, true).title)
        assertEquals("book-annotated", title(record, false).title)
        assertEquals("book-annotated", title(record.copy(documentTitle = " \t"), true).title)
        assertEquals("book-annotated", title(record.copy(documentTitle = null), true).title)
        assertEquals("Document title", title(record.copy(fileName = "book"), true).title)
        assertEquals(23L, title(record, true).sizeBytes)
    }

    @Test fun sortKeyLowercasesBeforeRemovingOnlyTheFinalPdfSuffix() {
        val file = files.newFile("book.pdf")
        assertEquals("mixed", homeRecordItem(file, "MiXeD.PDF").sortKey)
        assertEquals("mixed.pdf", homeRecordItem(file, "MiXeD.PDF.PDF").sortKey)
        assertEquals("mixed.txt", homeRecordItem(file, "MiXeD.TXT").sortKey)
    }
}
