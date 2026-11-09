// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.Await

object BackupClock {
    const val HOUR_MILLIS = 60L * 60L * 1000L

    fun awaitNextSecond() {
        val second = System.currentTimeMillis() / 1000L
        Await.until("the wall clock second advances past $second", Await.SHORT_TIMEOUT_MS, pollMs = 20L) {
            System.currentTimeMillis() / 1000L > second
        }
    }

    fun hoursAgo(hours: Long): Long = System.currentTimeMillis() - hours * HOUR_MILLIS

    fun minutesAgo(minutes: Long): Long = System.currentTimeMillis() - minutes * 60L * 1000L
}
