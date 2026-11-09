// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class IncognitoListVisibilityTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val home = PrivacyHome()

    @After
    fun leaveNoTaskBehind() {
        PrivacySettingsScreen.leave()
        reader.finishTask()
        home.picker.dismissIfShown()
    }

    @Test
    @TestId("PRIV-08")
    fun anIncognitoSessionLeavesNoTraceOnHomeOrTheReadingHistoryScreen() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        PrivacyFixtures.file(PrivacyFixtures.SECOND)
        home.listByFileName()

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            val hash = reader.awaitIdentity()
            PrivacyStores.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the normal open must record the document it opened",
                PrivacyFixtures.NORMAL_TITLE,
                PrivacyStores.record(hash)?.fileName,
            )
        }

        home.launch()
        home.openIncognitoFromDownloads(PrivacyFixtures.SECOND)
        reader.awaitReady()
        reader.awaitIdentity()
        reader.jumpToPageIndex(SECOND_DOCUMENT_PAGE_INDEX)
        PrivacyStores.holdRecordCount(1)
        reader.finishTask()

        home.reopen()
        val recent = home.recentTitles()
        assertTrue(
            "the Recent tab must still list the document read normally, it lists $recent",
            recent.contains(PrivacyFixtures.NORMAL_TITLE),
        )
        assertFalse(
            "the Recent tab must not list the incognito document, it lists $recent",
            recent.contains(PrivacyFixtures.SECOND_TITLE),
        )

        PrivacySettingsScreen.openReadingHistory()
        val historyTitles = PrivacySettingsScreen.readingHistoryTitles(expectedCount = 1)
        assertEquals(
            "the reading history screen must hold exactly the document read normally",
            listOf(PrivacyFixtures.NORMAL_TITLE),
            historyTitles,
        )

        val rows = PrivacyStores.records()
        assertEquals("exactly one document may be remembered", 1, rows.size)
        assertEquals(
            "the remembered document must be the one read normally",
            PrivacyFixtures.NORMAL_TITLE,
            rows.single().fileName,
        )
    }

    private companion object {
        const val SECOND_DOCUMENT_PAGE_INDEX = 1
    }
}
