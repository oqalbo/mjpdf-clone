// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await

object IntakeLogcat {

    fun mark(): String = AppLogcat.since()

    private fun errorLines(tag: String, since: String): List<String> =
        AppLogcat.appLinesSince(since, "$tag:E *:S")
            .filter { it.contains(" $tag") || it.contains("$tag:") }

    fun awaitErrorContaining(
        tag: String,
        fragment: String,
        since: String,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): String = Await.untilValue(
        "an error tagged $tag containing \"$fragment\" reaches logcat",
        timeoutMs,
        pollMs = 250L,
        read = { errorLines(tag, since).firstOrNull { it.contains(fragment) } },
    ) { it != null }!!
}
