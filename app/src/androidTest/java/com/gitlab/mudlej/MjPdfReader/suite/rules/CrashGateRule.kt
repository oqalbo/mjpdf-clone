// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.SuiteTiming
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestIds
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class ExpectsProcessDeath

object IntentionalKills {

    private val expected = mutableSetOf<String>()

    @Synchronized
    fun expect(reason: String) {
        expected += reason
    }

    @Synchronized
    fun expected(): Set<String> = expected.toSet()

    @Synchronized
    fun isExpected(): Boolean = expected.isNotEmpty()

    @Synchronized
    fun clear() {
        expected.clear()
    }
}

class AppCrashedDuringTestException(message: String) : AssertionError(message)

class CrashGateRule(private val enabled: Boolean = true) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        if (!enabled) return base
        return object : Statement() {
            override fun evaluate() {
                IntentionalKills.clear()
                val since = CrashGate.timestampNow()
                var testFailure: Throwable? = null
                try {
                    base.evaluate()
                } catch (error: Throwable) {
                    testFailure = error
                }

                val findings = SuiteTiming.measure("crashGate.scan") { scan(since, description) }
                IntentionalKills.clear()

                if (testFailure != null) {
                    if (findings.isNotEmpty()) {
                        runCatching {
                            FailureArtifacts.write(TestIds.of(description), CRASH_GATE_FILE, findings.joinToString("\n\n"))
                        }
                    }
                    throw testFailure
                }
                if (findings.isNotEmpty()) {
                    val testId = TestIds.of(description)
                    runCatching { FailureArtifacts.write(testId, CRASH_GATE_FILE, findings.joinToString("\n\n")) }
                    throw AppCrashedDuringTestException(
                        "The test body passed but the crash gate found evidence the app misbehaved:\n\n" +
                            findings.joinToString("\n\n")
                    )
                }
            }
        }
    }

    private fun scan(since: String, description: Description): List<String> {
        val logcat = Shell.executeQuietly("logcat -d -v threadtime -T $since")
        if (logcat.isBlank()) return emptyList()
        val findings = mutableListOf<String>()

        val allowProcessDeath = IntentionalKills.isExpected() ||
            description.getAnnotation(ExpectsProcessDeath::class.java) != null ||
            description.testClass?.getAnnotation(ExpectsProcessDeath::class.java) != null

        val fatal = fatalLines(logcat, TestEnvironment.targetPackage).take(MAX_REPORTED_LINES)
        if (fatal.isNotEmpty() && !allowProcessDeath) {
            findings += "The app logged a fatal exception:\n" + fatal.joinToString("\n")
        }

        val anr = matchingLines(logcat) { line ->
            line.contains(ANR_MARKER) && line.contains(TestEnvironment.targetPackage)
        }
        if (anr.isNotEmpty()) {
            findings += "The app was reported as not responding:\n" + anr.joinToString("\n")
        }

        val pdfiumOnMain = matchingLines(logcat) { line ->
            line.contains(CrashGate.PDFIUM_MAIN_THREAD_MARKER) && !CrashGate.isAcceptedMainThreadPdfium(line)
        }
        if (pdfiumOnMain.isNotEmpty()) {
            findings += "pdfium ran on the main thread outside the paths that are accepted there:\n" +
                pdfiumOnMain.joinToString("\n") + "\n\n" + CrashGate.ACCEPTED_MAIN_THREAD_PDFIUM_REASON
        }

        return findings
    }

    private fun matchingLines(logcat: String, predicate: (String) -> Boolean): List<String> =
        logcat.lineSequence().filter(predicate).take(MAX_REPORTED_LINES).toList()

    companion object {
        internal fun fatalLines(logcat: String, packageName: String): List<String> {
            val pending = mutableMapOf<String, String>()
            val fatal = mutableListOf<String>()
            logcat.lineSequence().forEach { line ->
                val record = RUNTIME_RECORD.matchEntire(line) ?: return@forEach
                val pid = record.groupValues[1]
                val message = record.groupValues[2]
                if (message.startsWith(FATAL_EXCEPTION)) {
                    pending[pid] = line
                    return@forEach
                }
                val process = PROCESS_RECORD.matchEntire(message) ?: return@forEach
                val header = pending.remove(pid) ?: return@forEach
                val name = process.groupValues[1]
                if (process.groupValues[2] == pid && (name == packageName || name.startsWith("$packageName:"))) {
                    fatal += header
                    fatal += line
                }
            }
            return fatal
        }

        private val RUNTIME_RECORD = Regex("\\S+\\s+\\S+\\s+(\\d+)\\s+\\d+\\s+[EF]\\s+AndroidRuntime:\\s+(.*)")
        private val PROCESS_RECORD = Regex("Process: ([^,]+), PID: (\\d+)")
        const val FATAL_EXCEPTION = "FATAL EXCEPTION"
        const val ANR_MARKER = "ANR in"
        const val CRASH_GATE_FILE = "crash-gate.txt"
        const val MAX_REPORTED_LINES = 40
    }
}
