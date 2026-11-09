// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat


object TextLogcat {

    fun since(): String = AppLogcat.since()

    fun assertAbsentSince(since: String, needle: String) {
        val hits = AppLogcat.appLinesSince(since).filter { it.contains(needle) }
        if (hits.isNotEmpty()) {
            throw AssertionError(
                "logcat carries $needle from the app process while none was expected:\n${hits.joinToString("\n")}"
            )
        }
    }
}
