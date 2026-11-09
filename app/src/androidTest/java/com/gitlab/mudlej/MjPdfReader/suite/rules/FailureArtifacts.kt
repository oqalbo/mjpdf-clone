// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object FailureArtifacts {

    const val ROOT_PATH = "/sdcard/Documents/mjpdf-test-artifacts"

    const val ALTERNATE_ROOT_PATH = "/storage/emulated/0/Documents/mjpdf-test-artifacts"

    const val MEDIA_RELATIVE_PATH = "Documents/mjpdf-test-artifacts"

    const val SCREENSHOT_FILE = "screenshot.png"
    const val HIERARCHY_FILE = "window-hierarchy.xml"
    const val LOGCAT_FILE = "logcat.txt"
    const val READER_STATE_FILE = "reader-state.txt"
    const val FAILURE_FILE = "failure.txt"
    const val SHARED_STORAGE_FILE = "shared-storage-diff.txt"

    fun root(): File = File(ROOT_PATH)

    fun directoryFor(testId: String): File {
        Fixtures.ensureAllFilesAccess()
        val directory = File(root(), testId)
        directory.mkdirs()
        return directory
    }

    fun write(testId: String, fileName: String, content: String): File {
        val target = File(directoryFor(testId), fileName)
        target.writeText(content)
        return target
    }
}
