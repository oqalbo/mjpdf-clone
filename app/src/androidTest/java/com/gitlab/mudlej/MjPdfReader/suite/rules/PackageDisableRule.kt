// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import android.content.Intent
import android.content.pm.PackageManager
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class PackageDisableRule : TestWatcher() {

    private val disabled = LinkedHashSet<String>()

    fun handlerPackagesOf(intent: Intent): Set<String> {
        val fromPackageManager = TestEnvironment.targetContext.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
        return (fromPackageManager + fromShell(intent))
            .filterTo(LinkedHashSet()) { it.isNotBlank() && it != TestEnvironment.targetPackage }
    }

    fun disableHandlersOf(intent: Intent): Set<String> {
        val packages = handlerPackagesOf(intent)
        packages.forEach { disable(it) }
        if (packages.isNotEmpty()) {
            Await.until("no package answers $intent any more", Await.DEFAULT_TIMEOUT_MS) {
                handlerPackagesOf(intent).isEmpty()
            }
        }
        return packages
    }

    override fun finished(description: Description) {
        disabled.forEach { Shell.executeQuietly("pm enable $it") }
        disabled.clear()
    }

    private fun disable(packageName: String) {
        disabled.add(packageName)
        Shell.execute("pm disable-user --user 0 $packageName")
    }

    private fun fromShell(intent: Intent): List<String> {
        val action = intent.action ?: return emptyList()
        val data = intent.data?.toString()
        val type = intent.type
        val command = buildString {
            append("cmd package query-activities --brief -a ")
            append(action)
            if (data != null) append(" -d ").append(data)
            if (type != null) append(" -t ").append(type)
        }
        return Shell.executeQuietly(command)
            .lineSequence()
            .mapNotNull { line ->
                COMPONENT_LINE.find(line)?.groupValues?.get(1)
                    ?: PACKAGE_NAME_LINE.find(line)?.groupValues?.get(1)
            }
            .toList()
    }

    private companion object {
        val COMPONENT_LINE = Regex("""^\s*([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+\s*$""")
        val PACKAGE_NAME_LINE = Regex("""^\s*packageName=([A-Za-z0-9_.]+)\s*$""")
    }
}
