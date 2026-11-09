// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.gitlab.mudlej.MjPdfReader.data.AutoBackupWorker
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import kotlinx.coroutines.runBlocking
import java.util.UUID

object BackupWork {
    const val UNIQUE_NAME = "autoBackupWork"

    private val manager: WorkManager
        get() = WorkManager.getInstance(TestEnvironment.targetContext.applicationContext)

    fun infos(): List<WorkInfo> = manager.getWorkInfosForUniqueWork(UNIQUE_NAME).get()

    fun liveInfos(): List<WorkInfo> =
        infos().filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    fun states(): List<WorkInfo.State> = infos().map { it.state }

    fun awaitLiveCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<WorkInfo> =
        Await.untilValue(
            "the unique work $UNIQUE_NAME has $expected enqueued or running entries",
            timeoutMs,
            read = { runCatching { liveInfos() }.getOrDefault(emptyList()) },
        ) { it.size == expected }

    fun awaitSingleLive(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): WorkInfo =
        awaitLiveCount(1, timeoutMs).single()

    fun assertStaysWithoutLiveWork(windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no $UNIQUE_NAME work is enqueued or running",
            windowMs = windowMs,
        ) { runCatching { liveInfos().isEmpty() }.getOrDefault(false) }
    }

    fun awaitCancelled(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "every $UNIQUE_NAME work entry reads CANCELLED",
            timeoutMs,
            read = { runCatching { states() }.getOrDefault(emptyList()) },
        ) { states -> states.isNotEmpty() && states.all { it == WorkInfo.State.CANCELLED } }
        assertStaysWithoutLiveWork()
    }

    fun nextScheduleTimeMillis(info: WorkInfo): Long = info.nextScheduleTimeMillis

    fun awaitSameSchedule(id: UUID, nextRunAt: Long, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the unique work $UNIQUE_NAME is still id $id scheduled for $nextRunAt",
            timeoutMs,
            read = { runCatching { liveInfos().singleOrNull() }.getOrNull() },
        ) { it != null && it.id == id && it.nextScheduleTimeMillis == nextRunAt }
    }

    fun awaitDifferentId(previous: UUID, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): WorkInfo =
        Await.untilValue(
            "the unique work $UNIQUE_NAME is re-enqueued under an id other than $previous",
            timeoutMs,
            read = { runCatching { liveInfos().singleOrNull() }.getOrNull() },
        ) { it != null && it.id != previous }!!

    fun runWorker(runAttemptCount: Int = 0): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<AutoBackupWorker>(
            TestEnvironment.targetContext.applicationContext,
        ).setRunAttemptCount(runAttemptCount).build()
        return runBlocking { worker.doWork() }
    }

    fun isSuccess(result: ListenableWorker.Result): Boolean = result is ListenableWorker.Result.Success

    fun isFailure(result: ListenableWorker.Result): Boolean = result is ListenableWorker.Result.Failure
}
