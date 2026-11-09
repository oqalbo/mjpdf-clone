// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ImportRoundTripTest : BackupTestCase() {
    @Test
    @TestId("BKP-16")
    @TestDuration(DurationCategory.LONG)
    fun fullRoundTripRestoresEveryRecordBookmarkAndSetting() {
        val tree = tree()
        val first = BackupSeed.record(
            hash = BackupHashes.ONE,
            fileName = "round-one.pdf",
            pageNumber = 17,
            reading = ReadingStatus.READING,
            favorite = true,
            lastOpened = LocalDateTime.of(2026, 2, 3, 4, 5, 6),
            cropMargins = "0.05,0.05,0.05,0.05",
            cropMarginsVersion = 2,
            documentTitle = "Round One",
            uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fround-one.pdf",
            sourceUri = "content://com.android.externalstorage.documents/document/primary%3ADocuments%2Fround-one.pdf",
        )
        val second = BackupSeed.record(
            hash = BackupHashes.TWO,
            fileName = "round-two.pdf",
            pageNumber = 2,
            reading = ReadingStatus.COMPLETED,
            favorite = false,
            documentTitle = "Round Two",
        )
        val bookmarks = listOf(
            BackupSeed.bookmark(BackupHashes.ONE, 4, label = "Alpha", sortOrder = 0),
            BackupSeed.bookmark(BackupHashes.ONE, 9, label = "Beta", sortOrder = 1),
            BackupSeed.bookmark(BackupHashes.TWO, 1, label = null, sortOrder = 0),
            BackupSeed.bookmark(BackupHashes.TWO, 6, label = "Delta", sortOrder = 1),
        )
        BackupSeed.settings(ROUND_TRIP_SETTINGS)

        launchBackupPage().use {
            grantFolder(tree)
            val written = exportWithDefaults(tree)
            BackupDialog.awaitTitle(BackupStrings.exportTitle, Await.LONG_TIMEOUT_MS)
            BackupDialog.dismissWithOk()

            BackupSeed.clearHistory()
            BackupSeed.removeSettings(*ROUND_TRIP_SETTINGS.keys.toTypedArray())
            assertEquals(0, BackupSeed.recordCount())
            assertEquals(0, BackupSeed.bookmarkCount())
            ROUND_TRIP_SETTINGS.keys.forEach { key ->
                assertFalse("Preference $key survived the clear", BackupSeed.contains(key))
            }

            val outcome = ImportSignals.awaitImport {
                BackupPage.startImport()
                BackupFixtures.openInTree(tree, written.name)
                confirmWipeWarning()
            }
            assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)

            assertEquals(
                listOf(BackupHashes.ONE, BackupHashes.TWO).sorted(),
                BackupSeed.recordHashes(),
            )
            listOf(first, second).forEach { expected ->
                val restored = BackupSeed.recordOf(expected.hash)
                assertNotNull("The record ${expected.hash} did not come back", restored)
                assertEquals(expected.pageNumber, restored!!.pageNumber)
                assertEquals(expected.reading, restored.reading)
                assertEquals(expected.favorite, restored.favorite)
                assertEquals(expected.fileName, restored.fileName)
                assertEquals(expected.uri.toString(), restored.uri.toString())
                assertEquals(expected.sourceUri, restored.sourceUri)
                assertEquals(expected.documentTitle, restored.documentTitle)
                assertEquals(expected.cropMargins, restored.cropMargins)
                assertEquals(expected.cropMarginsVersion, restored.cropMarginsVersion)
            }

            val restoredBookmarks = BackupSeed.allBookmarks()
            assertEquals(4, restoredBookmarks.size)
            bookmarks.forEach { expected ->
                val restored = restoredBookmarks.singleOrNull {
                    it.fileHash == expected.fileHash && it.pageIndex == expected.pageIndex
                }
                assertNotNull(
                    "The bookmark ${expected.fileHash} page ${expected.pageIndex} did not come back",
                    restored,
                )
                assertEquals(expected.label, restored!!.label)
                assertEquals(expected.sortOrder, restored.sortOrder)
            }

            ROUND_TRIP_SETTINGS.forEach { (key, value) ->
                assertEquals("Preference $key did not come back with its value", value, BackupSeed.raw(key))
            }

            val pending = BackupSeed.importResultPending()
            assertNotNull("importResultPending was not written by the import", pending)
            assertTrue(
                "The pending summary does not report a finished import, it read \"$pending\"",
                pending!!.contains(BackupStrings.importFinished),
            )
        }
    }

    @Test
    @TestId("BKP-17")
    fun importOntoAPopulatedDatabaseReplacesRatherThanMerges() {
        val stale = listOf(BackupHashes.ONE, BackupHashes.TWO, BackupHashes.THREE)
        stale.forEach { hash -> BackupSeed.record(hash = hash) }
        BackupSeed.bookmark(BackupHashes.ONE, 1)
        BackupSeed.bookmark(BackupHashes.ONE, 2)
        BackupSeed.bookmark(BackupHashes.TWO, 3)
        BackupSeed.bookmark(BackupHashes.THREE, 4)
        BackupSeed.saveDestination("file:///sdcard/Download/one.pdf")
        BackupSeed.saveDestination("file:///sdcard/Download/two.pdf")
        assertEquals(2, BackupSeed.saveDestinationCount())

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)

            assertEquals(
                "The imported hashes did not replace the seeded ones",
                BackupFixtures.VALID_FULL_HASHES.sorted(),
                BackupSeed.recordHashes(),
            )
            stale.forEach { hash ->
                assertFalse(
                    "The pre-existing record $hash survived a replace",
                    BackupSeed.recordHashes().contains(hash),
                )
            }
            assertEquals(
                BackupFixtures.VALID_FULL_BOOKMARK_COUNT,
                BackupSeed.bookmarkCount(),
            )
            assertEquals(
                "replaceAllHistory must clear the save destination table",
                0,
                BackupSeed.saveDestinationCount(),
            )
        }
    }

    @Test
    @TestId("BKP-18")
    fun importDeletesJournalsSignatureAndCovers() {
        BackupSeed.record(hash = BackupHashes.ONE, fileName = "journal-one.pdf")
        BackupSeed.record(hash = BackupHashes.TWO, fileName = "journal-two.pdf")
        BackupSeed.journal("file:///sdcard/Download/journal-one.pdf")
        BackupSeed.journal("file:///sdcard/Download/journal-two.pdf")
        BackupSeed.signature()
        BackupSeed.cover(BackupHashes.ONE)
        BackupSeed.cover(BackupHashes.TWO)

        assertEquals(2, BackupSeed.journalFiles().size)
        assertTrue(BackupSeed.signatureFile().isFile)
        assertEquals(2, BackupSeed.coverFiles().size)

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)

            assertEquals(
                "The annotation journals were not deleted by the import",
                emptyList<String>(),
                BackupSeed.journalFiles().map { it.name },
            )
            assertFalse(
                "The stored signature survived the import",
                BackupSeed.signatureFile().exists(),
            )
            assertEquals(
                "The cover cache was not cleared by the import",
                emptyList<String>(),
                BackupSeed.coverFiles().map { it.name },
            )
            assertEquals(BackupFixtures.VALID_FULL_HASHES.sorted(), BackupSeed.recordHashes())
        }
    }

    @Test
    @TestId("BKP-19")
    fun settingsOnlyImportShowsNoWipeWarningAndKeepsHistory() {
        val kept = listOf(BackupHashes.ONE, BackupHashes.TWO, BackupHashes.THREE)
        kept.forEach { hash -> BackupSeed.record(hash = hash) }
        BackupSeed.bookmark(BackupHashes.ONE, 1)
        BackupSeed.bookmark(BackupHashes.ONE, 2)
        BackupSeed.bookmark(BackupHashes.TWO, 3)
        BackupSeed.bookmark(BackupHashes.TWO, 4)
        BackupSeed.bookmark(BackupHashes.THREE, 5)
        BackupTree.assertNoSafetyDirectory()

        launchBackupPage().use {
            val outcome = importSettingsFixture(BackupFixtures.VALID_SETTINGS_ONLY)
            assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)
            BackupDialog.assertNoDialogMessageContaining(wipeWarningPrefix())

            assertEquals(kept.sorted(), BackupSeed.recordHashes())
            assertEquals(5, BackupSeed.bookmarkCount())

            val json = BackupFixtures.json(BackupFixtures.VALID_SETTINGS_ONLY)
            json.settingKeys().forEach { key ->
                assertTrue(
                    "The settings only import did not write $key",
                    BackupSeed.contains(key),
                )
            }

            val pending = BackupSeed.importResultPending()
            assertNotNull("importResultPending was not written by the import", pending)
            assertEquals(
                "A settings-only summary must contain exactly its finished and settings logical lines",
                listOf(
                    BackupStrings.importFinished,
                    BackupStrings.importedSettings(BackupFixtures.VALID_FULL_SETTINGS_COUNT),
                ),
                pending!!.lines(),
            )

            assertEquals(1, BackupTree.awaitSafetySnapshotCount(1).size)
        }
    }

    @Test
    @TestId("BKP-61")
    @TestDuration(DurationCategory.LONG)
    fun preferencesWithNoSettingsScreenRowSurviveARoundTrip() {
        val tree = tree()
        BackupSeed.record(hash = BackupHashes.ONE)
        BackupSeed.settings(
            mapOf(
                Preferences.secondBarEnabledKey to true,
                Preferences.hideButtonsLabelsKey to true,
            )
        )

        launchBackupPage().use {
            grantFolder(tree)
            val written = exportWithDefaults(tree)
            BackupDialog.awaitTitle(BackupStrings.exportTitle, Await.LONG_TIMEOUT_MS)
            BackupDialog.dismissWithOk()

            val exported = BackupJson.of(written)
            READER_ONLY_KEYS.forEach { key ->
                val setting = exported.setting(key)
                assertNotNull(
                    "The export dropped $key, which no settings screen row can write and no other " +
                        "test names",
                    setting,
                )
                assertEquals(
                    "The exported kind of $key does not match its declared kind",
                    Preferences.kindBoolean,
                    setting!!.getString("type"),
                )
                assertEquals("true", setting.getString("value"))
            }

            BackupSeed.removeSettings(*READER_ONLY_KEYS.toTypedArray())
            READER_ONLY_KEYS.forEach { key ->
                assertFalse("Preference $key survived the clear", BackupSeed.contains(key))
            }

            val outcome = ImportSignals.awaitImport {
                BackupPage.startImport()
                BackupFixtures.openInTree(tree, written.name)
                confirmWipeWarning()
            }
            assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)

            READER_ONLY_KEYS.forEach { key ->
                assertEquals(
                    "Preference $key did not come back as a stored boolean true",
                    true,
                    BackupSeed.boolean(key),
                )
            }
            val preferences = Preferences(BackupSeed.preferences)
            assertTrue(
                "getSecondBarEnabled fell back to its default after the restore",
                preferences.getSecondBarEnabled(),
            )
            assertTrue(
                "getHideButtonsLabels fell back to its default after the restore",
                preferences.getHideButtonsLabels(),
            )
        }
    }

    private fun wipeWarningPrefix(): String = BackupStrings.wipeWarning.take(WIPE_WARNING_PREFIX)

    private companion object {
        val READER_ONLY_KEYS = listOf(
            Preferences.secondBarEnabledKey,
            Preferences.hideButtonsLabelsKey,
        )

        val ROUND_TRIP_SETTINGS = mapOf<String, Any>(
            Preferences.highQualityKey to true,
            Preferences.screenOnKey to true,
            Preferences.horizontalScrollKey to true,
            Preferences.hideDelayKey to 5000,
            Preferences.scrollSpeedKey to 7,
            Preferences.maxZoomKey to 25.0f,
            Preferences.homeViewModeKey to "LIST",
            Preferences.shortcutBarActionsKey to setOf("search", "go_to_page", "bookmark_page"),
        )
    }
}
