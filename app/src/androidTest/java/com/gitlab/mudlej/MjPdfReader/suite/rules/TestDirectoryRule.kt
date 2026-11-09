// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.TestIds
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class TestDirectoryRule : TestWatcher() {

    private var testId: String = ""
    private var wasUsed = false

    override fun starting(description: Description) {
        testId = TestIds.of(description)
        wasUsed = false
    }

    fun directory(): File {
        wasUsed = true
        return Fixtures.createDirectoryForTest(testId)
    }

    fun fileNamed(name: String): File = File(directory(), name)

    fun expectedPath(): File = Fixtures.directoryForTest(testId)

    override fun finished(description: Description) {
        if (!wasUsed && !Fixtures.directoryForTest(testId).exists()) return
        runCatching { Fixtures.deleteDirectoryForTest(testId) }
    }
}
