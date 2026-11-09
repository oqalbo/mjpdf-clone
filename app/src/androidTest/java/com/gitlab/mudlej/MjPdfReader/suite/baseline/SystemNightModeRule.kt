// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.content.res.Configuration
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.rules.ExternalResource

class SystemNightModeRule : ExternalResource() {

    private var enabledByTest = false

    fun enable() {
        enabledByTest = true
        Shell.executeQuietly("cmd uimode night yes")
        Await.until("the system reports night mode on", Await.LONG_TIMEOUT_MS) { isNightModeOn() }
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun isNightModeOn(): Boolean {
        val mode = TestEnvironment.targetContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun requireNightModeOff() {
        if (!isNightModeOn()) return
        throw BaselineGateFailure(
            "The emulator is in night mode and this test's golden was recorded with it off. Night mode " +
                "survives the package data clear, so a previous test that failed to restore it changes what " +
                "this one renders. Restore it with \"cmd uimode night no\" before trusting any result here."
        )
    }

    override fun after() {
        if (!enabledByTest) return
        Shell.executeQuietly("cmd uimode night no")
        runCatching {
            Await.until("the system reports night mode off again", Await.LONG_TIMEOUT_MS) { !isNightModeOn() }
        }
    }
}
