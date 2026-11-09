// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import java.io.File

object ScanPrefs {

    fun seedMode(mode: ScanMode) = SettingsSeeder.seed(Preferences.scanModeKey, mode.name)

    fun seedLocations(paths: Set<String>) =
        SettingsSeeder.seed(Preferences.scanLocationsKey, paths)

    fun seedLocations(vararg directories: File) =
        seedLocations(directories.map { it.absolutePath }.toSet())

    fun seedSelected(vararg directories: File) {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.scanModeKey to ScanMode.SELECTED_LOCATIONS.name,
                Preferences.scanLocationsKey to directories.map { it.absolutePath }.toSet(),
            )
        )
    }

    fun seedWholeDevice() = seedMode(ScanMode.WHOLE_DEVICE)

    fun seedHistoryEnabled(enabled: Boolean) =
        SettingsSeeder.seed(Preferences.historyEnabledKey, enabled)

    fun seedFolderFlat(flat: Boolean) = SettingsSeeder.seed(Preferences.homeFolderFlatKey, flat)

    fun mode(): String? = string(Preferences.scanModeKey)

    fun locations(): Set<String> = stringSet(Preferences.scanLocationsKey).orEmpty()

    fun modeIs(expected: ScanMode): Boolean = mode() == expected.name

    fun awaitMode(expected: ScanMode, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference ${Preferences.scanModeKey} reads ${expected.name}",
            timeoutMs,
            read = { mode() },
        ) { it == expected.name }
    }

    fun awaitLocations(expected: Set<String>, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "preference ${Preferences.scanLocationsKey} holds exactly $expected",
            timeoutMs,
            read = { stringSet(Preferences.scanLocationsKey) },
        ) { it.orEmpty() == expected }
    }

    fun awaitLocations(vararg directories: File, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) =
        awaitLocations(directories.map { it.absolutePath }.toSet(), timeoutMs)

    fun assertModeUnconfigured(message: String) {
        val stored = mode()
        if (stored != null && stored != ScanMode.NOT_CONFIGURED.name) {
            throw AssertionError(
                "$message: preference ${Preferences.scanModeKey} reads \"$stored\""
            )
        }
    }

    fun assertLocationsEmpty(message: String) {
        val stored = locations()
        if (stored.isNotEmpty()) {
            throw AssertionError("$message: preference ${Preferences.scanLocationsKey} holds $stored")
        }
    }

    private fun string(key: String): String? = SettingsSeeder.preferences.all[key] as? String

    private fun stringSet(key: String): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return SettingsSeeder.preferences.all[key] as? Set<String>
    }
}
