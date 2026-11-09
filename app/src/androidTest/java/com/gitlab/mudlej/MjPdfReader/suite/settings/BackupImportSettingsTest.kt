// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.backup.ImportSignals
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackupImportSettingsTest : SettingsTestCase() {

    private val picker = SafPickerRobot()

    @Test
    @TestId("SET-71")
    fun aSettingsOnlyBackupRestoresEverySettingItDeclares() {
        SettingsRoom.seedRecord("h2import00000000000000000000001")

        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            runImport(SettingsFixtures.SETTINGS_ONLY_BACKUP, expectWipeWarning = false)

            SettingsPrefs.assertBoolean(Preferences.highQualityKey, true)
            SettingsPrefs.assertInt(Preferences.hideDelayKey, 5000)
            SettingsPrefs.assertFloat(Preferences.maxZoomKey, 25.0f)
            SettingsPrefs.assertString(Preferences.homeViewModeKey, "LIST")
            SettingsPrefs.assertStringSet(
                Preferences.shortcutBarActionsKey,
                setOf("search", "go_to_page", "bookmark_page"),
            )

            assertEquals(
                "the pending result announces the import and counts the five applied settings, with no " +
                    "skipped line because nothing was skipped",
                SettingsStrings.of(R.string.backup_import_finished) + "\n" +
                    SettingsStrings.of(R.string.backup_import_done_settings, APPLIED_SETTINGS),
                SettingsPrefs.string(Preferences.importResultPendingKey),
            )

            assertEquals("a settings only import leaves the record table alone", 1, SettingsRoom.recordCount())
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("SET-72")
    fun anImportThatWouldWipeHistoryCountsDownAndCancelChangesNothing() {
        SettingsRoom.seedRecord("h2wipe0000000000000000000000001")
        SettingsRoom.seedRecord("h2wipe0000000000000000000000002")
        SettingsRoom.seedBookmark("h2wipe0000000000000000000000001", 4)
        val recordsBefore = Records.all().toSet()
        val bookmarksBefore = Records.database.userBookmarkDao().findAll().toSet()
        val importsBefore = BackupDebugHooks.importCompletions()
        assertEquals("the library must start with the two seeded records", 2, recordsBefore.size)
        assertEquals("the library must start with the one seeded bookmark", 1, bookmarksBefore.size)

        launchSettingsOn(SettingsPage.BACKUP).use {
            val fixture = SettingsFixtures.pickerBackupFixture(SettingsFixtures.FULL_BACKUP)
            SettingsScreen.tapRow(importTitle)
            val warningCount = BackupDebugHooks.wipeWarnings()
            BackupTree.openFixture(fixture, picker)
            Await.until("the wipe warning onShow hook runs") {
                BackupDebugHooks.wipeWarnings() > warningCount
            }
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            assertEquals(
                "the warning explains what an import with history does",
                SettingsStrings.of(R.string.backup_import_wipe_warning),
                SettingsDialog.message(),
            )
            assertEquals(
                "the disabled confirm button must begin at the three-second arm interval",
                "${SettingsStrings.of(R.string.backup_import_wipe_action)} (3)",
                BackupDebugHooks.wipeWarningInitialLabel(),
            )
            assertTrue(
                "the destructive import action must be disabled when the warning is shown",
                BackupDebugHooks.wipeWarningInitiallyDisabled(),
            )

            SettingsDialog.awaitButtonEnabled(SettingsDialog.POSITIVE, true, COUNTDOWN_TIMEOUT_MS)
            SettingsDialog.cancel()
            SettingsDialog.awaitDismissed()

            Await.assertThroughout("cancelled import preserves history and never executes", windowMs = 1_000L) {
                assertEquals("a cancelled import must leave the records alone", recordsBefore, Records.all().toSet())
                assertEquals(
                    "a cancelled import must leave the bookmarks alone",
                    bookmarksBefore,
                    Records.database.userBookmarkDao().findAll().toSet(),
                )
                assertEquals(
                    "a cancelled import must record no completion",
                    importsBefore,
                    BackupDebugHooks.importCompletions(),
                )
                assertFalse("a cancelled import must not be running", BackupDebugHooks.importRunning())
                true
            }

            assertEquals(
                "cancelling returns to the page the import was started from",
                SettingsStrings.pageTitle(SettingsPage.BACKUP),
                SettingsScreen.title(),
            )
            SettingsPrefs.assertUnset(
                Preferences.highQualityKey,
                Preferences.hideDelayKey,
                Preferences.maxZoomKey,
                Preferences.homeViewModeKey,
                Preferences.shortcutBarActionsKey,
            )
        }
    }

    @Test
    @TestId("SET-73")
    fun aKeyThisVersionDoesNotKnowIsSkippedRatherThanInjected() {
        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            runImport(SettingsFixtures.UNKNOWN_KEYS_BACKUP, expectWipeWarning = false)

            SettingsPrefs.assertBoolean(Preferences.highQualityKey, true)
            SettingsPrefs.assertUnset("enableRootMode", "pdfDarkThemeV2", "zz_unknown")
            assertSkippedCount(3)
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("SET-74")
    fun aWronglyTypedValueIsSkippedAndItsRowKeepsTheShippedDefault() = Fixtures.withCrowdedDownloads {
        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            runImport(SettingsFixtures.WRONG_TYPES_BACKUP, expectWipeWarning = false, preparePicker = { current ->
                val packageName = TestEnvironment.uiDevice.currentPackageName
                val list = TestEnvironment.uiDevice.findObject(
                    androidx.test.uiautomator.By.res(packageName, "dir_list"),
                ) ?: throw AssertionError("the picker must expose its directory list")
                list.scroll(androidx.test.uiautomator.Direction.UP, SafPickerRobot.SCROLL_STEPS.toFloat())
                current.waitForIdle()
                assertTrue("the crowded folder rows are visible before locating the JSON fixture",
                    current.visibleTexts().any { it.startsWith("000-picker-folder-") })
            })

            SettingsPrefs.assertUnset(
                Preferences.highQualityKey,
                Preferences.hideDelayKey,
                Preferences.maxZoomKey,
            )
            assertSkippedCount(3)
            ImportSignals.clearProgressDialog(scenario)
            TestEnvironment.uiDevice.pressBack()

            SettingsScreen.awaitRootList()
            SettingsScreen.openPage(SettingsPage.APPEARANCE)
            assertEquals(
                "High Quality falls back to its off default",
                false,
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.quality)).checked,
            )
            backToRoot()

            SettingsScreen.openPage(SettingsPage.CUSTOMIZE_CONTROLS)
            assertEquals(
                "Controls Hide Delay falls back to three seconds",
                SettingsStrings.hideDelayLabel(Preferences.hideDelayDefault),
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.hide_delay_title)).summary,
            )
            backToRoot()

            SettingsScreen.openPage(SettingsPage.ADVANCED)
            assertTrue(
                "Max Zoom falls back to ten",
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.max_zoom)).summaryOrEmpty()
                    .endsWith(SettingsStrings.currentValue(Preferences.maxZoomDefault.toInt())),
            )
        }
    }

    @Test
    @TestId("SET-75")
    fun aValueOutsideAnEnumDomainIsSkipped() {
        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            runImport(SettingsFixtures.BAD_ENUM_BACKUP, expectWipeWarning = false)

            SettingsPrefs.assertUnset(
                Preferences.homeViewModeKey,
                Preferences.pageFitPolicyKey,
                Preferences.textModeThemeKey,
            )
            SettingsPrefs.assertBoolean(Preferences.screenOnKey, true)
            assertSkippedCount(3)
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("SET-76")
    fun outOfRangeTextModeValuesAreStoredRawAndClampedWhenTextModeLoadsThem() = Fixtures.withCrowdedDownloads {
        var clockOffset = 0L
        val slowLookupPicker = SafPickerRobot { android.os.SystemClock.uptimeMillis() + clockOffset }
        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            runImport(
                SettingsFixtures.OUT_OF_RANGE_TEXT_MODE_BACKUP,
                expectWipeWarning = false,
                importPicker = slowLookupPicker,
                onSelectionDispatched = { clockOffset += Await.DEFAULT_TIMEOUT_MS },
            )

            SettingsPrefs.assertFloat(Preferences.textModeFontSizeKey, 999.0f)
            SettingsPrefs.assertFloat(Preferences.textModeLineSpacingKey, 0.1f)
            SettingsPrefs.assertInt(Preferences.textModeHorizontalMarginKey, 500)

            val loaded = TextModeSettings.load(SettingsPrefs.store)
            assertEquals("999 is coerced down to the maximum font size", 36f, loaded.fontSize, TOLERANCE)
            assertEquals("0.1 is coerced up to the minimum line spacing", 1.0f, loaded.lineSpacing, TOLERANCE)
            assertEquals("500 is coerced down to the maximum margin", 48, loaded.horizontalMargin)
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("SET-77")
    fun aCorruptBackupIsRefusedOnThePageItWasStartedFromAndDisturbsNothing() {
        SettingsRoom.seedRecord("h2corrupt0000000000000000000001")
        launchSettingsOn(SettingsPage.APPEARANCE).use {
            toggleSwitch(SettingsStrings.of(R.string.quality), true)
        }
        SettingsPrefs.awaitBoolean(Preferences.highQualityKey, true)

        launchSettingsOn(SettingsPage.BACKUP).use {
            val fixture = SettingsFixtures.pickerBackupFixture(SettingsFixtures.CORRUPT_BACKUP)
            SettingsScreen.tapRow(importTitle)
            BackupTree.openFixture(fixture, picker)
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsDialog.awaitShown(Await.LONG_TIMEOUT_MS)
            assertEquals(
                "the failure is reported under the import title",
                SettingsStrings.of(R.string.backup_import_title),
                SettingsDialog.title(),
            )
            val message = SettingsDialog.message().orEmpty()
            val failurePrefix = SettingsStrings.of(R.string.backup_import_failed, "").trimEnd()
            assertTrue(
                "the failure is rendered with a reason after it, it reads \"$message\"",
                message.startsWith(failurePrefix) && message.length > failurePrefix.length,
            )
            SettingsDialog.tap(SettingsDialog.POSITIVE)
            SettingsDialog.awaitDismissed()

            SettingsDialog.assertNoTextWithin(SettingsStrings.of(R.string.backup_import_wipe_action))
            SettingsDialog.assertNoTextWithin(SettingsStrings.of(R.string.backup_import_running))
            SettingsPrefs.assertBoolean(Preferences.highQualityKey, true)
            assertEquals("a refused import touches no record", 1, SettingsRoom.recordCount())
        }
    }

    @Test
    @TestId("SET-78")
    @TestDuration(DurationCategory.SHORT)
    fun aZeroByteBackupIsRefusedWithAReason() {
        launchSettingsOn(SettingsPage.BACKUP).use {
            val fixture = SettingsFixtures.pickerBackupFixture(SettingsFixtures.EMPTY_BACKUP)
            SettingsScreen.tapRow(importTitle)
            BackupTree.openFixture(fixture, picker)
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsDialog.awaitShown(Await.LONG_TIMEOUT_MS)
            assertTrue(
                "the failure carries a rendered reason",
                SettingsDialog.message().orEmpty().isNotBlank(),
            )
            SettingsDialog.tap(SettingsDialog.POSITIVE)
            SettingsDialog.awaitDismissed()

            SettingsDialog.assertNoTextWithin(SettingsStrings.of(R.string.backup_import_running))
            SettingsPrefs.assertUnset(Preferences.highQualityKey, Preferences.hideDelayKey)
        }
    }

    @Test
    @TestId("SET-79")
    @TestDuration(DurationCategory.SHORT)
    fun aBackupFromANewerSchemaIsRefusedByVersion() {
        launchSettingsOn(SettingsPage.BACKUP).use {
            val fixture = SettingsFixtures.pickerBackupFixture(SettingsFixtures.NEWER_SCHEMA_BACKUP)
            SettingsScreen.tapRow(importTitle)
            BackupTree.openFixture(fixture, picker)
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)

            SettingsDialog.awaitShown(Await.LONG_TIMEOUT_MS)
            val message = SettingsDialog.message().orEmpty()
            assertTrue(
                "the refusal names the newer version reason, it reads \"$message\"",
                message.contains(SettingsStrings.of(R.string.backup_error_newer_version)),
            )
            SettingsDialog.tap(SettingsDialog.POSITIVE)
            SettingsDialog.awaitDismissed()

            SettingsPrefs.assertUnset(Preferences.highQualityKey, Preferences.hideDelayKey)
        }
    }

    @Test
    @TestId("SET-80")
    @TestDuration(DurationCategory.LONG)
    fun anImportReplacesTheSettingsItCoversAndKeepsTheChosenBackupFolder() {
        val tree = testDirectory()

        launchSettingsOn(SettingsPage.APPEARANCE).use {
            toggleSwitch(SettingsStrings.of(R.string.anti_aliasing), false)
        }
        launchSettingsOn(SettingsPage.READING).use {
            toggleSwitch(SettingsStrings.of(R.string.keep_screen_on), true)
        }
        SettingsPrefs.awaitBoolean(Preferences.antiAliasingKey, false)
        SettingsPrefs.awaitBoolean(Preferences.screenOnKey, true)

        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            SettingsScreen.tapRow(SettingsStrings.of(R.string.backup_folder_title))
            BackupTree.pick(tree, picker, Await.LONG_TIMEOUT_MS)
            SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
            SettingsPrefs.awaitPresent(Preferences.backupFolderTreeUriKey, Await.LONG_TIMEOUT_MS)
            val storedTree = SettingsPrefs.string(Preferences.backupFolderTreeUriKey)

            runImport(SettingsFixtures.SETTINGS_ONLY_BACKUP, expectWipeWarning = false)

            SettingsPrefs.assertUnset(Preferences.antiAliasingKey, Preferences.screenOnKey)
            assertEquals(
                "the chosen backup folder is excluded from the wipe",
                storedTree,
                SettingsPrefs.string(Preferences.backupFolderTreeUriKey),
            )
            ImportSignals.clearProgressDialog(scenario)

            backToRoot()
            SettingsScreen.openPage(SettingsPage.APPEARANCE)
            assertEquals(
                "Anti Aliasing goes back to its on default",
                true,
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.anti_aliasing)).checked,
            )
            backToRoot()
            SettingsScreen.openPage(SettingsPage.READING)
            assertEquals(
                "Keep Screen On goes back to its off default",
                false,
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.keep_screen_on)).checked,
            )
            backToRoot()
            SettingsScreen.openPage(SettingsPage.BACKUP)
            assertEquals(
                "the Backup Folder row still names the chosen folder",
                false,
                SettingsScreen.awaitRow(SettingsStrings.of(R.string.backup_folder_title)).summary ==
                    SettingsStrings.of(R.string.backup_folder_summary_unset),
            )
        }
    }

    @Test
    @TestId("SET-81")
    @TestDuration(DurationCategory.NORMAL)
    fun aSettingsOnlyImportWritesASafetySnapshotThatCanBeRestored() {
        launchSettingsOn(SettingsPage.READING).use {
            toggleSwitch(SettingsStrings.of(R.string.keep_screen_on), true)
        }
        SettingsPrefs.awaitBoolean(Preferences.screenOnKey, true)
        assertEquals(
            "the safety directory starts empty",
            emptyList<String>(),
            SettingsFixtures.safetySnapshotNames(),
        )

        launchSettingsOn(SettingsPage.BACKUP).use { scenario ->
            runImport(
                SettingsFixtures.SETTINGS_ONLY_BACKUP, expectWipeWarning = false,
                preparePicker = { current ->
                    val packageName = TestEnvironment.uiDevice.currentPackageName
                    val fixtureName = "00-picker-backup-${SettingsFixtures.SETTINGS_ONLY_BACKUP}"
                    val listSelector = androidx.test.uiautomator.By.res(packageName, "dir_list")
                    val fixtureSelector = androidx.test.uiautomator.By.text(fixtureName).hasAncestor(listSelector)
                    assertTrue("the JSON fixture is listed before moving the viewport", current.scrollToEntry(
                        fixtureSelector, Await.DEFAULT_TIMEOUT_MS, fixtureName,
                    ) != null)
                    Await.until("the picker shows PDF rows with the JSON fixture outside the viewport") {
                        TestEnvironment.uiDevice.findObject(listSelector)
                            ?.scroll(androidx.test.uiautomator.Direction.DOWN, 0.8f)
                        current.waitForIdle()
                        !TestEnvironment.uiDevice.hasObject(fixtureSelector) &&
                            current.visibleTexts().any { it.endsWith(".pdf", ignoreCase = true) }
                    }
                },
            )

            val snapshots = SettingsFixtures.safetySnapshotNames()
            assertEquals(
                "a settings only import writes exactly one snapshot, it wrote $snapshots",
                1,
                snapshots.size,
            )
            assertTrue(
                "the snapshot is named for the moment it was taken, it reads ${snapshots.first()}",
                SNAPSHOT_NAME_PATTERN.matches(snapshots.first()),
            )
            SettingsPrefs.assertUnset(Preferences.screenOnKey)

            ImportSignals.clearProgressDialog(scenario)
            backToRoot()
            SettingsScreen.openPage(SettingsPage.BACKUP)

            val restoreTitle = SettingsStrings.of(R.string.backup_restore_snapshot_title)
            val row = SettingsScreen.awaitRow(restoreTitle)
            assertTrue(
                "the restore row explains which snapshot it would bring back, it reads ${row.summary}",
                row.summaryOrEmpty().isNotBlank(),
            )

            val before = ImportSignals.completions()
            SettingsScreen.tapRow(restoreTitle)
            SettingsDialog.awaitShown()
            SettingsDialog.confirm()
            ImportSignals.awaitCompletionAfter(before)

            SettingsPrefs.awaitBoolean(Preferences.screenOnKey, true)
            SettingsPrefs.assertUnset(Preferences.highQualityKey)
            assertEquals(
                "the restore runs with createSafety false, so no second snapshot is written",
                1,
                SettingsFixtures.safetySnapshotNames().size,
            )
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    private fun runImport(
        fixtureName: String,
        expectWipeWarning: Boolean,
        importPicker: SafPickerRobot = picker,
        onSelectionDispatched: () -> Unit = {},
        preparePicker: (SafPickerRobot) -> Unit = {},
    ) {
        val fixture = SettingsFixtures.pickerBackupFixture(fixtureName)
        val before = ImportSignals.completions()
        SettingsScreen.tapRow(importTitle)
        BackupTree.openFixture(fixture, importPicker, onSelectionDispatched, preparePicker)
        SettingsScreen.awaitResumed(Await.LONG_TIMEOUT_MS)
        if (expectWipeWarning) {
            Dialogs.awaitText(SettingsStrings.of(R.string.backup_import_wipe_action), Await.LONG_TIMEOUT_MS)
            SettingsDialog.awaitButtonEnabled(SettingsDialog.POSITIVE, true, COUNTDOWN_TIMEOUT_MS)
            SettingsDialog.confirm()
        } else {
            SettingsDialog.assertNoTextWithin(SettingsStrings.of(R.string.backup_import_wipe_action))
        }
        ImportSignals.awaitCompletionAfter(before)
    }

    private fun assertSkippedCount(expected: Int) {
        val pending = SettingsPrefs.string(Preferences.importResultPendingKey).orEmpty()
        assertTrue(
            "the pending result counts $expected skipped settings, it reads \"$pending\"",
            pending.contains(SettingsStrings.of(R.string.backup_import_skipped_settings, expected)),
        )
    }

    private val importTitle: String get() = SettingsStrings.of(R.string.backup_import_title)

    private companion object {
        const val APPLIED_SETTINGS = 5
        const val COUNTDOWN_TIMEOUT_MS = 10_000L
        const val TOLERANCE = 0.0001f
        val SNAPSHOT_NAME_PATTERN = Regex("mj-pdf-pre-import-\\d{8}-\\d{6}\\.json")
    }
}
