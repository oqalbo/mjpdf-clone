// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.core

import android.app.Activity

object Rotation {

    fun toLandscape(type: Class<out Activity>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Activity =
        recreateThrough(type, timeoutMs) { TestEnvironment.uiDevice.setOrientationLeft() }

    fun toPortrait(type: Class<out Activity>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): Activity =
        recreateThrough(type, timeoutMs) { TestEnvironment.uiDevice.setOrientationNatural() }

    fun reset() {
        SuiteTiming.measure("rotation.reset") {
            TestEnvironment.uiDevice.setOrientationNatural()
            TestEnvironment.uiDevice.unfreezeRotation()
            TestEnvironment.uiDevice.waitForIdle()
        }
    }

    private fun recreateThrough(
        type: Class<out Activity>,
        timeoutMs: Long,
        turn: () -> Unit,
    ): Activity {
        val before = TestEnvironment.resumedActivityOfType(type)
        turn()
        TestEnvironment.uiDevice.waitForIdle()
        val after = Await.untilNotNull("${type.simpleName} is recreated and resumed after the turn", timeoutMs) {
            TestEnvironment.resumedActivityOfType(type)?.takeIf { before == null || it !== before }
        }
        TestEnvironment.waitForIdleSync()
        return after
    }
}
