// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.data.BackupManager
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.OffsetDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ExportTest : BackupTestCase() {
    @Test
    @TestId("BKP-01")
    fun exportToAFolderPickedThroughTheRealPicker() {
        val tree = tree()
        BackupSeed.record(hash = BackupHashes.ONE, pageNumber = 3)
        BackupSeed.record(hash = BackupHashes.TWO, pageNumber = 8)
        BackupSeed.bookmark(BackupHashes.ONE, 4)
        BackupSeed.bookmark(BackupHashes.ONE, 12)
        BackupSeed.bookmark(BackupHashes.TWO, 7)

        launchBackupPage().use {
            BackupPage.startExport()
            ExportOptions.export()
            BackupTree.pick(tree)

            val treeUri = BackupSeed.string(Preferences.backupFolderTreeUriKey)
            assertNotNull("backupFolderTreeUri was not written by the picker", treeUri)
            assertTrue(
                "The stored tree uri $treeUri does not end in this test's own directory ${tree.name}",
                treeUri!!.endsWith(tree.name),
            )
            val granted = TestEnvironment.targetContext.contentResolver.persistedUriPermissions
                .singleOrNull { it.uri.toString() == treeUri }
            assertNotNull("No persisted uri permission was taken on $treeUri", granted)
            assertTrue("The persisted grant on $treeUri carries no read permission", granted!!.isReadPermission)
            assertTrue("The persisted grant on $treeUri carries no write permission", granted.isWritePermission)

            val written = BackupTree.awaitManualBackup(tree)
            assertTrue(
                "The export did not create an MJ PDF directory inside ${tree.absolutePath}",
                BackupTree.folderExists(tree),
            )
            assertEquals(
                "The MJ PDF folder should hold exactly the one file this export wrote",
                listOf(written.name),
                BackupTree.fileNames(tree),
            )
            BackupTree.awaitNoTemporaryFiles(tree)

            val json = BackupJson.of(written)
            assertEquals(BackupManager.SCHEMA_VERSION, json.schemaVersion)
            assertEquals(BuildConfig.VERSION_CODE.toLong(), json.appVersionCode)
            OffsetDateTime.parse(json.exportedAt)
            assertEquals(2, json.records!!.size)
            assertEquals(3, json.bookmarks!!.size)

            val message = awaitExportMessage()
            assertTrue(
                "The result dialog does not name the written file $written, it read \"$message\"",
                message.contains(written.name),
            )
            assertTrue(
                "The result dialog does not report the settings count, it read \"$message\"",
                message.contains(BackupStrings.exportedSettings(json.settings!!.size)),
            )
            assertTrue(
                "The result dialog does not report 2 files and 3 bookmarks, it read \"$message\"",
                message.contains(BackupStrings.exportedHistory(2, 3)),
            )
        }
    }

    @Test
    @TestId("BKP-02")
    fun exportedRecordFieldsMatchTheDatabaseRow() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            val seeded = BackupSeed.record(
                hash = BackupHashes.ONE,
                fileName = "normal.pdf",
                pageNumber = 41,
                length = 123456,
                lastOpened = LocalDateTime.of(2026, 3, 14, 15, 9, 26),
                reading = ReadingStatus.READING,
                favorite = true,
                cropMargins = "0.10,0.10,0.10,0.10",
                cropMarginsVersion = 3,
                autoScrollSpeed = 7,
                readingDirectionOverride = "RTL",
                detectedReadingDirection = "LTR",
                documentTitle = "Doc Title",
                hidden = true,
                uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fnormal.pdf",
                sourceUri = "content://com.android.externalstorage.documents/document/primary%3ADocuments%2Fsource.pdf",
            )

            val written = exportWithDefaults(tree)
            val record = BackupJson.of(written).records!!.single()

            assertEquals(seeded.hash, record.getString("hash"))
            assertEquals(seeded.pageNumber, record.getInt("pageNumber"))
            assertEquals(seeded.length, record.getInt("length"))
            assertEquals(seeded.fileName, record.getString("fileName"))
            assertEquals(seeded.lastOpened.toString(), record.getString("lastOpened"))
            assertEquals(seeded.reading.name, record.getString("reading"))
            assertEquals(seeded.favorite, record.getBoolean("favorite"))
            assertEquals(seeded.cropMargins, record.getString("cropMargins"))
            assertEquals(seeded.cropMarginsVersion, record.getInt("cropMarginsVersion"))
            assertEquals(seeded.autoScrollSpeed, record.getInt("autoScrollSpeed"))
            assertEquals(seeded.readingDirectionOverride, record.getString("readingDirectionOverride"))
            assertEquals(seeded.detectedReadingDirection, record.getString("detectedReadingDirection"))
            assertEquals(seeded.documentTitle, record.getString("documentTitle"))
            assertEquals(seeded.uri.toString(), record.getString("uri"))
            assertEquals(seeded.hidden, record.getBoolean("hidden"))
            assertEquals(seeded.sourceUri, record.getString("sourceUri"))
            assertFalse(
                "A record exported with the dialog defaults must carry no password member",
                record.has("password"),
            )
        }
    }

    @Test
    @TestId("BKP-03")
    fun settingsOnlyExportOmitsTheHistoryMembers() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupSeed.record(hash = BackupHashes.ONE)
            BackupSeed.record(hash = BackupHashes.TWO)
            BackupSeed.bookmark(BackupHashes.ONE, 1)
            BackupSeed.bookmark(BackupHashes.TWO, 2)
            BackupSeed.settings(NON_DEFAULT_SETTINGS)

            BackupPage.startExport()
            ExportOptions.toggle(BackupStrings.optionHistory, expected = false)
            ExportOptions.export()

            val json = BackupJson.of(BackupTree.awaitManualBackup(tree))
            assertTrue("A settings only export must carry a settings member", json.has("settings"))
            assertFalse("A settings only export must carry no pdfRecords member", json.has("pdfRecords"))
            assertFalse("A settings only export must carry no userBookmarks member", json.has("userBookmarks"))

            val message = awaitExportMessage()
            assertTrue(
                "The result dialog omits the settings line, it read \"$message\"",
                message.contains(BackupStrings.exportedSettings(json.settings!!.size)),
            )
            assertFalse(
                "The result dialog reports history for an export that carried none, it read \"$message\"",
                BackupStrings.exportedHistoryAnyCount.containsMatchIn(message),
            )
        }
    }

    @Test
    @TestId("BKP-04")
    fun historyOnlyExportOmitsTheSettingsMember() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupSeed.record(hash = BackupHashes.ONE)
            BackupSeed.record(hash = BackupHashes.TWO)
            BackupSeed.bookmark(BackupHashes.ONE, 1)
            BackupSeed.bookmark(BackupHashes.TWO, 2)
            BackupSeed.settings(NON_DEFAULT_SETTINGS)

            BackupPage.startExport()
            ExportOptions.toggle(BackupStrings.optionSettings, expected = false)
            ExportOptions.export()

            val json = BackupJson.of(BackupTree.awaitManualBackup(tree))
            assertTrue("A history only export must carry a pdfRecords member", json.has("pdfRecords"))
            assertTrue("A history only export must carry a userBookmarks member", json.has("userBookmarks"))
            assertFalse("A history only export must carry no settings member", json.has("settings"))

            val message = awaitExportMessage()
            assertTrue(
                "The result dialog omits the history line, it read \"$message\"",
                message.contains(BackupStrings.exportedHistory(2, 2)),
            )
            assertFalse(
                "The result dialog reports settings for an export that carried none, it read \"$message\"",
                BackupStrings.exportedSettingsAnyCount.containsMatchIn(message),
            )
        }
    }

    @Test
    @TestId("BKP-05")
    fun passwordsAreExcludedByDefaultAndIncludedWhenAsked() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupSeed.record(hash = BackupHashes.ONE, password = BackupFixtures.SAVED_PASSWORD)

            val withoutPasswords = exportWithDefaults(tree)
            awaitExportMessage()
            BackupDialog.dismissWithOk()
            BackupClock.awaitNextSecond()

            BackupPage.startExport()
            ExportOptions.toggle(BackupStrings.optionPasswords, expected = true)
            ExportOptions.export()
            val both = BackupTree.awaitManualBackupCount(tree, 2)
            awaitExportMessage()

            val withPasswords = both.single { it.name != withoutPasswords.name }
            val first = BackupJson.of(withoutPasswords)
            val second = BackupJson.of(withPasswords)

            assertFalse(
                "The default export wrote a password member",
                first.records!!.single().has("password"),
            )
            assertFalse(
                "The default export's raw bytes contain the saved password",
                first.containsText(BackupFixtures.SAVED_PASSWORD),
            )
            assertEquals(BackupFixtures.SAVED_PASSWORD, second.records!!.single().getString("password"))
        }
    }

    @Test
    @TestId("BKP-09")
    fun deviceLocalKeysAreExcludedFromTheExportedFile() {
        val tree = tree()
        launchBackupPage().use {
            grantFolder(tree)
            BackupSeed.settings(
                mapOf(
                    Preferences.firstInstallKey to false,
                    Preferences.showFeaturesDialogKey to false,
                    Preferences.showExitFullscreenTipKey to false,
                    Preferences.lastSeenVersionCodeKey to 310,
                    Preferences.autoBackupLastRunKey to 1_700_000_000_000L,
                    Preferences.autoBackupLastErrorKey to "boom",
                    Preferences.autoBackupErrorAcknowledgedRunKey to 1L,
                    Preferences.autoBackupEnabledAtKey to 2L,
                    Preferences.importResultPendingKey to "leftover",
                )
            )

            val json = BackupJson.of(exportWithDefaults(tree))
            val exportedKeys = json.settingKeys()

            EXCLUDED_KEYS.forEach { key ->
                assertFalse(
                    "The exported settings array carries the device local key $key",
                    exportedKeys.contains(key),
                )
            }
            assertFalse(
                "The exported bytes carry a storage tree uri",
                json.containsText("content://com.android.externalstorage.documents/tree"),
            )
            json.settings!!.forEach { setting ->
                val key = setting.getString("key")
                val declared = Preferences.backupSettingKinds[key]
                assertNotNull("The export wrote the unknown key $key", declared)
                assertEquals(
                    "The export declared the wrong kind for $key",
                    declared,
                    setting.getString("type"),
                )
            }
        }
    }

    @Test
    @TestId("BKP-10")
    @TestDuration(DurationCategory.LONG)
    fun secondExportReusesTheConfiguredFolderWithoutAPicker() {
        val tree = tree()
        BackupSeed.record(hash = BackupHashes.ONE)
        launchBackupPage().use {
            grantFolder(tree)
            exportWithDefaults(tree)
            awaitExportMessage()
            BackupDialog.dismissWithOk()
        }

        leaveBackupPage()
        BackupClock.awaitNextSecond()

        launchBackupPage().use {
            BackupPage.startExport()
            ExportOptions.export()
            Await.assertThroughout(
                condition = "the system document picker never appears for a configured folder",
                windowMs = Await.SHORT_TIMEOUT_MS,
            ) { !SafPickerRobot().isShowing() }

            val both = BackupTree.awaitManualBackupCount(tree, 2)
            assertEquals(
                "The two exports produced the same file name",
                2,
                both.map { it.name }.distinct().size,
            )
            awaitExportMessage()
            BackupDialog.dismissWithOk()
            BackupPage.awaitShown()
            BackupPage.awaitSummaryContaining(BackupStrings.backupFolderTitle, FOLDER_SET_SUMMARY_PREFIX)
            BackupPage.awaitSummaryContaining(BackupStrings.backupFolderTitle, tree.name)
        }
    }

    private fun awaitExportMessage(): String {
        BackupDialog.awaitTitle(BackupStrings.exportTitle, Await.LONG_TIMEOUT_MS)
        return Await.untilNotNull("the export result dialog carries a message", Await.LONG_TIMEOUT_MS) {
            BackupDialog.message()
        }
    }

    private companion object {
        val FOLDER_SET_SUMMARY_PREFIX: String =
            BackupStrings.of(com.gitlab.mudlej.MjPdfReader.R.string.backup_folder_summary_set)
                .substringBefore("%1\$s").trim()

        val EXCLUDED_KEYS = listOf(
            Preferences.backupFolderTreeUriKey,
            Preferences.firstInstallKey,
            Preferences.showFeaturesDialogKey,
            Preferences.showExitFullscreenTipKey,
            Preferences.lastSeenVersionCodeKey,
            Preferences.autoBackupLastRunKey,
            Preferences.autoBackupLastErrorKey,
            Preferences.autoBackupErrorAcknowledgedRunKey,
            Preferences.autoBackupEnabledAtKey,
            Preferences.importResultPendingKey,
        )

        val NON_DEFAULT_SETTINGS = mapOf<String, Any>(
            Preferences.highQualityKey to true,
            Preferences.horizontalScrollKey to true,
            Preferences.screenOnKey to true,
            Preferences.hideDelayKey to 5000,
            Preferences.maxZoomKey to 25.0f,
        )
    }
}
