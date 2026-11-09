// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.localization.LocaleRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Rule
import org.junit.rules.RuleChain
import java.io.File

abstract class BackupTestCase {
    protected val suite = SuiteRules.standard()
    protected val locale = LocaleRule()
    protected val cleanup = BackupCleanupRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(locale)
        .around(suite)
        .around(cleanup)

    protected fun tree(): File = suite.testDirectory.directory().also { BackupTree.requireEmpty(it) }

    protected fun launchBackupPage(): ActivityScenario<SettingsActivity> = BackupPage.launch()

    protected fun leaveBackupPage() {
        repeat(BACK_PRESS_BUDGET) {
            if (TestEnvironment.resumedActivityOfType(SettingsActivity::class.java) == null) return
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.waitForIdleSync()
        }
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    protected fun grantFolder(directory: File, picker: SafPickerRobot = SafPickerRobot()) {
        BackupPage.tapRow(BackupStrings.backupFolderTitle)
        BackupTree.pick(directory, picker)
        BackupPage.awaitShown()
        BackupPage.awaitSummaryContaining(BackupStrings.backupFolderTitle, directory.name)
    }

    protected fun exportWithDefaults(directory: File): File {
        BackupPage.startExport()
        ExportOptions.export()
        return BackupTree.awaitManualBackup(directory)
    }

    protected fun startImportOf(
        name: String,
        picker: SafPickerRobot = SafPickerRobot(),
        onSelectionDispatched: () -> Unit = {},
    ) {
        BackupPage.startImport()
        BackupFixtures.open(name, picker, onSelectionDispatched)
    }

    protected fun confirmWipeWarning() {
        BackupDialog.awaitMessageContaining(BackupStrings.wipeWarning.take(WIPE_WARNING_PREFIX))
        BackupDialog.awaitButtonEnabled(BackupDialog.POSITIVE, WIPE_COUNTDOWN_TIMEOUT_MS)
        BackupDialog.tap(BackupDialog.POSITIVE)
    }

    protected fun importHistoryFixture(name: String): String = ImportSignals.awaitImport {
        startImportOf(name)
        confirmWipeWarning()
    }

    protected fun importSettingsFixture(name: String): String = ImportSignals.awaitImport {
        startImportOf(name)
    }

    protected fun waitForIdle() {
        TestEnvironment.waitForIdleSync()
    }

    protected companion object {
        const val BACK_PRESS_BUDGET = 6
        const val WIPE_COUNTDOWN_TIMEOUT_MS = 15_000L
        const val WIPE_WARNING_PREFIX = 40
    }
}
