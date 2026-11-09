// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import org.junit.rules.ExternalResource

class AirplaneModeRule : ExternalResource() {

    private var enabledByTest = false

    fun enable() {
        enabledByTest = true
        Shell.executeQuietly("cmd connectivity airplane-mode enable")
        Await.until("airplane mode reads back as on", Await.DEFAULT_TIMEOUT_MS) { isEnabled() }
    }

    override fun after() {
        if (!enabledByTest) return
        Shell.executeQuietly("cmd connectivity airplane-mode disable")
        runCatching {
            Await.until("airplane mode reads back as off", Await.DEFAULT_TIMEOUT_MS) { !isEnabled() }
        }
    }

    private fun isEnabled(): Boolean =
        Shell.executeQuietly("settings get global airplane_mode_on").trim() == "1"
}
