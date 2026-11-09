// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitStableChildCount
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.flingContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ThumbnailGridBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    @PerfTest("PERF-39")
    fun thumbnailGridOpensAndScrollsSmoothlyOnAFiveHundredPageDocument() {
        BenchmarkFixtures.requireFixture(BenchmarkTargets.PAGES_500)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                startActivityAndWait(BenchmarkTargets.goToPageIntent(BenchmarkTargets.PAGES_500))
                device.awaitStableChildCount(BenchmarkTargets.PAGE_GRID)
            },
        ) {
            device.flingContent(BenchmarkTargets.PAGE_GRID, GRID_FLINGS)
        }
    }

    private companion object {
        const val FRAME_ITERATIONS = 10
        const val GRID_FLINGS = 15
    }
}
