// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object BackupStrings {
    fun of(@StringRes id: Int): String = TestEnvironment.appString(id)

    fun of(@StringRes id: Int, vararg args: Any): String =
        TestEnvironment.appString(id, *args)

    val backupPageTitle: String get() = of(R.string.settings_backup)

    val backupFolderTitle: String get() = of(R.string.backup_folder_title)
    val autoBackupTitle: String get() = of(R.string.auto_backup_title)
    val autoBackupTimeTitle: String get() = of(R.string.auto_backup_time_title)
    val exportTitle: String get() = of(R.string.backup_export_title)
    val importTitle: String get() = of(R.string.backup_import_title)
    val restoreSnapshotTitle: String get() = of(R.string.backup_restore_snapshot_title)

    val wipeAction: String get() = of(R.string.backup_import_wipe_action)
    val cancel: String get() = of(R.string.cancel)
    val ok: String get() = of(R.string.ok)

    val optionSettings: String get() = of(R.string.backup_export_option_settings)
    val optionHistory: String get() = of(R.string.backup_export_option_history)
    val optionPasswords: String get() = of(R.string.include_saved_passwords)

    val wipeWarning: String get() = of(R.string.backup_import_wipe_warning)
    val folderUnavailable: String get() = of(R.string.backup_folder_unavailable)
    val importFinished: String get() = of(R.string.backup_import_finished)
    val importRunning: String get() = of(R.string.backup_import_running)

    val errorNotValid: String get() = of(R.string.backup_error_not_valid)
    val errorNewerVersion: String get() = of(R.string.backup_error_newer_version)
    val errorTooLarge: String get() = of(R.string.backup_error_too_large)
    val errorNothingToImport: String get() = of(R.string.backup_error_nothing_to_import)
    val errorCreateFile: String get() = of(R.string.backup_error_create_file)
    val errorFolderUnavailable: String get() = of(R.string.backup_error_folder_unavailable)

    val failureNotice: String get() = of(R.string.auto_backup_failure_notice)
    val staleNotice: String get() = of(R.string.auto_backup_stale_notice)
    val noticeAction: String get() = of(R.string.auto_backup_notice_action)

    fun exportedSettings(count: Int): String = of(R.string.backup_export_done_settings, count)

    fun exportedHistory(records: Int, bookmarks: Int): String =
        of(R.string.backup_export_done_history, records, bookmarks)

    val exportedSettingsAnyCount: Regex get() = anyCountPattern(R.string.backup_export_done_settings)

    val exportedHistoryAnyCount: Regex get() = anyCountPattern(R.string.backup_export_done_history)

    private fun anyCountPattern(@StringRes id: Int): Regex =
        Regex(NUMBER_PLACEHOLDER.split(of(id)).joinToString(DIGITS) { Regex.escape(it) })

    private val NUMBER_PLACEHOLDER = Regex("%\\d+\\\$d")

    private const val DIGITS = "\\d+"

    fun importedSettings(count: Int): String = of(R.string.backup_import_done_settings, count)

    fun importedHistory(records: Int, bookmarks: Int): String =
        of(R.string.backup_import_done_history, records, bookmarks)

    fun skippedSettings(count: Int): String = of(R.string.backup_import_skipped_settings, count)

    val importFailedPrefix: String
        get() = of(R.string.backup_import_failed).substringBefore("%1\$s").trim()

    val importFailedBeforeChangesPrefix: String
        get() = of(R.string.backup_import_failed_before_changes).substringBefore("%1\$s").trim()

    fun importSafetyAborted(reason: String): String =
        of(R.string.backup_import_safety_aborted, reason)

    fun autoBackupTimeSummary(time: String): String = of(R.string.auto_backup_time_summary, time)

    fun autoBackupLastFailed(error: String): String = of(R.string.auto_backup_last_failed, error)

    val restoreSnapshotSummaryPrefix: String
        get() = of(R.string.backup_restore_snapshot_summary).substringBefore("%1\$s").trim()
}
