// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupDialog
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupPage
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupStrings
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.backup.ExportOptions
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import java.io.File

object LocaleBackup {

    val asciiManualName = Regex("mj-pdf-backup-[0-9]{8}-[0-9]{6}\\.json")

    fun launchBackupPage(): ActivityScenario<SettingsActivity> = BackupPage.launch()

    fun grantFolder(directory: File) {
        BackupPage.tapRow(BackupStrings.backupFolderTitle)
        BackupTree.pick(directory, SafPickerRobot())
        Await.untilNotNull(
            "the backup folder grant is persisted into ${Preferences.backupFolderTreeUriKey}",
            Await.LONG_TIMEOUT_MS,
        ) { TestEnvironment.defaultPreferences.getString(Preferences.backupFolderTreeUriKey, null) }
        BackupPage.awaitSummaryContaining(BackupStrings.backupFolderTitle, directory.name)
    }

    fun exportWithDefaults() {
        BackupPage.startExport()
        ExportOptions.awaitExportEnabled(true)
        ExportOptions.export()
    }

    fun awaitExportReport(): String {
        BackupDialog.awaitTitle(BackupStrings.exportTitle, Await.LONG_TIMEOUT_MS)
        return BackupDialog.message().orEmpty()
    }

    fun dismissExportReport() {
        BackupDialog.tap(BackupDialog.POSITIVE)
        BackupDialog.awaitGone()
    }

    fun seedAutoBackup(directory: File, name: String, modifiedAt: Long): File {
        val file = BackupTree.seed(directory, name, SEED_CONTENT.toByteArray(Charsets.UTF_8))
        file.setLastModified(modifiedAt)
        Await.untilValue(
            "the modification time of $name reads back as $modifiedAt",
            Await.SHORT_TIMEOUT_MS,
            read = { file.lastModified() / MILLIS_PER_SECOND },
        ) { it == modifiedAt / MILLIS_PER_SECOND }
        return file
    }

    fun autoBackupName(stamp: String): String = "mj-pdf-auto-backup-$stamp.json"

    fun toPersianDigits(value: String): String =
        value.map { character ->
            val digit = Character.digit(character, RADIX)
            if (digit >= 0) PERSIAN_ZERO + digit else character
        }.joinToString(separator = "")

    fun hasNonAsciiDigit(value: String): Boolean =
        value.any { Character.digit(it, RADIX) >= 0 && it !in '0'..'9' }

    private const val SEED_CONTENT = "{\"version\":1,\"settings\":{},\"history\":{}}"
    private const val MILLIS_PER_SECOND = 1_000L
    private const val RADIX = 10
    private const val PERSIAN_ZERO = '۰'
}
