// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.system.Os
import java.io.File

object OpenDescriptors {

    private val root = File("/proc/self/fd")

    fun countPointingAt(target: File): Int = countPointingAt(target.absolutePath)

    fun countPointingAt(path: String): Int =
        root.listFiles()?.count { entry -> resolve(entry) == path } ?: 0

    private fun resolve(entry: File): String? =
        runCatching { Os.readlink(entry.absolutePath) }.getOrNull()
}
