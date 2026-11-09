// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ClearDataTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @Test
    @TestId("HIST-10")
    @TestDuration(DurationCategory.SHORT)
    fun clearReadingHistoryWipesEveryRecordAfterConfirmation() {
        val alphaHash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        HistoryFlows.readAndLeave(HistoryFixtures.BETA)
        val alpha = Records.awaitRowExists(alphaHash)
        HistoryRecords.seedSaveDestination(alpha.uri, DESTINATION_URI, alphaHash)
        HistoryRecords.seedCover(alphaHash)
        HistoryRecords.awaitRowCount(2)

        ReadingHistoryScreen.openHistoryAndPrivacy()
        ReadingHistoryScreen.tapSettingsRow(string(R.string.clear_reading_history_title))

        ReadingHistoryScreen.confirmDialogTitleShown(string(R.string.clear_reading_history_title))
        ReadingHistoryScreen.confirmDialogMessageShown(string(R.string.clear_reading_history_message))
        ReadingHistoryScreen.tapDelete()

        Toasts.awaitText(clearedEntries(2))
        HistoryRecords.awaitEmpty()
        HistoryRecords.awaitNoSaveDestinations()
        HistoryRecords.awaitCoverDirectoryEmpty()

        ReadingHistoryScreen.open().awaitEmptyState()
        assertTrue("clearing history must not touch the files", HistoryFixtures.file(HistoryFixtures.ALPHA).isFile)
        assertTrue("clearing history must not touch the files", HistoryFixtures.file(HistoryFixtures.BETA).isFile)
    }

    @Test
    @TestId("HIST-11")
    fun cancellingClearReadingHistoryChangesNothing() {
        val hash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 5)
        Records.awaitStoredPage(hash, 4)

        ReadingHistoryScreen.openHistoryAndPrivacy()
        ReadingHistoryScreen.tapSettingsRow(string(R.string.clear_reading_history_title))
        ReadingHistoryScreen.confirmDialogTitleShown(string(R.string.clear_reading_history_title))
        ReadingHistoryScreen.tapCancel()

        assertFalse(
            "a cancelled clear must not report that anything was deleted",
            anyDeletionToastAppears(),
        )
        assertEquals("the record survives a cancelled clear", 1, Records.rowCount())
        Records.assertStoredPageStays(hash, 4)
    }

    @Test
    @TestId("HIST-22")
    fun clearSavedPasswordsKeepsTheRecords() {
        val protectedHash = openProtectedSavingItsPassword()
        val alphaHash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA, oneBasedPage = 3)

        val storedPassword = Await.untilValue(
            "the protected document's password reaches its record",
            read = { Records.byHash(protectedHash)?.password },
        ) { it != null }
        assertEquals("the saved password", HistoryFixtures.PROTECTED_PASSWORD, storedPassword)
        val protectedPage = Records.awaitRowExists(protectedHash).pageNumber
        Records.awaitStoredPage(alphaHash, 2)

        ReadingHistoryScreen.openHistoryAndPrivacy()
        ReadingHistoryScreen.tapSettingsRow(string(R.string.clear_saved_passwords_title))
        ReadingHistoryScreen.confirmDialogTitleShown(string(R.string.clear_saved_passwords_title))
        ReadingHistoryScreen.tapDelete()

        Toasts.awaitText(clearedEntries(1))
        Await.until("the stored password is gone") { Records.byHash(protectedHash)?.password == null }

        assertEquals("both records survive a password wipe", 2, Records.rowCount())
        assertNotNull("the protected document is still remembered", Records.byHash(protectedHash))
        assertEquals(
            "the protected document keeps its reading position",
            protectedPage,
            Records.byHash(protectedHash)?.pageNumber,
        )
        assertEquals(
            "the other document keeps its reading position",
            2,
            Records.byHash(alphaHash)?.pageNumber,
        )
        assertNull("the password itself is gone", Records.byHash(protectedHash)?.password)
    }

    @Test
    @TestId("HIST-23")
    @TestDuration(DurationCategory.SHORT)
    fun clearBookmarksKeepsTheRecords() {
        val alphaHash = HistoryFlows.readAndLeave(HistoryFixtures.ALPHA)
        val betaHash = HistoryFlows.readAndLeave(HistoryFixtures.BETA)
        HistoryRecords.seedBookmark(alphaHash, pageIndex = 1, label = "alpha one")
        HistoryRecords.seedBookmark(alphaHash, pageIndex = 4, label = "alpha two")
        HistoryRecords.seedBookmark(betaHash, pageIndex = 2, label = "beta one")
        assertEquals("three bookmarks are seeded", 3, HistoryRecords.allBookmarks().size)

        ReadingHistoryScreen.openHistoryAndPrivacy()
        ReadingHistoryScreen.tapSettingsRow(string(R.string.clear_bookmarks_title))
        ReadingHistoryScreen.confirmDialogTitleShown(string(R.string.clear_bookmarks_title))
        ReadingHistoryScreen.tapDelete()

        Toasts.awaitText(clearedEntries(3))
        Await.until("every bookmark is gone") { HistoryRecords.allBookmarks().isEmpty() }

        assertEquals("both records survive a bookmark wipe", 2, Records.rowCount())
        val rows = ReadingHistoryScreen.open().awaitRowCount(2)
        assertEquals(
            "both documents are still listed",
            setOf(HistoryFixtures.ALPHA_TITLE, HistoryFixtures.BETA_TITLE),
            rows.map { it.title }.toSet(),
        )
    }

    private fun openProtectedSavingItsPassword(): String {
        val hist = HistoryReader()
        var hash = ""
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(HistoryFixtures.PROTECTED)).use {
            Await.untilNoThrow("the password prompt is shown", Await.LONG_TIMEOUT_MS) {
                onView(withId(R.id.passwordInput)).perform(
                    replaceText(HistoryFixtures.PROTECTED_PASSWORD),
                    closeSoftKeyboard(),
                )
            }
            onView(withId(R.id.savePassword)).perform(click())
            Dialogs.tapPositive()
            hist.awaitReady()
            hash = hist.reader.awaitFileHashResolved()
            Records.awaitRowExists(hash)
        }
        return hash
    }

    private fun anyDeletionToastAppears(): Boolean =
        TestEnvironment.uiDevice.wait(
            Until.hasObject(By.text(deletionToastPattern())),
            Toasts.TOAST_TIMEOUT_MS,
        ) == true

    private fun deletionToastPattern(): Pattern = Pattern.compile(
        deletionToastFragments().joinToString("|") { ".*" + Pattern.quote(it) + ".*" }
    )

    private fun deletionToastFragments(): List<String> =
        listOf(SINGULAR_COUNT, COUNT_PLACEHOLDER).map { count ->
            clearedEntries(count).substringAfter(count.toString()).trim()
        }

    private fun clearedEntries(count: Int): String =
        TestEnvironment.targetContext.resources.getQuantityString(R.plurals.cleared_entries, count, count)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val DESTINATION_URI = "content://mjpdf.test.destination/hist-alpha-annotated.pdf"
        const val COUNT_PLACEHOLDER = 0
        const val SINGULAR_COUNT = 1
    }
}
