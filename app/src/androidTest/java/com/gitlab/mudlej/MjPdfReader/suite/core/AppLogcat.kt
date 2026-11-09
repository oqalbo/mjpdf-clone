// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.Process

object AppLogcat {

    fun since(): String = CrashGate.timestampNow()

    fun appLinesSince(since: String, filterSpec: String = ""): List<String> {
        val pid = Process.myPid().toString()
        val filter = if (filterSpec.isEmpty()) "" else " $filterSpec"
        return Shell.executeQuietly("logcat -d -v threadtime -T $since$filter")
            .lineSequence()
            .filter { line -> pidOf(line) == pid }
            .toList()
    }

    private fun pidOf(line: String): String? {
        val fields = line.trim().split(WHITESPACE)
        if (fields.size < 3) return null
        return fields[2].takeIf { field -> field.isNotEmpty() && field.all { it.isDigit() } }
    }

    private val WHITESPACE = Regex("\\s+")
}
