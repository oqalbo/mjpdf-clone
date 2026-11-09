// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
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
class HistoryDisabledGateTest {

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
    @TestId("PRIV-25")
    fun historyDisabledAloneBlocksThePdfRecordWrite() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        home.launch()
        home.openNormallyFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIdentity()
        reader.jumpToPageIndex(READ_TO_INDEX)

        PrivacyStores.holdNoRecords()
        assertEquals("history off must block the record write", 0, PrivacyStores.recordCount())

        val grants = Await.untilValue(
            "the normal pick takes one persisted read grant even with history off",
            Await.LONG_TIMEOUT_MS,
            read = { PrivacyStores.persistedUris() },
        ) { it.size == 1 }
        assertEquals("exactly one persisted grant must be held for the picked document", 1, grants.size)

        reader.finishTask()
        home.reopen()
        assertTrue(
            "the Recent tab must show its empty state, it lists ${home.recentTitles()}",
            home.recentHasEmptyState(),
        )
    }

    @Test
    @TestId("PRIV-26")
    @TestDuration(DurationCategory.NORMAL)
    fun historyDisabledBlocksTheAnnotationJournal() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            val documentUri = reader.documentUri()

            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)

            PrivacyStores.drainJournalWriter(documentUri)
            assertEquals(
                "history off must protect annotation drafts on disk, not only the library",
                emptyList<String>(),
                PrivacyStores.journalFiles().map { it.name },
            )
            PrivacyStores.holdNoJournal()
            assertTrue(
                "the highlight must still exist in memory",
                reader.isDirtyFabVisible(),
            )
        }
    }

    @Test
    @TestId("PRIV-27")
    fun bothGatesOnTogetherRecordNothing() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        home.launch()
        home.home.awaitScanLoaded(Await.LONG_TIMEOUT_MS)
        val scannedBefore = PrivacyStores.scannedEntry(fixture.absolutePath)
        home.openIncognitoFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIncognitoIcon(visible = true)
        reader.awaitIdentity()
        val documentUri = reader.documentUri()

        reader.jumpToPageIndex(READ_TO_INDEX)
        reader.createHighlightOnCurrentPage()
        reader.awaitDirtyFab(visible = true)
        PrivacyStores.drainJournalWriter(documentUri)

        reader.finishTask()

        PrivacyStores.holdNoRecords()
        PrivacyStores.holdScannedEntryUnchanged(fixture.absolutePath, scannedBefore)
        assertEquals("no document may be recorded", 0, PrivacyStores.recordCount())
        assertEquals("no bookmark may be recorded", 0, PrivacyStores.bookmarkCount())
        assertEquals(
            "the incognito open must not add or alter the scanned-cache row for its own document",
            scannedBefore,
            PrivacyStores.scannedEntry(fixture.absolutePath),
        )
        assertEquals(
            "no annotation journal may be written",
            emptyList<String>(),
            PrivacyStores.journalFiles().map { it.name },
        )
        assertEquals(
            "no cover may be cached",
            emptyList<String>(),
            PrivacyStores.coverFiles().map { it.name },
        )
        assertEquals(
            "no page preview may be cached",
            emptyList<String>(),
            PrivacyStores.previewFiles().map { it.name },
        )
        assertEquals(
            "no persisted grant may be taken",
            emptyList<String>(),
            PrivacyStores.persistedUris(),
        )
    }

    @Test
    @TestId("PRIV-29")
    @TestDuration(DurationCategory.NORMAL)
    fun turningHistoryOffDoesNotDeleteExistingRecords() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)
        home.listByFileName()

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(READ_TO_INDEX)
            Records.awaitStoredPage(hash, READ_TO_INDEX)
        }

        val before = Records.awaitRowExists(hash)
        assertEquals("the normal session must record the page it reached", READ_TO_INDEX, before.pageNumber)

        home.launch()
        PrivacySettingsScreen.openHistoryAndPrivacy()
        PrivacySettingsScreen.tapRow(string(R.string.history_enabled_title))
        Await.untilPreference(Preferences.historyEnabledKey) { it == false }
        PrivacySettingsScreen.leave()

        home.reopen()

        val after = Records.awaitRowExists(hash)
        assertEquals("the switch must not erase what was already recorded", READ_TO_INDEX, after.pageNumber)
        assertEquals("the switch must not touch the last opened date", before.lastOpened, after.lastOpened)
        assertFalse(
            "the Recent tab must still show the existing row",
            home.recentHasEmptyState(),
        )
        assertTrue(
            "the Recent tab must still list the document, it lists ${home.recentTitles()}",
            home.recentTitles().contains(PrivacyFixtures.NORMAL_TITLE),
        )
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val READ_TO_INDEX = 2
    }
}
