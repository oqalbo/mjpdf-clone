// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat


object StrictModeLog {

    const val VIOLATION_MARKER = "StrictMode policy violation"
    const val DISK_READ_MARKER = "DiskReadViolation"
    const val DISK_WRITE_MARKER = "DiskWriteViolation"

    fun diskViolationsSince(since: String): List<String> =
        AppLogcat.appLinesSince(since).filter { line ->
            line.contains(VIOLATION_MARKER) &&
                (line.contains(DISK_READ_MARKER) || line.contains(DISK_WRITE_MARKER))
        }

    fun assertNoDiskViolationsSince(since: String) {
        val violations = diskViolationsSince(since)
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "The debug thread policy reported disk work on the main thread during the load:\n" +
                    violations.joinToString("\n")
            )
        }
    }
}
