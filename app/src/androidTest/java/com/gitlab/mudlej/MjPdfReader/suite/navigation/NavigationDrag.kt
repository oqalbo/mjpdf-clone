// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import android.graphics.PointF
import android.os.SystemClock
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object NavigationDrag {

    const val SETTLE_MOVES = 12
    const val FRAME_INTERVAL_MS = 16L

    fun settledDrag(from: PointF, to: PointF) {
        val downTime = RawInput.down(from)
        SystemClock.sleep(FRAME_INTERVAL_MS)
        RawInput.pointsAlong(from, to).forEach { point ->
            RawInput.move(downTime, point)
            SystemClock.sleep(FRAME_INTERVAL_MS)
        }
        repeat(SETTLE_MOVES) {
            RawInput.move(downTime, to)
            SystemClock.sleep(FRAME_INTERVAL_MS)
        }
        RawInput.up(downTime, to)
        TestEnvironment.waitForIdleSync()
    }
}
