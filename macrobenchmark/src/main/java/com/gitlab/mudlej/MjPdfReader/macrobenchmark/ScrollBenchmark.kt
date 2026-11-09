// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitStableChildCount
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitVisible
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.flingContent
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.turnPagesBackward
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.turnPagesForward
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    @PerfTest("PERF-25")
    fun scrollingAContinuousDocumentStaysWithinAJankBudget() {
        measureFlings(BenchmarkTargets.PAGES_500, DOCUMENT_FLINGS)
    }

    @Test
    @PerfTest("PERF-26")
    fun scrollingAScannedDocumentStaysWithinAJankBudget() {
        measureFlings(BenchmarkTargets.SCANNED_40, DOCUMENT_FLINGS)
    }

    @Test
    @PerfTest("PERF-45")
    fun textModeScrollsSmoothlyThroughALargeDocument() {
        BenchmarkFixtures.requireFixture(BenchmarkTargets.PAGES_500)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                startActivityAndWait(BenchmarkTargets.textModeIntent(BenchmarkTargets.PAGES_500))
                device.awaitStableChildCount(BenchmarkTargets.TEXT_PAGES)
            },
        ) {
            device.flingContent(BenchmarkTargets.TEXT_PAGES, TEXT_MODE_FLINGS)
        }
    }

    @Test
    @PerfTest("PERF-50")
    fun sustainedSessionDegradationRatioFirstSegment() {
        measurePageTurns(warmUpTurns = 0)
    }

    @Test
    @PerfTest("PERF-50")
    fun sustainedSessionDegradationRatioLastSegment() {
        measurePageTurns(warmUpTurns = SUSTAINED_WARM_UP_TURNS)
    }

    private fun measureFlings(fixture: String, flings: Int) {
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
            device.flingContent(BenchmarkTargets.PDF_VIEW, flings)
        }
    }

    private fun measurePageTurns(warmUpTurns: Int) {
        BenchmarkFixtures.requireFixture(BenchmarkTargets.PAGES_500)
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = SUSTAINED_ITERATIONS,
            setupBlock = {
                startActivityAndWait(BenchmarkTargets.viewIntent(BenchmarkTargets.PAGES_500))
                device.awaitVisible(BenchmarkTargets.PDF_VIEW)
                device.turnPagesBackward(
                    BenchmarkTargets.PDF_VIEW,
                    warmUpTurns + SUSTAINED_MEASURED_TURNS + SUSTAINED_RETURN_MARGIN_TURNS,
                )
                if (warmUpTurns > 0) {
                    device.turnPagesForward(BenchmarkTargets.PDF_VIEW, warmUpTurns)
                }
            },
        ) {
            device.turnPagesForward(BenchmarkTargets.PDF_VIEW, SUSTAINED_MEASURED_TURNS)
        }
    }

    private companion object {
        const val FRAME_ITERATIONS = 10
        const val SUSTAINED_ITERATIONS = 5
        const val DOCUMENT_FLINGS = 8
        const val TEXT_MODE_FLINGS = 15
        const val SUSTAINED_MEASURED_TURNS = 20
        const val SUSTAINED_WARM_UP_TURNS = 180
        const val SUSTAINED_RETURN_MARGIN_TURNS = 10
    }
}
