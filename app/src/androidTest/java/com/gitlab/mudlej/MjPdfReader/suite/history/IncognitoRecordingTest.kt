// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoRecordingTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val hist = HistoryReader()

    @Test
    @TestId("HIST-12")
    fun anIncognitoOpenCreatesNoRecordAtAll() {
        HistoryRecords.awaitEmpty()

        ActivityScenario.launch<MainActivity>(HistoryReader.incognitoViewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.jumpToPage(3)
            hist.reader.awaitPageIndex(2)
            HistoryRecords.holdEmpty()
        }

        HistoryRecords.holdEmpty()
        ReadingHistoryScreen.open().awaitEmptyState()
    }

    @Test
    @TestId("HIST-13")
    fun incognitoDoesNotTouchAnExistingRecord() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 3)
        val before = Records.awaitStoredPage(hash, 2)
        val lastOpenedBefore = before.lastOpened

        ActivityScenario.launch<MainActivity>(HistoryReader.incognitoViewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.jumpToPage(15)
            hist.reader.awaitPageIndex(14)
        }

        Records.assertStoredPageStays(hash, 2)
        val after = Records.awaitRowExists(hash)
        assertEquals("an incognito session must not move the saved position", 2, after.pageNumber)
        assertEquals(
            "an incognito session must not move the last opened date",
            lastOpenedBefore,
            after.lastOpened,
        )
    }

    @Test
    @TestId("HIST-14")
    fun turningIncognitoOffMidSessionResumesRecording() {
        HistoryRecords.awaitEmpty()

        ActivityScenario.launch<MainActivity>(HistoryReader.incognitoViewIntent(HistoryFixtures.ALPHA)).use {
            hist.awaitReady()
            hist.jumpToPage(9)
            hist.reader.awaitPageIndex(8)
            HistoryRecords.holdEmpty()

            hist.toggleIncognito()

            Snackbars.awaitText(string(R.string.incognito_off_message))
            hist.awaitReady()
            hist.reader.awaitPageIndex(8)

            val record = HistoryRecords.awaitRecordNamed(HistoryFixtures.ALPHA_TITLE)
            assertNotEquals(
                "leaving incognito must write a real last opened date",
                HistoryRecords.unsetDate,
                record.lastOpened,
            )
            Records.awaitStoredPage(record.hash, 8)
            assertEquals("exactly one document is remembered", 1, Records.rowCount())
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
