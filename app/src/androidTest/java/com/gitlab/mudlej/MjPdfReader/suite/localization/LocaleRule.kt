// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization
import android.os.Build

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.robot.LocaleRobot
import org.junit.rules.ExternalResource

class LocaleRule : ExternalResource() {

    private val robot = LocaleRobot()
    private var applied: String? = null

    fun robot(): LocaleRobot = robot

    fun appliedTag(): String? = applied

    fun set(tag: String): LocaleRule {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            robot.setAppLocaleByShell(tag, Await.LONG_TIMEOUT_MS)
        } else {
            robot.setApplicationLocalesLive(tag)
        }
        applied = tag
        return this
    }

    fun setLive(tag: String): LocaleRule {
        robot.setApplicationLocalesLive(tag)
        applied = tag
        return this
    }

    fun currentLanguageInAppProcess(): String = robot.defaultLocaleInAppProcess().language

    override fun before() {
        applied = null
        clear()
    }

    override fun after() {
        clear()
        applied = null
    }

    private fun clear() {
        robot.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { robot.awaitNoPerAppLocale(Await.SHORT_TIMEOUT_MS) }
        }
    }
}
