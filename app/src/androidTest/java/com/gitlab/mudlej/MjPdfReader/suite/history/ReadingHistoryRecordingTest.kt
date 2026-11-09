// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReadingHistoryRecordingTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @Test
    @TestId("HIST-01")
    fun openingADocumentRecordsItInReadingHistory() {
        HistoryRecords.awaitEmpty()

        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)

        val record = Records.awaitRowExists(hash)
        assertEquals("exactly one document is remembered", 1, Records.rowCount())
        assertEquals("the remembered file name", HistoryFixtures.ALPHA_TITLE, record.fileName)
        assertNotEquals(
            "an opened document must carry a real last opened date",
            HistoryRecords.unsetDate,
            record.lastOpened,
        )

        val list = ReadingHistoryScreen.open()
        val rows = list.awaitRowCount(1)
        assertEquals("the single reading history row title", HistoryFixtures.ALPHA_TITLE, rows.single().title)
        list.assertEmptyStateHidden()
    }

    @Test
    @TestId("HIST-02")
    fun readingHistoryIsOrderedByLastOpenedNewestFirst() {
        val alphaHash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        val betaHash = HistoryFlows.readAndLeave(HistoryFixtures.BETA)

        val alpha = Records.awaitRowExists(alphaHash)
        val beta = Records.awaitRowExists(betaHash)
        assertTrue(
            "the document opened second must carry the later timestamp, read ${beta.lastOpened} and ${alpha.lastOpened}",
            beta.lastOpened.isAfter(alpha.lastOpened),
        )

        val rows = ReadingHistoryScreen.open().awaitRowCount(2)
        assertEquals("the most recently opened document is at the top", HistoryFixtures.BETA_TITLE, rows[0].title)
        assertEquals("the earlier document is below it", HistoryFixtures.ALPHA_TITLE, rows[1].title)
    }

    @Test
    @TestId("HIST-03")
    fun aNeverOpenedRecordSortsLastAndReadsNeverOpened() {
        val beta = HistoryFixtures.file(HistoryFixtures.BETA)
        HistoryRecords.seedRecord(
            hash = HistoryRecords.identityOf(beta),
            uri = HistoryRecords.fileUri(beta),
            fileName = HistoryFixtures.BETA_TITLE,
            length = 0,
            lastOpened = HistoryRecords.unsetDate,
        )
        HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)

        val rows = ReadingHistoryScreen.open().awaitRowCount(2)
        assertEquals("the opened document sorts above the never opened one", HistoryFixtures.ALPHA_TITLE, rows[0].title)
        assertEquals("the never opened document sorts last", HistoryFixtures.BETA_TITLE, rows[1].title)
        assertEquals(
            "a never opened record says so instead of showing a date",
            string(R.string.reading_history_never_opened),
            rows[1].subtitle,
        )
        assertTrue(
            "an opened record names its last opened date, read \"${rows[0].subtitle}\"",
            rows[0].subtitle.startsWith(lastOpenedPrefix()),
        )
    }

    @Test
    @TestId("HIST-04")
    fun theRowSubtitleShowsTheLastOpenedDateAndTheSavedPage() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 7)

        val record = Records.awaitStoredPage(hash, 6)
        assertEquals("the stored page count", HistoryFixtures.ALPHA_PAGES, record.length)

        val row = ReadingHistoryScreen.open().awaitRowCount(1).single()
        assertTrue(
            "the subtitle must name the last opened date first, read \"${row.subtitle}\"",
            row.subtitle.startsWith(lastOpenedPrefix()),
        )
        assertTrue(
            "the subtitle must name the saved page, read \"${row.subtitle}\"",
            row.subtitle.contains(pageOfPages(7, HistoryFixtures.ALPHA_PAGES)),
        )
    }

    @Test
    @TestId("HIST-05")
    fun anEmptyReadingHistoryShowsTheEmptyState() {
        HistoryRecords.awaitEmpty()

        val list = ReadingHistoryScreen.open()
        list.awaitEmptyState()
        assertEquals("an empty reading history renders no rows", 0, list.childCount())
    }

    private fun lastOpenedPrefix(): String = string(R.string.home_last_opened, "").trimEnd()

    private fun pageOfPages(oneBasedPage: Int, pageCount: Int): String =
        string(R.string.page_x_of_y, oneBasedPage, pageCount)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun string(resId: Int, vararg args: Any): String =
        TestEnvironment.targetContext.getString(resId, *args)
}
