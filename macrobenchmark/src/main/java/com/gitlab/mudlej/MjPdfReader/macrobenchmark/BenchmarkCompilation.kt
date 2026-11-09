// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.benchmark.macro.CompilationMode

object BenchmarkCompilation {

    val AS_A_SHIPPED_APP_RUNS: CompilationMode = CompilationMode.DEFAULT

    val WORST_CASE_NEVER_COMPILED: CompilationMode = CompilationMode.None()

    val PARTIALLY_COMPILED_FROM_A_PROFILE: CompilationMode = CompilationMode.Partial()

    val MATCHES_THE_PERF_01_COLD_BASELINE: CompilationMode = WORST_CASE_NEVER_COMPILED
}
