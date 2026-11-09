// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitVisible
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    @PerfTest("PERF-01")
    fun coldStartOfHomeNoCompilation() {
        measureHomeStartup(StartupMode.COLD, BenchmarkCompilation.WORST_CASE_NEVER_COMPILED)
    }

    @Test
    @PerfTest("PERF-01")
    fun coldStartOfHomePartialCompilation() {
        measureHomeStartup(StartupMode.COLD, BenchmarkCompilation.PARTIALLY_COMPILED_FROM_A_PROFILE)
    }

    @Test
    @PerfTest("PERF-02")
    fun warmStartOfHome() {
        measureHomeStartup(StartupMode.WARM, BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS)
    }

    @Test
    @PerfTest("PERF-03")
    fun hotStartOfHome() {
        measureHomeStartup(StartupMode.HOT, BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS)
    }

    @Test
    @Ignore("Needs a prebuilt app-db.db pushed to the device before the benchmark runs")
    @PerfTest("PERF-04")
    fun coldStartOfHomeWithALargeLibrary() {
        BenchmarkFixtures.requirePerfLibrary()
        BenchmarkFixtures.requireSeededLibraryDatabase()
        measureHomeStartup(StartupMode.COLD, BenchmarkCompilation.MATCHES_THE_PERF_01_COLD_BASELINE)
    }

    @Test
    @PerfTest("PERF-05")
    fun coldStartIntoTheReaderViaAViewIntent() {
        BenchmarkFixtures.requireFixture(BenchmarkTargets.SMALL)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
        ) {
            startActivityAndWait(BenchmarkTargets.viewIntent(BenchmarkTargets.SMALL))
            device.awaitVisible(BenchmarkTargets.PDF_VIEW)
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    @PerfTest("PERF-06")
    fun applicationOnCreateDoesNotDominateStartup() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                TraceSectionMetric(APP_ON_CREATE_SECTION, TraceSectionMetric.Mode.First),
            ),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
        ) {
            startActivityAndWait(BenchmarkTargets.launcherIntent())
            device.awaitVisible(BenchmarkTargets.HOME_TABS)
        }
    }

    private fun measureHomeStartup(startupMode: StartupMode, compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = STARTUP_ITERATIONS,
        ) {
            startActivityAndWait(BenchmarkTargets.launcherIntent())
            device.awaitVisible(BenchmarkTargets.HOME_TABS)
        }
    }

    private companion object {
        const val STARTUP_ITERATIONS = 10

        const val APP_ON_CREATE_SECTION = "App.onCreate"
    }
}
