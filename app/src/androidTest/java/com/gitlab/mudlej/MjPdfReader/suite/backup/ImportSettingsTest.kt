// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.TapToTurnZones
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ImportSettingsTest : BackupTestCase() {
    @Test
    @TestId("BKP-21")
    @TestDuration(DurationCategory.LONG)
    fun excludedSettingsSurviveAnImport() {
        val tree = tree()
        BackupSeed.settings(
            mapOf(
                Preferences.firstInstallKey to false,
                Preferences.showFeaturesDialogKey to false,
            )
        )

        launchBackupPage().use { scenario ->
            grantFolder(tree)
            val grantedTreeUri = BackupSeed.string(Preferences.backupFolderTreeUriKey)
            assertNotNull("The setup grant did not write backupFolderTreeUri", grantedTreeUri)

            importHistoryFixture(BackupFixtures.VALID_FULL)

            assertEquals(
                "The import repointed this device's backup folder at the exporting device's one",
                grantedTreeUri,
                BackupSeed.string(Preferences.backupFolderTreeUriKey),
            )
            assertEquals(
                "firstInstall was overwritten, which would replay onboarding",
                false,
                BackupSeed.boolean(Preferences.firstInstallKey),
            )
            assertEquals(
                "showFeaturesDialog was overwritten, which would replay the features dialog",
                false,
                BackupSeed.boolean(Preferences.showFeaturesDialogKey),
            )

            ImportSignals.clearProgressDialog(scenario)
            BackupPage.awaitSummaryContaining(BackupStrings.backupFolderTitle, tree.name)
        }
    }

    @Test
    @TestId("BKP-22")
    fun settingsAbsentFromTheBackupFallBackToDefaults() {
        val json = BackupFixtures.json(BackupFixtures.VALID_FULL)
        val fileKeys = json.settingKeys()
        assertFalse(
            "The fixture now carries horizontalScroll, so it can no longer prove the fallback",
            fileKeys.contains(Preferences.horizontalScrollKey),
        )
        assertFalse(
            "The fixture now carries screenOn, so it can no longer prove the fallback",
            fileKeys.contains(Preferences.screenOnKey),
        )
        assertTrue(
            "The fixture no longer carries highQuality, so the applied half of this test is void",
            fileKeys.contains(Preferences.highQualityKey),
        )

        BackupSeed.settings(
            mapOf(
                Preferences.horizontalScrollKey to true,
                Preferences.screenOnKey to true,
            )
        )

        launchBackupPage().use {
            importHistoryFixture(BackupFixtures.VALID_FULL)

            assertFalse(
                "horizontalScroll survived an import of a file that never mentions it",
                BackupSeed.contains(Preferences.horizontalScrollKey),
            )
            assertFalse(
                "screenOn survived an import of a file that never mentions it",
                BackupSeed.contains(Preferences.screenOnKey),
            )

            val preferences = Preferences(BackupSeed.preferences)
            assertEquals(Preferences.horizontalScrollDefault, preferences.getHorizontalScroll())
            assertEquals(Preferences.screenOnDefault, preferences.getScreenOn())
            assertEquals(
                "The setting the file does carry was not applied",
                true,
                BackupSeed.boolean(Preferences.highQualityKey),
            )
        }
    }

    @Test
    @TestId("BKP-23")
    fun unknownMistypedAndOutOfDomainSettingsAreSkippedAndCounted() {
        launchBackupPage().use {
            val outcome = importSettingsFixture(BackupFixtures.BAD_SETTINGS)
            assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)

            BackupFixtures.BAD_SETTINGS_SKIPPED_KEYS.forEach { key ->
                assertFalse(
                    "The skipped setting $key was written to the preference store",
                    BackupSeed.contains(key),
                )
            }
            assertEquals(
                "The out of domain enum value reached the store instead of falling back",
                TapToTurnZones.LEFT_RIGHT,
                Preferences(BackupSeed.preferences).getTapToTurnZones(),
            )
            BackupFixtures.BAD_SETTINGS_APPLIED_KEYS.forEach { key ->
                assertTrue(
                    "The usable setting $key was dropped along with the unusable ones",
                    BackupSeed.contains(key),
                )
            }
        }

        HomeRobot().launchFromLauncher(Await.LONG_TIMEOUT_MS) {
            BackupDialog.awaitTitle(BackupStrings.importTitle, Await.LONG_TIMEOUT_MS)
            val message = Await.untilNotNull("the import summary dialog carries a message") {
                BackupDialog.message()
            }
            assertTrue(
                "The summary does not report three skipped settings, it read \"$message\"",
                message.contains(BackupStrings.skippedSettings(BackupFixtures.BAD_SETTINGS_SKIPPED)),
            )
            BackupDialog.dismissWithOk()
        }
    }
}
