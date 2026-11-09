// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await

object SecurityLogcat {
    fun mark(): String = AppLogcat.since()

    fun linesSince(since: String): List<String> =
        AppLogcat.appLinesSince(since).filterNot(UI_OBJECT2_TEXT_ENTRY::matches)

    fun awaitLineContaining(fragment: String, since: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): String =
        Await.untilValue(
            "a log line from the app pid containing \"$fragment\" reaches logcat",
            timeoutMs,
            pollMs = 250L,
            read = { linesSince(since).firstOrNull { it.contains(fragment) } },
        ) { it != null }!!

    fun assertAbsent(token: String, since: String, what: String) {
        val offending = linesSince(since).filter { it.contains(token) }
        if (offending.isNotEmpty()) {
            throw AssertionError(
                "$what leaked \"$token\" into logcat. App.kt configures ACRA with ReportField.LOGCAT, so a " +
                    "line carrying it can be uploaded without a prompt. The offending lines were:\n" +
                    offending.take(MAX_REPORTED_LINES).joinToString("\n")
            )
        }
    }

    const val MAX_REPORTED_LINES = 20

    private val UI_OBJECT2_TEXT_ENTRY = Regex(
        """^\d{2}-\d{2}\s+\S+\s+\d+\s+\d+\s+[VDIWEAF]\s+UiObject2:\s+Setting text to '.*'\.\s*"""
    )
}
