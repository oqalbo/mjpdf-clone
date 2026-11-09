// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

data class ShellStartResult(val command: String, val output: String) {
    val refusedAsNotExported: Boolean
        get() = output.contains(NOT_EXPORTED_MARKER, ignoreCase = true) ||
            output.contains(PERMISSION_DENIAL_MARKER, ignoreCase = true)

    val reportedSecurityException: Boolean
        get() = output.contains(SECURITY_EXCEPTION_MARKER)

    val succeeded: Boolean
        get() = output.contains(STARTING_MARKER) && !reportedSecurityException

    override fun toString(): String = "$command\n${output.trim()}"

    companion object {
        const val NOT_EXPORTED_MARKER = "not exported"
        const val PERMISSION_DENIAL_MARKER = "Permission Denial"
        const val SECURITY_EXCEPTION_MARKER = "SecurityException"
        const val STARTING_MARKER = "Starting: Intent"
    }
}

object ShellIntent {
    fun isAvailable(): Boolean =
        runCatching { Shell.execute("am --help").isNotBlank() }.getOrDefault(false)

    fun startComponent(className: String): ShellStartResult {
        val component = "${TestEnvironment.targetPackage}/$className"
        return execute("am start -n $component")
    }

    fun startView(uri: String, className: String? = null): ShellStartResult {
        val target = className?.let { " -n ${TestEnvironment.targetPackage}/$it" }.orEmpty()
        return execute("am start -a android.intent.action.VIEW -d $uri$target")
    }

    fun startViewWithNoData(className: String): ShellStartResult {
        val component = "${TestEnvironment.targetPackage}/$className"
        return execute("am start -a android.intent.action.VIEW -n $component")
    }

    private fun execute(command: String): ShellStartResult {
        val since = CrashGate.timestampNow()
        val captured = runCatching { Shell.executeWithStderr(command) }
            .getOrElse { "shell capture failed: ${it.javaClass.simpleName}: ${it.message}" }
        val denial = Shell.executeQuietly(
            "logcat -d -v threadtime -T $since ActivityTaskManager:E ActivityManager:E *:S"
        ).lineSequence()
            .filter { line ->
                line.contains(TestEnvironment.targetPackage) &&
                    (line.contains(ShellStartResult.PERMISSION_DENIAL_MARKER, ignoreCase = true) ||
                        line.contains(ShellStartResult.NOT_EXPORTED_MARKER, ignoreCase = true))
            }
            .joinToString("\n")
        return ShellStartResult(command, listOf(captured, denial).filter { it.isNotBlank() }.joinToString("\n"))
    }
}

object ProcessTree {
    fun childrenOfAppProcess(): List<String> {
        val appPid = android.os.Process.myPid().toString()
        return Shell.executeLines("ps -A -o PID,PPID,NAME")
            .drop(1)
            .filter { line -> line.trim().split(Regex("\\s+")).getOrNull(1) == appPid }
    }

    fun commandLinesContaining(fragment: String): List<String> =
        Shell.executeLines("ps -A -o PID,NAME").filter { it.contains(fragment) }
}
