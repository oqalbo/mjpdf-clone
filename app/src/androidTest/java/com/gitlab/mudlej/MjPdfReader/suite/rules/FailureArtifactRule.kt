// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestIds
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateSource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class FailureArtifactRule(private val logcatLineCap: Int = 4000) : TestWatcher() {

    private var startedAtLogcatTime: String? = null
    private var testId: String = ""

    override fun starting(description: Description) {
        testId = TestIds.of(description)
        startedAtLogcatTime = CrashGate.timestampNow()
    }

    override fun failed(error: Throwable, description: Description) {
        val directory = runCatching { FailureArtifacts.directoryFor(testId) }.getOrElse { return }
        capture("failure summary") {
            File(directory, FailureArtifacts.FAILURE_FILE).writeText(summaryOf(description, error))
        }
        captureScreenState(directory)
        capture("logcat") {
            File(directory, FailureArtifacts.LOGCAT_FILE).writeText(readLogcat())
        }
        capture("reader state") {
            val state = ReaderStateSource.readRaw()
            if (!state.isNullOrEmpty()) {
                File(directory, FailureArtifacts.READER_STATE_FILE).writeText(state)
            }
        }
    }

    private fun captureScreenState(directory: File) {
        capture("window hierarchy") {
            TestEnvironment.uiDevice.dumpWindowHierarchy(File(directory, FailureArtifacts.HIERARCHY_FILE))
        }
        capture("screenshot") {
            TestEnvironment.uiDevice.takeScreenshot(File(directory, FailureArtifacts.SCREENSHOT_FILE))
        }
    }

    fun readLogcat(): String {
        val since = startedAtLogcatTime
        val fromTestStart = if (since != null) {
            Shell.executeQuietly("logcat -d -v threadtime -T $since")
        } else {
            ""
        }
        val captured = fromTestStart.ifBlank { Shell.executeQuietly("logcat -d -v threadtime -t $logcatLineCap") }
        return capToLastLines(captured)
    }

    private fun capToLastLines(text: String): String {
        val lines = text.lines()
        if (lines.size <= logcatLineCap) return text
        val dropped = lines.size - logcatLineCap
        return (listOf("... $dropped earlier lines were dropped by the ${logcatLineCap} line cap") +
            lines.takeLast(logcatLineCap)).joinToString("\n")
    }

    private fun summaryOf(description: Description, error: Throwable): String {
        val stackTrace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("test id: $testId")
            appendLine("class: ${description.className}")
            appendLine("method: ${description.methodName}")
            appendLine("failure: ${error::class.java.name}: ${error.message}")
            appendLine()
            append(stackTrace)
        }
    }

    private fun capture(what: String, block: () -> Unit) {
        runCatching(block).onFailure { failure ->
            runCatching {
                File(FailureArtifacts.directoryFor(testId), "artifact-errors.txt")
                    .appendText(describeCaptureFailure(what, failure) + "\n")
            }
        }
    }

    private fun describeCaptureFailure(what: String, failure: Throwable): String {
        if (!isLostUiAutomationConnection(failure)) return "could not capture $what: $failure"
        return "could not capture $what because the UiAutomation connection was lost: $failure. " +
            "Something outside this process registered its own UiAutomationService, which disconnects " +
            "instrumentation. A shell uiautomator dump run against this device while the suite is " +
            "running does exactly that, so check for another user of the device before suspecting the app."
    }

    private fun isLostUiAutomationConnection(failure: Throwable): Boolean =
        failure is IllegalStateException &&
            failure.message?.contains(LOST_CONNECTION_MARKER, ignoreCase = true) == true

    private companion object {
        const val LOST_CONNECTION_MARKER = "Not connected"
    }
}
