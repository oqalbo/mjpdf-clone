// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SettingsSearchTest : SettingsTestCase() {

    @Test
    @TestId("SET-07")
    fun aSettingIsFoundByAKeywordThatIsNotInItsTitle() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("diacritics")

            val title = SettingsStrings.of(R.string.search_ignore_accents_title)
            val row = SettingsScreen.awaitRow(title)
            assertTrue(
                "the result summary leads with the page breadcrumb, it reads ${row.summary}",
                row.summaryOrEmpty().startsWith(SettingsStrings.pageTitle(SettingsPage.TEXT) + ": "),
            )
            SettingsPage.entries.forEach { page ->
                assertFalse(
                    "the ${page.name} navigation row must give way to the search results",
                    SettingsScreen.hasRow(SettingsStrings.pageTitle(page)),
                )
            }
        }
    }

    @Test
    @TestId("SET-08")
    fun aSearchThatMatchesNothingExplainsItselfWithOneUnclickableRow() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("qzxvv")

            val empty = SettingsStrings.of(R.string.settings_search_no_results)
            SettingsScreen.awaitRow(empty)
            assertEquals(
                "an unmatched search shows exactly the one explaining row",
                listOf(empty),
                SettingsScreen.rowTitles(),
            )
            assertFalse(
                "the no results row must not be selectable",
                SettingsScreen.row(empty).selectable,
            )
        }
    }

    @Test
    @TestId("SET-09")
    fun aSwitchToggledFromTheSearchResultsWritesItsKey() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("quality")

            val title = SettingsStrings.of(R.string.quality)
            SettingsScreen.awaitRow(title)
            SettingsScreen.awaitChecked(title, false)

            SettingsScreen.tapRow(title)

            SettingsScreen.awaitChecked(title, true)
            SettingsPrefs.awaitBoolean(Preferences.highQualityKey, true)
            SettingsPrefs.assertDeclaredKind(Preferences.highQualityKey)
        }
    }

    @Test
    @TestId("SET-10")
    fun backClearsAnActiveSearchBeforeItLeavesSettings() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("zoom")
            Await.until("the search narrowed the list away from the ten navigation rows") {
                !SettingsScreen.hasRow(SettingsStrings.pageTitle(SettingsPage.HOME))
            }

            SettingsScreen.back()

            SettingsScreen.awaitRootList()
            assertTrue(
                "the query is cleared and the field collapsed, so no query text survives",
                SettingsScreen.searchQueryOrNull().isNullOrEmpty(),
            )

            SettingsScreen.back()
            Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("SET-11")
    fun aTypedQuerySurvivesRotationTogetherWithItsResults() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("backup")
            SettingsScreen.awaitRow(SettingsStrings.of(R.string.backup_folder_title))
            val resultsBeforeRotation = SettingsScreen.rowTitles()

            Rotation.toLandscape(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)

            Await.until("the settings search field remains visibly expanded", Await.LONG_TIMEOUT_MS) {
                SettingsScreen.searchViewOrNull()?.isShown == true
            }
            Await.untilValue(
                "the search field is restored with the typed query",
                Await.LONG_TIMEOUT_MS,
                read = { SettingsScreen.searchQueryOrNull() },
            ) { it == "backup" }
            Await.untilValue(
                "rotation preserves the complete filtered result list",
                Await.LONG_TIMEOUT_MS,
                read = { SettingsScreen.rowTitles() },
            ) { it == resultsBeforeRotation }
            SettingsPage.entries.forEach { page ->
                assertFalse(
                    "the ${page.name} navigation row must not replace the restored search results",
                    SettingsScreen.hasRow(SettingsStrings.pageTitle(page)),
                )
            }
        }
    }

    @Test
    @TestId("SET-12")
    fun anInvisibleRowIsLeftOutOfTheSearchResultsUntilItsFileExists() {
        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("snapshot")

            val empty = SettingsStrings.of(R.string.settings_search_no_results)
            SettingsScreen.awaitRow(empty)
            assertEquals(
                "with no snapshot on disk the restore row is invisible and so is left out of the results",
                listOf(empty),
                SettingsScreen.rowTitles(),
            )
        }

        val snapshot = SettingsFixtures.seedSafetySnapshot()

        launchSettings().use {
            SettingsScreen.awaitRootList()
            SettingsScreen.search("snapshot")

            val title = SettingsStrings.of(R.string.backup_restore_snapshot_title)
            val row = SettingsScreen.awaitRow(title)
            assertTrue(
                "the restore row appears once a snapshot exists, it reads ${row.summary}",
                row.summaryOrEmpty().isNotBlank(),
            )
            assertTrue(
                "the summary names the snapshot the row would restore",
                row.summaryOrEmpty().contains(formattedTimestampOf(snapshot.lastModified())),
            )
        }
    }

    private fun formattedTimestampOf(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT))
}
