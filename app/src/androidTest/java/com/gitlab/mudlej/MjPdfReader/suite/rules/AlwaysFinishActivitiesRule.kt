// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import org.junit.rules.ExternalResource

class AlwaysFinishActivitiesRule : ExternalResource() {

    private var previous = OFF

    override fun before() {
        previous = read()
    }

    fun enable() {
        write(ON)
        Await.until("the always finish activities setting reads back as on", Await.SHORT_TIMEOUT_MS) { read() == ON }
    }

    override fun after() {
        write(previous)
    }

    private fun read(): String =
        Shell.executeQuietly("settings get global $SETTING").trim().let { if (it == "null" || it.isEmpty()) OFF else it }

    private fun write(value: String) {
        Shell.executeQuietly("settings put global $SETTING $value")
    }

    private companion object {
        const val SETTING = "always_finish_activities"
        const val ON = "1"
        const val OFF = "0"
    }
}
