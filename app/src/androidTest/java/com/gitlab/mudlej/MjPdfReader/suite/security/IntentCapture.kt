// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.content.Intent
import androidx.test.espresso.intent.Intents
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import java.io.Closeable

class IntentCapture private constructor() : Closeable {
    private var released = false

    init {
        Intents.init()
        IntentStubs.stubEveryOutgoingIntent()
    }

    fun recorded(): List<Intent> = Intents.getIntents()

    fun outgoing(): List<Intent> = recorded().filter { it.component?.packageName != TestEnvironment.targetPackage }

    fun awaitOutgoingCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<Intent> =
        Await.untilValue(
            "the app fires $expected outgoing intent(s)",
            timeoutMs,
            read = { outgoing() },
        ) { it.size >= expected }

    fun holdOutgoingCount(expected: Int, windowMs: Long = SecurityReader.SETTLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the app stays at $expected outgoing intent(s)",
            windowMs = windowMs,
        ) { outgoing().size == expected }
    }

    fun describe(): String {
        val intents = recorded()
        if (intents.isEmpty()) return "no intent was recorded"
        return intents.joinToString("\n") { intent ->
            "action=${intent.action} data=${intent.data} component=${intent.component} " +
                "package=${intent.`package`} flags=0x${Integer.toHexString(intent.flags)}"
        }
    }

    override fun close() {
        if (released) return
        released = true
        runCatching { Intents.release() }
    }

    companion object {
        fun start(): IntentCapture = IntentCapture()

        fun grantFlagsOf(intent: Intent): Int =
            intent.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
    }
}
