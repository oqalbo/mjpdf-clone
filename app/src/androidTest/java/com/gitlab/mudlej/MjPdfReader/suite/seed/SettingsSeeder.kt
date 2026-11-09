// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.seed

import android.content.SharedPreferences
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

class UnseedableKeyException(message: String) : IllegalArgumentException(message)

object SettingsSeeder {

    const val READING_MODE_ROW_KEY = "readingMode"

    val preferences: SharedPreferences
        get() = TestEnvironment.defaultPreferences

    fun suppressOnboarding() {
        seedAll(
            mapOf(
                Preferences.firstInstallKey to false,
                Preferences.showFeaturesDialogKey to false,
                Preferences.showExitFullscreenTipKey to false,
                Preferences.lastSeenVersionCodeKey to BuildConfig.VERSION_CODE,
            )
        )
    }

    fun seed(key: String, value: Any) = seedAll(mapOf(key to value))

    fun seedAll(values: Map<String, Any>) {
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            rejectUnseedableKey(key)
            requireDeclaredKind(key, value)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                else -> throw UnseedableKeyException(
                    "Preference $key cannot be seeded with a ${value::class.java.name}"
                )
            }
        }
        editor.apply()
    }

    fun seedReadingMode(singlePage: Boolean, horizontalScroll: Boolean, dualPage: Boolean) {
        seedAll(
            mapOf(
                Preferences.singlePageModeKey to singlePage,
                Preferences.horizontalScrollKey to horizontalScroll,
                Preferences.dualPageModeKey to dualPage,
            )
        )
    }

    fun remove(vararg keys: String) {
        val editor = preferences.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    fun declaredKindOf(key: String): String? = Preferences.backupSettingKinds[key]

    private fun rejectUnseedableKey(key: String) {
        if (key == READING_MODE_ROW_KEY) {
            throw UnseedableKeyException(
                "$READING_MODE_ROW_KEY is a preference row key that is never stored. " +
                    "Seed singlePageMode, horizontalScroll and dualPageMode together through seedReadingMode instead."
            )
        }
    }

    private fun requireDeclaredKind(key: String, value: Any) {
        val declared = declaredKindOf(key) ?: return
        val actual = kindOf(value)
        if (declared != actual) {
            throw UnseedableKeyException(
                "Preference $key is declared as $declared in Preferences.backupSettingKinds but was seeded as $actual. " +
                    "A wrongly typed key is deleted on read and silently falls back to its default."
            )
        }
    }

    private fun kindOf(value: Any): String = when (value) {
        is Boolean -> Preferences.kindBoolean
        is Int -> Preferences.kindInt
        is Long -> Preferences.kindLong
        is Float -> Preferences.kindFloat
        is String -> Preferences.kindString
        is Set<*> -> Preferences.kindStringSet
        else -> value::class.java.name
    }
}
