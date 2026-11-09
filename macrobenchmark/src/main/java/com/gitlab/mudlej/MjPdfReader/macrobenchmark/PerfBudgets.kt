// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.macrobenchmark

object PerfBudgets {
    data class Startup(val id: String, val medianMs: Long)

    data class Frames(
        val id: String,
        val p50DurationMs: Double,
        val p99DurationMs: Double,
        val p99OverrunMs: Double,
    )

    val COLD_HOME = Startup("PERF-01", 1200)
    val WARM_HOME = Startup("PERF-02", 500)
    val HOT_HOME = Startup("PERF-03", 220)
    val COLD_HOME_LARGE_LIBRARY = Startup("PERF-04", 1680)
    val COLD_READER_VIEW_INTENT = Startup("PERF-05", 1600)
    val APP_ON_CREATE = Startup("PERF-06", 120)

    val CONTINUOUS_SCROLL = Frames("PERF-25", 8.0, 32.0, 0.0)
    val SCANNED_SCROLL = Frames("PERF-26", 10.0, 48.0, 4.0)
    val PINCH_ZOOM = Frames("PERF-28", 10.0, 40.0, 2.0)
    val LIBRARY_GRID_SCROLL = Frames("PERF-37", 8.0, 40.0, 2.0)
    val THUMBNAIL_GRID_SCROLL = Frames("PERF-39", 8.0, 45.0, 4.0)
    val FOLDERS_NAVIGATION = Frames("PERF-41", Double.MAX_VALUE, 60.0, 8.0)
    val HIGHLIGHTED_PAGE_DRAW = Frames("PERF-42", 10.0, 40.0, 2.0)
    val TEXT_MODE_SCROLL = Frames("PERF-45", 8.0, 45.0, 4.0)
    val SUSTAINED_SESSION = Frames("PERF-50", 8.0, 32.0, 0.0)

    const val LARGE_LIBRARY_RATIO = 1.4
    const val LARGE_LIBRARY_ABSOLUTE_MS = 350L
    const val WARM_BELOW_COLD_FRACTION = 0.6
    const val HIGHLIGHT_CONTROL_RATIO = 1.5
    const val SUSTAINED_DEGRADATION_RATIO = 1.3
}
