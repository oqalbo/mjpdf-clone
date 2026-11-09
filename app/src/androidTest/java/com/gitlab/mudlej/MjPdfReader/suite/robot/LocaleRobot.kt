// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.robot

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.util.Locale

class LocaleRobot : Robot() {

    override fun awaitShown(timeoutMs: Long) = Unit

    fun appProcessId(): Int? =
        Shell.execute("pidof $targetPackage").trim().split(" ").firstOrNull()?.toIntOrNull()

    fun setAppLocaleByShell(tag: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): LocaleRobot {
        val response = Shell.execute("cmd locale set-app-locales $targetPackage --user 0 --locales $tag")
        if (response.contains(UNKNOWN_PACKAGE_MARKER)) {
            throw ConditionTimeoutException(
                "The system does not know $targetPackage, so the per app locale could not be set. " +
                    "The app must be installed before a locale is applied. Shell said: ${response.trim()}"
            )
        }
        awaitLocaleTagApplied(tag, timeoutMs)
        return this
    }

    fun setApplicationLocalesLive(tag: String): LocaleRobot {
        TestEnvironment.onMainThread {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        return this
    }

    fun currentAppLocales(): String =
        Shell.execute("cmd locale get-app-locales $targetPackage").trim()

    fun reset() {
        runCatching { Shell.execute("cmd locale set-app-locales $targetPackage --user 0 --locales") }
        runCatching {
            TestEnvironment.onMainThread {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }

    fun defaultLocaleInAppProcess(): Locale = TestEnvironment.onMainThread { Locale.getDefault() }

    fun awaitLocaleTagApplied(tag: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the system reports the per app locale $tag",
            timeoutMs = timeoutMs,
            read = { currentAppLocales() },
            predicate = { it.contains(tag, ignoreCase = true) },
        )
    }

    fun awaitNoPerAppLocale(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            condition = "the system reports no per app locale",
            timeoutMs = timeoutMs,
            read = { currentAppLocales() },
            predicate = { it.contains(EMPTY_LOCALE_MARKER) || it.contains(UNKNOWN_PACKAGE_MARKER) },
        )
    }

    companion object {
        const val UNKNOWN_PACKAGE_MARKER = "Unknown package"
        const val EMPTY_LOCALE_MARKER = "[]"

        const val ARABIC_EGYPT = "ar-EG"
        const val RUSSIAN = "ru-RU"
        const val UKRAINIAN = "uk-UA"
        const val GERMAN = "de-DE"
        const val TURKISH = "tr-TR"
        const val HEBREW = "he-IL"
    }
}
