// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ClearDataSettingsTest : SettingsTestCase() {

    @Test
    @TestId("SET-56")
    fun everyClearRowAsksFirstAndOnlyTheConfirmedOneDeletesAnything() {
        SettingsRoom.seedRecord("h2clear000000000000000000000001")
        SettingsRoom.seedRecord("h2clear000000000000000000000002")
        SettingsRoom.seedBookmark("h2clear000000000000000000000001", 3)
        SettingsRoom.seedBookmark("h2clear000000000000000000000002", 7)

        launchSettingsOn(SettingsPage.HISTORY_PRIVACY).use {
            listOf(
                R.string.clear_reading_history_title,
                R.string.clear_saved_passwords_title,
                R.string.clear_bookmarks_title,
                R.string.clear_annotation_journals_title,
            ).forEach { titleRes ->
                val title = SettingsStrings.of(titleRes)
                SettingsScreen.tapRow(title)
                SettingsDialog.awaitShown()
                assertEquals("the confirmation carries the row's own title", title, SettingsDialog.title())
                assertEquals(
                    "the confirmation offers a Delete button rather than a bare OK",
                    SettingsStrings.of(R.string.delete),
                    SettingsDialog.buttonLabel(SettingsDialog.POSITIVE),
                )
                SettingsDialog.cancel()
                SettingsDialog.awaitDismissed()
            }

            assertEquals("cancelling leaves every record in place", 2, SettingsRoom.recordCount())
            assertEquals("cancelling leaves every bookmark in place", 2, SettingsRoom.bookmarkCount())

            SettingsScreen.tapRow(SettingsStrings.of(R.string.clear_bookmarks_title))
            SettingsDialog.awaitShown()
            SettingsDialog.confirm()
            SettingsDialog.awaitDismissed()

            Await.untilValue(
                "the bookmark table is emptied",
                Await.DEFAULT_TIMEOUT_MS,
                read = { SettingsRoom.bookmarkCount() },
            ) { it == 0 }
            assertEquals("clearing bookmarks leaves the reading history alone", 2, SettingsRoom.recordCount())
            Toasts.awaitText(SettingsStrings.plural(R.plurals.cleared_entries, 2, 2))
        }
    }
}
