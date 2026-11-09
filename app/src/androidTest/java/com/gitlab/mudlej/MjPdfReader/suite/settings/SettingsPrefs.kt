// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import android.content.SharedPreferences
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

object SettingsPrefs {

    const val STABLE_ABSENCE_MS = 1_000L

    val store: SharedPreferences
        get() = TestEnvironment.defaultPreferences

    fun preferences(): Preferences = Preferences(store)

    fun contains(key: String): Boolean = store.contains(key)

    fun raw(key: String): Any? = store.all[key]

    fun boolean(key: String): Boolean? = raw(key) as? Boolean

    fun int(key: String): Int? = raw(key) as? Int

    fun long(key: String): Long? = raw(key) as? Long

    fun float(key: String): Float? = raw(key) as? Float

    fun string(key: String): String? = raw(key) as? String

    @Suppress("UNCHECKED_CAST")
    fun stringSet(key: String): Set<String>? = raw(key) as? Set<String>

    fun orderList(key: String): List<String> =
        string(key)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun assertUnset(key: String) {
        assertFalse(
            "$key must not be written, it holds ${raw(key)}",
            contains(key),
        )
    }

    fun assertUnset(vararg keys: String) = keys.forEach(::assertUnset)

    fun assertBoolean(key: String, expected: Boolean) {
        assertEquals("$key must be stored as the boolean $expected", expected, boolean(key))
    }

    fun assertInt(key: String, expected: Int) {
        assertEquals("$key must be stored as the int $expected", expected, int(key))
    }

    fun assertFloat(key: String, expected: Float) {
        assertEquals("$key must be stored as the float $expected", expected, float(key) ?: Float.NaN, 0.0001f)
    }

    fun assertString(key: String, expected: String) {
        assertEquals("$key must be stored as the string $expected", expected, string(key))
    }

    fun assertStringSet(key: String, expected: Set<String>) {
        assertEquals("$key must be stored as the string set $expected", expected, stringSet(key))
    }

    fun assertDeclaredKind(key: String) {
        val declared = Preferences.backupSettingKinds[key] ?: return
        val actual = kindOf(raw(key))
        assertEquals(
            "$key is declared as $declared in Preferences.backupSettingKinds but reads back as $actual",
            declared,
            actual,
        )
    }

    fun awaitBoolean(key: String, expected: Boolean, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue("$key reads the boolean $expected", timeoutMs, read = { raw(key) }) { it == expected }
    }

    fun awaitInt(key: String, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue("$key reads the int $expected", timeoutMs, read = { raw(key) }) { it == expected }
    }

    fun awaitFloat(key: String, expected: Float, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue("$key reads the float $expected", timeoutMs, read = { raw(key) }) {
            it is Float && kotlin.math.abs(it - expected) < 0.0001f
        }
    }

    fun awaitString(key: String, expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue("$key reads the string $expected", timeoutMs, read = { raw(key) }) { it == expected }
    }

    fun awaitStringSet(key: String, expected: Set<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue("$key reads the string set $expected", timeoutMs, read = { stringSet(key) }) { it == expected }
    }

    fun awaitPresent(key: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("$key is present in the preference store", timeoutMs) { contains(key) }
    }

    fun awaitAbsent(key: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("$key is gone from the preference store", timeoutMs) { !contains(key) }
    }

    fun assertStaysUnset(key: String, windowMs: Long = STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "$key stays out of the preference store",
            windowMs = windowMs,
        ) { !contains(key) }
    }

    fun assertStays(key: String, expected: Any?, windowMs: Long = STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "$key keeps the value $expected",
            windowMs = windowMs,
        ) { raw(key) == expected }
    }

    fun seedLegacyBoolean(key: String, value: Boolean) {
        store.edit().putBoolean(key, value).apply()
    }

    fun seedLegacyString(key: String, value: String) {
        store.edit().putString(key, value).apply()
    }

    fun remove(vararg keys: String) {
        val editor = store.edit()
        keys.forEach(editor::remove)
        editor.apply()
    }

    private fun kindOf(value: Any?): String = when (value) {
        is Boolean -> Preferences.kindBoolean
        is Int -> Preferences.kindInt
        is Long -> Preferences.kindLong
        is Float -> Preferences.kindFloat
        is String -> Preferences.kindString
        is Set<*> -> Preferences.kindStringSet
        else -> "absent"
    }
}
