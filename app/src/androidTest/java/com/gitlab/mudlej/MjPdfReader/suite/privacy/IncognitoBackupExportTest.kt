// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupJson
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupStrings
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.backup.ExportOptions
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class IncognitoBackupExportTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val home = PrivacyHome()
    private val picker = SafPickerRobot()

    @After
    fun cleanUp() {
        PrivacySettingsScreen.leave()
        reader.finishTask()
        home.picker.dismissIfShown()
        runCatching { Fixtures.delete(exportRoot()) }
    }

    @Test
    @TestId("PRIV-35")
    fun backupExportDuringAnIncognitoSessionContainsNoIncognitoDocument() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        PrivacyFixtures.file(PrivacyFixtures.SECOND)
        exportRoot().mkdirs()

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(READ_TO_INDEX)
            PrivacyStores.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
        }

        home.launch()
        home.openIncognitoFromDownloads(PrivacyFixtures.SECOND)
        reader.awaitReady()
        reader.awaitIdentity()
        reader.jumpToPageIndex(READ_TO_INDEX)
        PrivacyStores.holdRecordCount(1)
        reader.finishTask()

        PrivacySettingsScreen.openBackup()
        PrivacySettingsScreen.tapRow(string(R.string.backup_export_title))
        ExportOptions.awaitShown()
        assertTrue(
            "the history option must be offered so the export covers records and bookmarks",
            TestEnvironment.uiDevice.hasObject(By.text(BackupStrings.optionHistory)),
        )
        ExportOptions.export()

        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        BackupTree.pick(exportRoot(), picker)

        val json = BackupJson.of(BackupTree.awaitManualBackup(exportRoot(), Await.VERY_LONG_TIMEOUT_MS))
        val records = json.records.orEmpty()

        assertEquals(
            "exactly one document may reach the backup, the one read normally",
            1,
            records.size,
        )
        assertEquals(
            "the exported record must name the document read normally",
            PrivacyFixtures.NORMAL_TITLE,
            records.firstOrNull()?.optString(FILE_NAME_MEMBER),
        )
        assertFalse(
            "the incognito document's name must not appear anywhere in the backup, including inside a URI",
            json.containsText(PrivacyFixtures.SECOND_TITLE),
        )
    }

    private fun exportRoot(): File = File(rules.testDirectory.directory(), EXPORT_FOLDER_NAME)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val READ_TO_INDEX = 1
        const val EXPORT_FOLDER_NAME = "priv-backup-qxzv"
        const val FILE_NAME_MEMBER = "fileName"
    }
}
