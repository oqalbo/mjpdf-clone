// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.os.Build
import android.os.ParcelFileDescriptor
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File
import java.util.concurrent.Executors

object Shell {

    fun execute(command: String): String {
        val descriptor = TestEnvironment.instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    fun executeLines(command: String): List<String> =
        execute(command).lineSequence().filter { it.isNotBlank() }.toList()

    fun executeQuietly(command: String): String = runCatching { execute(command) }.getOrDefault("")

    fun executeWithStderr(command: String): String {
        if (Build.VERSION.SDK_INT >= 34) {
            val descriptors = TestEnvironment.instrumentation.uiAutomation.executeShellCommandRwe(command)
            descriptors[1].close()
            val readers = Executors.newFixedThreadPool(2)
            return try {
                val stdout = readers.submit<ByteArray> {
                    ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { it.readBytes() }
                }
                val stderr = readers.submit<ByteArray> {
                    ParcelFileDescriptor.AutoCloseInputStream(descriptors[2]).use { it.readBytes() }
                }
                (stdout.get() + stderr.get()).toString(Charsets.UTF_8)
            } finally {
                runCatching { descriptors[0].close() }
                runCatching { descriptors[2].close() }
                readers.shutdownNow()
            }
        }

        val directory = Fixtures.perTestRoot
        check(directory.isDirectory || directory.mkdirs()) {
            "Could not create shell-readable test directory ${directory.absolutePath}"
        }
        val script = File(directory, "mjpdf-shell-${android.os.Process.myPid()}.sh")
        return try {
            script.writeText("#!/system/bin/sh\n$command 2>&1\n")
            execute("sh ${script.absolutePath}")
        } finally {
            script.delete()
        }
    }
}
