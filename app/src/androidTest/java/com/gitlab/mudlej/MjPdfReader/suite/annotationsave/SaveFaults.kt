// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object SaveFaults {

    const val RESCUE_DIRECTORY = "annotation-rescue"
    const val RESCUE_KEEP = 2

    fun armFailWrite() = arm(AnnotationTestHooks.FAULT_FAIL_WRITE)

    fun armCorruptWrite() = arm(AnnotationTestHooks.FAULT_CORRUPT_WRITE)

    fun armSlowWrite() = arm(AnnotationTestHooks.FAULT_SLOW_WRITE)

    fun arm(mode: String) {
        AnnotationTestHooks.setSaveFaultMode(TestEnvironment.targetContext, mode)
    }

    fun clear() {
        runCatching { AnnotationTestHooks.clearSaveFaultMode(TestEnvironment.targetContext) }
        runCatching { AnnotationTestHooks.reset() }
    }

    fun awaitSlowWriteParked(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the seeded slow write parks on its gate", timeoutMs) {
            AnnotationTestHooks.isSlowWriteBlocked()
        }
    }

    fun releaseSlowWrite() {
        AnnotationTestHooks.releaseSlowWrite()
    }

    fun rescueDirectory(): File = File(TestEnvironment.targetContext.filesDir, RESCUE_DIRECTORY)

    fun rescueFiles(): List<File> = rescueDirectory().listFiles()?.toList().orEmpty()

    fun awaitRescueFileCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<File> =
        Await.untilValue(
            "the rescue directory holds $expected files",
            timeoutMs,
            read = { rescueFiles() },
        ) { it.size == expected }

    fun assertNoRescueDirectory(windowMs: Long = SaveDestinations.STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no rescue copy is written",
            windowMs = windowMs,
        ) { rescueFiles().isEmpty() }
    }
}
