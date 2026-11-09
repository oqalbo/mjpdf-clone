// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.SuiteTiming
import com.gitlab.mudlej.MjPdfReader.suite.core.TestIds
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.SharedStorage
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.SharedStorageDiff
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.SharedStorageSnapshot
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

class SharedStorageRule(
    private val roots: List<String> = SharedStorage.defaultRoots,
    private val depth: Int = SharedStorage.DEFAULT_DEPTH,
) : TestWatcher() {

    private var before: SharedStorageSnapshot? = null
    private var testId: String = ""

    override fun starting(description: Description) {
        testId = TestIds.of(description)
        Fixtures.ensureAllFilesAccess()
        before = runCatching { SuiteTiming.measure("storage.snapshot") { SharedStorage.snapshot(roots, depth) } }.getOrNull()
    }

    override fun failed(error: Throwable, description: Description) {
        val diff = computeDiff() ?: return
        runCatching {
            File(FailureArtifacts.directoryFor(testId), FailureArtifacts.SHARED_STORAGE_FILE)
                .writeText(diff.describe())
        }
    }

    private fun computeDiff(): SharedStorageDiff? {
        val start = before ?: return null
        val end = runCatching { SuiteTiming.measure("storage.snapshot") { SharedStorage.snapshot(roots, depth) } }.getOrNull() ?: return null
        return SharedStorage.diff(start, end)
    }
}
