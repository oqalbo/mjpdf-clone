// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.search

import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat


object SearchLogcat {

    const val PATTERN_SYNTAX = "PatternSyntaxException"
    const val OUT_OF_MEMORY = "OutOfMemoryError"
    const val EXTRACTOR_TAG = "PdfExtractor"
    const val COORDINATOR_TAG = "SearchCoordinator"

    fun since(): String = AppLogcat.since()

    private fun linesContaining(since: String, needle: String): List<String> =
        AppLogcat.appLinesSince(since).filter { it.contains(needle) }

    fun assertAbsentSince(since: String, needle: String) {
        val hits = linesContaining(since, needle)
        if (hits.isNotEmpty()) {
            throw AssertionError(
                "logcat carries \"$needle\" from the app process while none was expected:\n" +
                    hits.joinToString("\n")
            )
        }
    }

    fun assertNoUnhandledExceptionSince(since: String) {
        val offenders = AppLogcat.appLinesSince(since).filter { line ->
            UNHANDLED_MARKERS.any { line.contains(it) }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "logcat carries an unhandled exception from the app process while a handled failure path was " +
                    "expected to absorb it:\n" + offenders.joinToString("\n")
            )
        }
    }

    fun assertNoErrorFrom(since: String, tag: String) {
        val offenders = AppLogcat.appLinesSince(since).filter { line ->
            line.contains(tag) && errorLevelOf(line) in FAILURE_LEVELS
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "logcat carries an error or fatal entry tagged $tag while none was expected:\n" +
                    offenders.joinToString("\n")
            )
        }
    }

    private fun errorLevelOf(line: String): String? {
        val fields = line.trim().split(WHITESPACE)
        return fields.getOrNull(4)
    }

    private val UNHANDLED_MARKERS = listOf("FATAL EXCEPTION", "AndroidRuntime: Process")

    private val WHITESPACE = Regex("\\s+")
    private val FAILURE_LEVELS = setOf("E", "F")
}
