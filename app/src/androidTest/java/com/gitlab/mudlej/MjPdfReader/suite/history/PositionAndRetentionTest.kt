// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PositionAndRetentionTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()
    private val home = HomeRobot()

    @Test
    @TestId("HIST-18")
    fun reopeningARememberedDocumentResumesAtTheSavedPage() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 12)
        val before = Records.awaitStoredPage(hash, 11)

        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.reader.awaitPageIndex(11)
        }

        val after = Records.awaitRowExists(hash)
        assertEquals("the saved position is unchanged by the resume", 11, after.pageNumber)
        assertTrue(
            "reopening must bump the last opened date, read ${after.lastOpened} against ${before.lastOpened}",
            after.lastOpened.isAfter(before.lastOpened),
        )
    }

    @Test
    @TestId("HIST-21")
    fun nothingIsPrunedByAge() {
        val alphaHash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        val beta = HistoryFixtures.file(HistoryFixtures.BETA)
        val betaHash = HistoryRecords.identityOf(beta)
        HistoryRecords.seedRecord(
            hash = betaHash,
            uri = HistoryRecords.fileUri(beta),
            fileName = HistoryFixtures.BETA_TITLE,
            pageNumber = 5,
            length = HistoryFixtures.BETA_PAGES,
            lastOpened = LocalDateTime.parse(ANCIENT_DATE),
        )
        HistoryRecords.awaitRowCount(2)

        home.launchFromLauncher()

        val stale = Records.awaitRowExists(betaHash)
        assertEquals("an old record keeps its date", LocalDateTime.parse(ANCIENT_DATE), stale.lastOpened)
        assertEquals("an old record keeps its saved position", 5, stale.pageNumber)
        assertEquals("both records survive a fresh start", 2, Records.rowCount())
        Records.awaitRowExists(alphaHash)

        val rows = ReadingHistoryScreen.open().awaitRowCount(2)
        assertEquals("the recently opened document sorts first", HistoryFixtures.ALPHA_TITLE, rows[0].title)
        assertEquals("the old document is still listed below it", HistoryFixtures.BETA_TITLE, rows[1].title)
    }

    private companion object {
        const val ANCIENT_DATE = "2001-01-01T00:00"
    }
}
