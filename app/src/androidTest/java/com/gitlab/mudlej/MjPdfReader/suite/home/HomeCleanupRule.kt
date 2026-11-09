// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class HomeCleanupRule : TestWatcher() {

    override fun finished(description: Description) {
        runCatching { Fixtures.ensureAllFilesAccess() }
        runCatching { HomeBaselines.sealedDirectory.deleteRecursively() }
        runCatching { Covers.directory().deleteRecursively() }
    }
}
