// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class HistorySwitchTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @Test
    @TestId("HIST-15")
    fun theHistorySwitchOffSuppressesRecordCreationEntirely() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        HistoryRecords.awaitEmpty()

        val hash = HistoryRecords.identityOf(HistoryFixtures.file(HistoryFixtures.ALPHA))
        HistoryFlows.readWithHistoryOffAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 4)

        HistoryRecords.holdEmpty()
        HistoryRecords.holdNoCoverFor(hash)
        ReadingHistoryScreen.open().awaitEmptyState()
    }

    @Test
    @TestId("HIST-16")
    fun theHistorySwitchOffFreezesAnExistingRecord() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 3)
        val before = Records.awaitStoredPage(hash, 2)

        SettingsSeeder.seed(Preferences.historyEnabledKey, false)

        HistoryFlows.readWithHistoryOffAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 16)

        Records.assertStoredPageStays(hash, 2)
        HistoryRecords.assertLastOpenedStays(hash, before.lastOpened)

        val rows = ReadingHistoryScreen.open().awaitRowCount(1)
        assertEquals(
            "a frozen record is still listed rather than deleted",
            HistoryFixtures.ALPHA_TITLE,
            rows.single().title,
        )
    }

    @Test
    @TestId("HIST-17")
    fun turningTheHistorySwitchBackOnResumesRecording() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        HistoryRecords.awaitEmpty()

        ReadingHistoryScreen.openHistoryAndPrivacy()
        ReadingHistoryScreen.tapSettingsRow(string(R.string.history_enabled_title))

        Await.untilPreference(Preferences.historyEnabledKey) { it == true }
        ReadingHistoryScreen.leaveSettings()

        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 6)

        Records.awaitStoredPage(hash, 5)
        val rows = ReadingHistoryScreen.open().awaitRowCount(1)
        assertEquals("the document is listed again", HistoryFixtures.ALPHA_TITLE, rows.single().title)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
