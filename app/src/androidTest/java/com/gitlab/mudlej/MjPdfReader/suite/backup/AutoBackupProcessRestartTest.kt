// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.data.BackupData
import com.gitlab.mudlej.MjPdfReader.data.BackupImportPipeline
import com.gitlab.mudlej.MjPdfReader.data.BackupManager
import com.gitlab.mudlej.MjPdfReader.data.BackupSetting
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@TestId("BKP-59")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AutoBackupProcessRestartTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @After
    fun cancelScheduledWork() {
        AutoBackupControl.cancel()
        BackupDebugHooks.suppressRestart = false
    }

    @Test
    fun phaseOneImportAutoBackupOnAndLeaveTheScheduleCancelled() {
        PhaseRun.requirePhase(SCENARIO, 1)
        BackupDebugHooks.suppressRestart = true

        val imported = BackupData(
            schemaVersion = BackupManager.SCHEMA_VERSION,
            settings = listOf(
                BackupSetting(
                    key = Preferences.autoBackupEnabledKey,
                    type = Preferences.kindBoolean,
                    value = true.toString(),
                )
            ),
        )
        val outcome = ImportSignals.awaitImport {
            val started = BackupImportPipeline.start(
                TestEnvironment.targetContext.applicationContext,
                imported,
                createSafety = false,
                onRejected = { false },
            )
            assertTrue("The import pipeline refused the phase-one import", started)
        }

        assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)
        assertEquals(
            "The restored auto-backup preference did not arrive",
            true,
            BackupSeed.boolean(Preferences.autoBackupEnabledKey),
        )
        assertFalse(
            "An import must never carry the exporting device's backup folder across",
            BackupSeed.contains(Preferences.backupFolderTreeUriKey),
        )
        BackupWork.assertStaysWithoutLiveWork()
    }

    @Test
    fun phaseTwoAssertProcessStartupRescheduledTheImportedPreference() {
        PhaseRun.requirePhase(SCENARIO, 2)

        assertEquals(
            "The imported auto-backup preference did not survive the process boundary",
            true,
            BackupSeed.boolean(Preferences.autoBackupEnabledKey),
        )
        assertFalse(
            "The process boundary introduced a backup folder that the import did not contain",
            BackupSeed.contains(Preferences.backupFolderTreeUriKey),
        )
        BackupWork.awaitSingleLive()

        val result = BackupWork.runWorker()
        assertTrue(BackupWork.isSuccess(result))
        assertEquals(
            "A restored schedule with nowhere to write must report itself rather than run silently",
            BackupStrings.errorFolderUnavailable,
            BackupSeed.string(Preferences.autoBackupLastErrorKey),
        )
    }

    private companion object {
        const val SCENARIO = "BKP-59"
    }
}
