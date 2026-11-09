// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.gitlab.mudlej.MjPdfReader.R
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {

    private const val workName = "autoBackupWork"
    private const val legacyWorkTag = "autoBackupWork"

    fun ensureScheduled(context: Context, hour: Int, minute: Int) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest(hour, minute),
        )
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(legacyWorkTag)
        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            periodicRequest(hour, minute),
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag(legacyWorkTag)
        workManager.cancelUniqueWork(workName)
    }

    private fun periodicRequest(hour: Int, minute: Int): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(hour, minute, LocalDateTime.now()), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }

    internal fun millisUntilNext(hour: Int, minute: Int, now: LocalDateTime): Long {
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = Preferences(PreferenceManager.getDefaultSharedPreferences(applicationContext))
        if (!preferences.getAutoBackupEnabled()) {
            return Result.success()
        }
        val folder = BackupFolder.resolve(applicationContext, preferences.getBackupFolderTreeUri())
        if (folder == null) {
            preferences.setAutoBackupLastResult(
                System.currentTimeMillis(),
                applicationContext.getString(R.string.backup_error_folder_unavailable),
            )
            return Result.success()
        }
        try {
            BackupFolder.sweepStaleTmpFiles(folder)
            val backupManager = BackupManager(
                applicationContext,
                PdfRepository(AppDatabase.getInstance(applicationContext)),
            )
            backupManager.export(
                folder,
                BackupFolder.newAutoBackupFileName(),
                BackupExportOptions(includeSettings = true, includeHistory = true, includePasswords = false),
            )
            BackupFolder.enforceRetention(folder)
            preferences.setAutoBackupLastResult(System.currentTimeMillis(), null)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if ((exception is IOException || exception is BackupException) && runAttemptCount < 3) {
                return Result.retry()
            }
            preferences.setAutoBackupLastResult(
                System.currentTimeMillis(),
                BackupException.render(applicationContext, exception),
            )
        }
        return Result.success()
    }
}
