// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase_Impl
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.support.InMemorySharedPreferences
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeLibraryControllerSortTest {
    @get:Rule val files = TemporaryFolder()
    private val pref = Preferences(InMemorySharedPreferences())
    private val controller = HomeLibraryController(PdfRepository(AppDatabase_Impl()), pref, HistoryPolicy(pref))

    private fun item(name: String, title: String, day: Int): HomeItem =
        homeRecordItem(files.newFile(name), title).copy(lastOpened = LocalDateTime.of(2026, 1, day, 0, 0))

    @Test fun lastOpenedSortBreaksDateTiesByNameThenUri() {
        pref.setHomeSort(HomeSortOrder.LAST_OPENED)
        val later = item("later.pdf", "Zebra", 3)
        val alphaB = item("b.pdf", "Alpha", 2)
        val alphaA = item("a.pdf", "ALPHA", 2)
        val beta = item("beta.pdf", "Beta", 2)
        val older = item("older.pdf", "Aardvark", 1)
        assertEquals(listOf(later, alphaA, alphaB, beta, older), controller.sort(listOf(beta, older, alphaB, later, alphaA)))
    }

    @Test fun nameSortIgnoresCaseAndDateAndBreaksTiesByUri() {
        pref.setHomeSort(HomeSortOrder.NAME)
        val alphaB = item("b.pdf", "ALPHA.PDF", 3)
        val alphaA = item("a.pdf", "alpha.pdf", 1)
        val beta = item("beta.pdf", "Beta", 2)
        assertEquals(listOf(alphaA, alphaB, beta), controller.sort(listOf(beta, alphaB, alphaA)))
    }

    @Test fun continueReadingExcludesUnopenedItemsAndKeepsTheSixMostRecent() {
        val opened = (1..8).map { item("$it.pdf", "Book $it", it) }
        val unopened = homeRecordItem(files.newFile("unopened.pdf"), "Unopened")
        assertEquals(opened.reversed().take(6), controller.continueReading(listOf(unopened) + opened))
    }

    @Test fun recentsExcludesHashesAcrossUrisAndCapsAtTwelveOpenedItems() {
        val opened = (1..16).map { item("$it.pdf", "Book $it", it) }
        val excludedCopy = opened.last().copy(uri = Uri.fromFile(files.newFile("copy.pdf")))
        val unopened = homeRecordItem(files.newFile("unopened.pdf"), "Unopened")
        assertEquals(opened.dropLast(1).reversed().take(12), controller.recents(listOf(unopened) + opened, listOf(excludedCopy)))
    }
}

internal fun homeRecordItem(file: File, name: String = file.nameWithoutExtension): HomeItem = HomeItem.from(
    PdfRecord(
        hash = file.name, pageNumber = 0, uri = Uri.fromFile(file), length = 0,
        fileName = name, password = null, lastOpened = LocalDateTime.parse(PdfRecord.UNSET_DATE),
        reading = ReadingStatus.UNSET, favorite = false,
    ),
    showPdfTitle = false, availability = Availability.AVAILABLE, annotatedTitleFormat = "%s (annotated)",
)
