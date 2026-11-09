// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import com.gitlab.mudlej.MjPdfReader.suite.core.TestIds
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class SaveCleanupRule : TestWatcher() {

    private var prefix: String = ""
    private var publicCopyDirectoryExisted = true

    fun prefix(): String = prefix

    override fun starting(description: Description) {
        prefix = "${TestIds.of(description)}-"
        publicCopyDirectoryExisted = publicCopyDirectory().exists()
        runCatching { SaveFaults.clear() }
        runCatching { Fixtures.stopSimulatingAccess() }
        removeOwnedFiles()
    }

    override fun finished(description: Description) {
        runCatching { SaveFaults.releaseSlowWrite() }
        runCatching { SaveFaults.clear() }
        runCatching { Fixtures.stopSimulatingAccess() }
        runCatching { StubSharerProvider.clear() }
        runCatching { settleOwnedFiles() }
        removeOwnedFiles()
    }

    private fun settleOwnedFiles() {
        SaveFixtures.downloadsFilesWithPrefix(prefix).forEach { file ->
            runCatching { SaveDigest.awaitSettled(file) }
        }
    }

    private fun removeOwnedFiles() {
        runCatching { Fixtures.ensureAllFilesAccess() }
        SaveFixtures.downloadsFilesWithPrefix(prefix).forEach { remove(it) }
        listOf(publicCopyDirectory(), File(TestEnvironment.targetContext.filesDir, "imported"))
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && it.name.startsWith(prefix) }
            .forEach { remove(it) }
        if (!publicCopyDirectoryExisted && publicCopyDirectory().listFiles()?.isEmpty() == true) {
            remove(publicCopyDirectory())
        }
    }

    private fun publicCopyDirectory(): File = File(Fixtures.externalRoot, "Documents/MJ PDF")

    private fun remove(file: File) {
        runCatching { Fixtures.delete(file) }
    }
}
