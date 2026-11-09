// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

object BackupNotices {

    private const val staleAfterMillis = 36L * 60L * 60L * 1000L

    fun shouldShowFailureNotice(preferences: Preferences): Boolean {
        return preferences.getAutoBackupLastError() != null &&
            preferences.getAutoBackupLastRun() > preferences.getAutoBackupErrorAcknowledgedRun()
    }

    fun shouldShowStaleNotice(preferences: Preferences, now: () -> Long = System::currentTimeMillis): Boolean {
        if (!preferences.getAutoBackupEnabled()) {
            return false
        }
        val enabledAt = preferences.ensureAutoBackupEnabledAt()
        val freshest = maxOf(preferences.getAutoBackupLastRun(), enabledAt)
        return freshest < now() - staleAfterMillis
    }

    fun acknowledge(preferences: Preferences) {
        preferences.setAutoBackupErrorAcknowledgedRun(preferences.getAutoBackupLastRun())
    }
}
