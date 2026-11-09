// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object AutoBackupWork {

    const val UNIQUE_NAME = "autoBackupWork"

    private val manager: WorkManager
        get() = WorkManager.getInstance(TestEnvironment.targetContext.applicationContext)

    fun infos(): List<WorkInfo> = manager.getWorkInfosForUniqueWork(UNIQUE_NAME).get()

    fun liveInfos(): List<WorkInfo> =
        infos().filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    fun awaitLiveCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<WorkInfo> =
        Await.untilValue(
            "the unique work $UNIQUE_NAME has $expected enqueued or running entries",
            timeoutMs,
            read = { runCatching { liveInfos() }.getOrDefault(emptyList()) },
        ) { it.size == expected }

    fun assertStaysWithoutLiveWork(windowMs: Long = SettingsPrefs.STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "no $UNIQUE_NAME work is enqueued or running",
            windowMs = windowMs,
        ) { runCatching { liveInfos().isEmpty() }.getOrDefault(false) }
    }
}
