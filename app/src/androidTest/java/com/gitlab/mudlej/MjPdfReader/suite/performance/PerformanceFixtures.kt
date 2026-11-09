// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.performance

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object PerformanceFixtures {

    const val HIGHLIGHTS_500 = "perf-500-highlights.pdf"

    fun place(name: String, targetDirectory: File): File = Fixtures.copyDeviceFixture(name, targetDirectory)
}
