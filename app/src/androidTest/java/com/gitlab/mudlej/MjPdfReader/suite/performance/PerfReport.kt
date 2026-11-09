// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.performance

import android.util.Log
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifacts
import java.io.File

object PerfReport {

    fun record(testId: String, metric: String, value: Any, budget: String) {
        write(testId, "test=$testId;metric=$metric;value=$value;budget=$budget")
    }

    private fun write(testId: String, line: String) {
        Log.i(TAG, line)
        runCatching {
            Fixtures.ensureAllFilesAccess()
            val directory = File(FailureArtifacts.ROOT_PATH, REPORT_DIRECTORY).apply { mkdirs() }
            File(directory, "$testId.txt").appendText(line + "\n")
        }
    }

    private const val TAG = "MjPdfPerfTest"
    private const val REPORT_DIRECTORY = "performance"
}
