// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitStableChildCount
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.awaitVisible
import com.gitlab.mudlej.MjPdfReader.macrobenchmark.BenchmarkGestures.flingContent
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    @Ignore("Needs a prebuilt app-db.db pushed to the device before the benchmark runs")
    @PerfTest("PERF-37")
    fun homeLibraryGridScrollsSmoothlyWithAColdCoverCache() {
        measureLibraryGridScroll(clearCoverCache = true)
    }

    @Test
    @Ignore("Needs a prebuilt app-db.db pushed to the device before the benchmark runs")
    @PerfTest("PERF-37")
    fun homeLibraryGridScrollsSmoothlyWithAWarmCoverCache() {
        measureLibraryGridScroll(clearCoverCache = false)
    }

    @Test
    @Ignore("Needs a prebuilt app-db.db pushed to the device before the benchmark runs")
    @PerfTest("PERF-41")
    fun foldersTabRendersWithinBudgetWithADeepTree() {
        BenchmarkFixtures.requirePerfLibrary()
        BenchmarkFixtures.requireSeededLibraryDatabase()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                startActivityAndWait(BenchmarkTargets.launcherIntent())
                device.awaitVisible(BenchmarkTargets.HOME_TABS)
                device.awaitStableChildCount(BenchmarkTargets.HOME_PAGER)
            },
        ) {
            device.findObject(By.res(BenchmarkTargets.PACKAGE, BenchmarkTargets.HOME_TABS))
                ?.children?.lastOrNull()?.click()
            device.waitForIdle()
            repeat(FOLDER_LEVELS) {
                val entered = device.wait(
                    Until.findObject(By.res(BenchmarkTargets.PACKAGE, BenchmarkTargets.HOME_PAGER)),
                    FOLDER_WAIT_MS,
                )?.children?.firstOrNull()?.children?.firstOrNull()
                entered?.click()
                device.waitForIdle()
            }
        }
    }

    private fun measureLibraryGridScroll(clearCoverCache: Boolean) {
        BenchmarkFixtures.requirePerfLibrary()
        BenchmarkFixtures.requireSeededLibraryDatabase()
        benchmarkRule.measureRepeated(
            packageName = BenchmarkTargets.PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkCompilation.AS_A_SHIPPED_APP_RUNS,
            startupMode = StartupMode.WARM,
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                if (clearCoverCache) {
                    BenchmarkFixtures.shell(
                        "rm -rf /data/data/${BenchmarkTargets.PACKAGE}/cache/covers"
                    )
                }
                startActivityAndWait(BenchmarkTargets.launcherIntent())
                device.awaitVisible(BenchmarkTargets.HOME_TABS)
                device.awaitStableChildCount(BenchmarkTargets.HOME_PAGER)
            },
        ) {
            device.flingContent(BenchmarkTargets.HOME_PAGER, GRID_FLINGS)
        }
    }

    private companion object {
        const val FRAME_ITERATIONS = 10
        const val GRID_FLINGS = 12
        const val FOLDER_LEVELS = 6
        const val FOLDER_WAIT_MS = 5_000L
    }
}
