// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitVisible
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.dragContent
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.pinchCycleOn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class GestureBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    @PerfTest("PERF-28")
    fun framesDroppedDuringAPinchZoomGesture() {
        BenchmarkFixtures.requireFixture(BenchmarkTargets.SMALL)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                startActivityAndWait(BenchmarkTargets.viewIntent(BenchmarkTargets.SMALL))
                device.awaitVisible(BenchmarkTargets.PDF_VIEW)
            },
        ) {
            device.pinchCycleOn(BenchmarkTargets.PDF_VIEW, PINCH_CYCLES)
        }
    }

    @Test
    @PerfTest("PERF-42")
    fun readerDrawsSmoothlyOnAPageWithFiveHundredHighlights() {
        measureHighlightGestureScript(BenchmarkTargets.HIGHLIGHTS_ONE_PAGE)
    }

    @Test
    @PerfTest("PERF-42")
    fun readerDrawsSmoothlyOnAPlainPageAsAControl() {
        measureHighlightGestureScript(BenchmarkTargets.SMALL)
    }

    private fun measureHighlightGestureScript(fixture: String) {
        BenchmarkFixtures.requireFixture(fixture)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                startActivityAndWait(BenchmarkTargets.viewIntent(fixture))
                device.awaitVisible(BenchmarkTargets.PDF_VIEW)
            },
        ) {
            device.pinchCycleOn(BenchmarkTargets.PDF_VIEW, HIGHLIGHT_PINCH_CYCLES)
            device.dragContent(BenchmarkTargets.PDF_VIEW, HIGHLIGHT_DRAGS)
        }
    }

    private companion object {
        const val FRAME_ITERATIONS = 10
        const val PINCH_CYCLES = 3
        const val HIGHLIGHT_PINCH_CYCLES = 4
        const val HIGHLIGHT_DRAGS = 4
    }
}
