// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.history.ReadingHistoryActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReadingHistoryRowsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @Test
    @TestId("HIST-06")
    fun readingHistoryRowsAreViewAndDeleteOnly() {
        HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)

        val list = ReadingHistoryScreen.open()
        list.awaitRowCount(1)

        ActivityWatcher().use { watcher ->
            list.tapRowTitle(HistoryFixtures.ALPHA_TITLE)

            Screens.assertTopIs(ReadingHistoryActivity::class.java)
            assertEquals(
                "the Reading History screen stays in front of the user",
                string(R.string.reading_history),
                Screens.titleOfResumed(),
            )
            watcher.assertNeverReached(MainActivity::class.java, Stage.CREATED)
        }
    }

    @Test
    @TestId("HIST-08")
    @TestDuration(DurationCategory.NORMAL)
    fun cancellingASingleDeleteKeepsTheEntry() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 5)
        Records.awaitStoredPage(hash, 4)

        val list = ReadingHistoryScreen.open()
        list.awaitRowCount(1)
        list.tapDeleteOn(HistoryFixtures.ALPHA_TITLE)

        ReadingHistoryScreen.confirmDialogTitleShown(string(R.string.delete_dialog_title))
        ReadingHistoryScreen.tapCancel()

        val rows = list.awaitRowCount(1)
        assertEquals("the entry survives a cancelled delete", HistoryFixtures.ALPHA_TITLE, rows.single().title)
        Records.assertStoredPageStays(hash, 4)
    }

    @Test
    @TestId("HIST-09")
    fun deletingAnEntryAlsoRemovesItsBookmarksJournalDestinationAndCover() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        val record = Records.awaitRowExists(hash)
        val sourceUri = record.uri

        HistoryRecords.seedBookmark(hash, pageIndex = 2, label = "page three")
        val journal = HistoryRecords.seedJournal(sourceUri, "highlight 0 mj-hist-09")
        HistoryRecords.seedSaveDestination(sourceUri, DESTINATION_URI, hash)
        val cover = HistoryRecords.seedCover(hash)

        assertTrue("the seeded journal must exist before the delete", journal.exists())
        assertTrue("the seeded cover must exist before the delete", cover.exists())

        val list = ReadingHistoryScreen.open()
        list.awaitRowCount(1)
        list.tapDeleteOn(HistoryFixtures.ALPHA_TITLE)
        ReadingHistoryScreen.tapDelete()

        Records.awaitRowDisappears(hash)
        assertTrue("its bookmarks are gone", Records.bookmarksOf(hash).isEmpty())
        HistoryRecords.awaitNoSaveDestinations()
        assertNull("no save destination survives for the source", HistoryRecords.saveDestinationForSource(sourceUri))
        assertNull("no save destination survives for the identity", HistoryRecords.saveDestinationForHash(hash))
        Await.untilFileAbsent(journal)
        HistoryRecords.awaitNoCoverFor(hash)
        assertTrue(
            "forgetting a document must never touch the file itself",
            HistoryFixtures.file(HistoryFixtures.ALPHA).isFile,
        )
    }

    @Test
    @TestId("HIST-19")
    fun anEntryWhoseFileWasDeletedIsStillListedAndStillDeletable() {
        val copy = Fixtures.copyDeviceFixture(HistoryFixtures.ALPHA_COPY, rules.testDirectory.directory())

        val hash = HistoryFlows.readAndLeaveUri(HistoryRecords.fileUri(copy))

        Fixtures.delete(copy)
        Await.untilFileAbsent(copy)

        val list = ReadingHistoryScreen.open()
        val rows = list.awaitRowCount(1)
        assertEquals("a missing file is still listed", HistoryFixtures.ALPHA_COPY_TITLE, rows.single().title)
        assertTrue(
            "a missing file still shows when it was last opened, read \"${rows.single().subtitle}\"",
            rows.single().subtitle.startsWith(lastOpenedPrefix()),
        )

        list.tapDeleteOn(HistoryFixtures.ALPHA_COPY_TITLE)
        ReadingHistoryScreen.tapDelete()

        Records.awaitRowDisappears(hash)
        list.awaitEmptyState()
    }

    @Test
    @TestId("HIST-20")
    fun hiddenDocumentsStillAppearInReadingHistory() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        HistoryRecords.setHidden(hash, true)

        assertTrue("the record must be hidden before the screen is opened", Records.awaitRowExists(hash).hidden)

        val rows = ReadingHistoryScreen.open().awaitRowCount(1)
        assertEquals(
            "hiding a document from the library must not hide what the app remembers",
            HistoryFixtures.ALPHA_TITLE,
            rows.single().title,
        )
    }

    @Test
    @TestId("HIST-24")
    fun readingHistorySurvivesRotationOfItsOwnScreen() {
        HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        HistoryFlows.readAndLeave(HistoryFixtures.BETA)

        val list = ReadingHistoryScreen.open()
        list.awaitRowCount(2)

        Rotation.toLandscape(ReadingHistoryActivity::class.java)

        val rows = list.awaitRowCount(2)
        assertEquals("the newest document stays at the top after the turn", HistoryFixtures.BETA_TITLE, rows[0].title)
        assertEquals("the older document stays below it", HistoryFixtures.ALPHA_TITLE, rows[1].title)
        list.assertEmptyStateHidden()
    }

    private fun lastOpenedPrefix(): String =
        TestEnvironment.targetContext.getString(R.string.home_last_opened, "").trimEnd()

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val DESTINATION_URI = "content://mjpdf.test.destination/hist-alpha-annotated.pdf"
    }
}
