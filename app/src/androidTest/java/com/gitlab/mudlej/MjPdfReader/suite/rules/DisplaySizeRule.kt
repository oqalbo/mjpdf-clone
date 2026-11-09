// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import org.junit.rules.ExternalResource

class DisplaySizeRule : ExternalResource() {

    private var changed = false
    private var priorSize = ""
    private var priorDensity = ""

    fun set(widthPx: Int, heightPx: Int, densityDpi: Int) {
        if (!changed) {
            priorSize = override()
            priorDensity = densityOverride()
            changed = true
        }
        Shell.execute("wm density $densityDpi")
        Shell.execute("wm size ${widthPx}x$heightPx")
        Await.until("the display reports ${widthPx}x$heightPx", Await.LONG_TIMEOUT_MS) {
            override().contains("${widthPx}x$heightPx")
        }
        Await.until("the display reports $densityDpi dpi", Await.LONG_TIMEOUT_MS) {
            densityOverride().contains(densityDpi.toString())
        }
    }

    fun physicalSize(): String = Shell.execute("wm size").trim()

    override fun after() {
        if (!changed) return
        runCatching { Shell.execute("wm size ${priorSize.ifEmpty { "reset" }}") }
        runCatching { Shell.execute("wm density ${priorDensity.ifEmpty { "reset" }}") }
        changed = false
        val restored = runCatching {
            Await.until("the incoming display overrides are restored", Await.LONG_TIMEOUT_MS) {
                override() == priorSize && densityOverride() == priorDensity
            }
        }.isSuccess
        if (!restored) {
            throw ConditionTimeoutException(
                "Expected display overrides size=\"$priorSize\", density=\"$priorDensity\"; " +
                    "observed size=\"${override()}\", density=\"${densityOverride()}\"."
            )
        }
    }

    private fun override(): String = valueAfter(physicalSize(), OVERRIDE_SIZE_PREFIX)

    private fun densityOverride(): String = valueAfter(physicalDensity(), OVERRIDE_DENSITY_PREFIX)

    private fun physicalDensity(): String = Shell.execute("wm density").trim()

    private fun valueAfter(output: String, prefix: String): String =
        output.lineSequence()
            .firstOrNull { it.trim().startsWith(prefix) }
            ?.trim()
            ?.removePrefix(prefix)
            ?.trim()
            .orEmpty()

    private companion object {
        const val OVERRIDE_SIZE_PREFIX = "Override size:"
        const val OVERRIDE_DENSITY_PREFIX = "Override density:"
    }
}
