// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor

object BenchmarkFixtures {
    const val PERF_LIBRARY_MIN_FILES = 1000

    fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    fun requireFixture(name: String) {
        val path = BenchmarkTargets.path(name)
        val listing = shell("ls $path").trim()
        if (listing.isEmpty() || listing.contains("No such file")) {
            throw AssertionError(
                "The performance fixture $path is missing. python3 testing/runner/run.py pushes " +
                    "testing/fixtures/out into ${BenchmarkTargets.FIXTURE_DIR}, and a plain gradle connected " +
                    "run does not."
            )
        }
    }

    fun requirePerfLibrary() {
        val count = shell("find ${BenchmarkTargets.PERF_LIBRARY_DIR} -name '*.pdf' | wc -l")
            .trim().toIntOrNull() ?: 0
        if (count < PERF_LIBRARY_MIN_FILES) {
            throw AssertionError(
                "The perf-library-100k tree under ${BenchmarkTargets.PERF_LIBRARY_DIR} holds $count files, " +
                    "which is below the $PERF_LIBRARY_MIN_FILES this benchmark needs. The library exists only " +
                    "for the macrobenchmark. Place it with python3 testing/runner/run.py --with-perf-library, " +
                    "which takes about five minutes on a cold device. A later suite run without that flag " +
                    "removes it again, and --sweep-perf-library removes it together with its MediaStore rows."
            )
        }
    }

    fun requireSeededLibraryDatabase() {
        val listing = shell("ls /data/data/${BenchmarkTargets.PACKAGE}/databases/app-db.db").trim()
        if (listing.isEmpty() || listing.contains("No such file") || listing.contains("Permission denied")) {
            throw AssertionError(
                "The seeded library database is not in place, or the shell cannot see it. A macrobenchmark " +
                    "cannot seed Room in process against a non debuggable build, so a prebuilt app-db.db " +
                    "must be pushed to the device before this benchmark runs."
            )
        }
    }
}
