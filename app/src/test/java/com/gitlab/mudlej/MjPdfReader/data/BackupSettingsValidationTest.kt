// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSettingsValidationTest {

    @Test
    fun unknownKeyIsSkippedAndNeverWritten() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(BackupSetting("someKeyThatDoesNotExist", Preferences.kindBoolean, "true")),
        )

        assertEquals(0, puts.size)
        assertEquals(1, skipped)
    }

    @Test
    fun knownKeyWithWrongKindIsSkipped() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(BackupSetting(Preferences.highQualityKey, Preferences.kindString, "true")),
        )

        assertEquals(0, puts.size)
        assertEquals(1, skipped)
    }

    @Test
    fun knownKeyWithMatchingKindIsWritten() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(BackupSetting(Preferences.highQualityKey, Preferences.kindBoolean, "true")),
        )

        assertEquals(0, skipped)
        assertEquals(mapOf<String, Any?>(Preferences.highQualityKey to true), apply(puts))
    }

    @Test
    fun excludedKeyIsDroppedWithoutCountingAsSkipped() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(BackupSetting(Preferences.firstInstallKey, Preferences.kindBoolean, "true")),
        )

        assertEquals(0, puts.size)
        assertEquals(0, skipped)
    }

    @Test
    fun textModeSettingsSurviveImport() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(
                BackupSetting(Preferences.textModeFontSizeKey, Preferences.kindFloat, "24.0"),
                BackupSetting(Preferences.textModeLineSpacingKey, Preferences.kindFloat, "1.5"),
                BackupSetting(Preferences.textModeHorizontalMarginKey, Preferences.kindInt, "24"),
                BackupSetting(Preferences.textModeThemeKey, Preferences.kindString, "SEPIA"),
                BackupSetting(Preferences.textModeFontFamilyKey, Preferences.kindString, "SERIF"),
                BackupSetting(Preferences.textModeReadableLineLengthKey, Preferences.kindBoolean, "false"),
            ),
        )

        assertEquals(0, skipped)
        assertEquals(
            mapOf<String, Any?>(
                Preferences.textModeFontSizeKey to 24.0f,
                Preferences.textModeLineSpacingKey to 1.5f,
                Preferences.textModeHorizontalMarginKey to 24,
                Preferences.textModeThemeKey to "SEPIA",
                Preferences.textModeFontFamilyKey to "SERIF",
                Preferences.textModeReadableLineLengthKey to false,
            ),
            apply(puts),
        )
    }

    @Test
    fun textModeKindsMatchWhatTheSheetWrites() {
        assertEquals(Preferences.kindFloat, Preferences.backupSettingKinds[Preferences.textModeFontSizeKey])
        assertEquals(Preferences.kindFloat, Preferences.backupSettingKinds[Preferences.textModeLineSpacingKey])
        assertEquals(Preferences.kindInt, Preferences.backupSettingKinds[Preferences.textModeHorizontalMarginKey])
        assertEquals(Preferences.kindString, Preferences.backupSettingKinds[Preferences.textModeThemeKey])
        assertEquals(Preferences.kindString, Preferences.backupSettingKinds[Preferences.textModeFontFamilyKey])
        assertEquals(
            Preferences.kindBoolean,
            Preferences.backupSettingKinds[Preferences.textModeReadableLineLengthKey],
        )
    }

    @Test
    fun textModeThemeOutsideItsEnumDomainIsSkipped() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(BackupSetting(Preferences.textModeThemeKey, Preferences.kindString, "NEON")),
        )

        assertEquals(0, puts.size)
        assertEquals(1, skipped)
    }

    @Test
    fun textModeFontFamilyOutsideItsEnumDomainIsSkipped() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(BackupSetting(Preferences.textModeFontFamilyKey, Preferences.kindString, "COMIC")),
        )

        assertEquals(0, puts.size)
        assertEquals(1, skipped)
    }

    @Test
    fun mixedBackupKeepsOnlyTheValidEntries() {
        val (puts, skipped) = BackupManager.validateSettings(
            listOf(
                BackupSetting(Preferences.highQualityKey, Preferences.kindBoolean, "true"),
                BackupSetting("injectedKey", Preferences.kindString, "anything"),
                BackupSetting(Preferences.hideDelayKey, Preferences.kindString, "3000"),
                BackupSetting(Preferences.textModeFontSizeKey, Preferences.kindFloat, "4000.0"),
                BackupSetting(Preferences.homeTabKey, Preferences.kindString, "NOT_A_TAB"),
                BackupSetting(Preferences.scrollSpeedKey, Preferences.kindInt, "notANumber"),
                BackupSetting(null, Preferences.kindBoolean, "true"),
            ),
        )

        assertEquals(4, skipped)
        assertEquals(
            mapOf<String, Any?>(
                Preferences.highQualityKey to true,
                Preferences.textModeFontSizeKey to 4000.0f,
            ),
            apply(puts),
        )
    }

    @Test
    fun everyEnumDomainKeyIsAlsoDeclaredAsAKind() {
        for (key in Preferences.backupSettingEnumDomains.keys) {
            assertTrue(key, Preferences.backupSettingKinds.containsKey(key))
        }
    }

    private fun apply(puts: List<(SharedPreferences.Editor) -> Unit>): Map<String, Any?> {
        val editor = RecordingEditor()
        puts.forEach { put -> put(editor) }
        return editor.written
    }

    private class RecordingEditor : SharedPreferences.Editor {

        val written = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            written[key.orEmpty()] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            written[key.orEmpty()] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            written[key.orEmpty()] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            written[key.orEmpty()] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            written[key.orEmpty()] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            written[key.orEmpty()] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor = this

        override fun clear(): SharedPreferences.Editor = this

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
