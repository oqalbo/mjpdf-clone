// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class SecurityDownloadsRule(private val prefix: String) : TestWatcher() {

    override fun starting(description: Description) {
        removeOwnedFiles()
    }

    override fun finished(description: Description) {
        removeOwnedFiles()
    }

    fun ownedFiles(): List<File> {
        runCatching { Fixtures.ensureAllFilesAccess() }
        return SecurityFixtures.seededRoot.listFiles()
            ?.filter { it.name.startsWith(prefix) }
            .orEmpty()
    }

    private fun removeOwnedFiles() {
        ownedFiles().forEach { file -> runCatching { Fixtures.delete(file) } }
    }
}
