// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.performance

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate

object PerfLogcat {

    fun since(): String = AppLogcat.since()

    fun mainThreadViolationsSince(since: String): List<String> =
        CrashGate.unacceptedMainThreadPdfiumLines(AppLogcat.appLinesSince(since).asSequence())
}
