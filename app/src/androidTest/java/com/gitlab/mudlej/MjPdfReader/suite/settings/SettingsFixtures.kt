// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.settings

import com.gitlab.mudlej.MjPdfReader.data.BackupFolder
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryStore
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object SettingsFixtures {

    private val ownedPickerFixtures = linkedSetOf<File>()

    const val NORMAL = "normal.pdf"

    const val SETTINGS_ONLY_BACKUP = "settings-only-backup.json"
    const val FULL_BACKUP = "full-backup.json"
    const val UNKNOWN_KEYS_BACKUP = "unknown-keys-backup.json"
    const val WRONG_TYPES_BACKUP = "wrong-types-backup.json"
    const val BAD_ENUM_BACKUP = "bad-enum-backup.json"
    const val OUT_OF_RANGE_TEXT_MODE_BACKUP = "out-of-range-text-mode-backup.json"
    const val CORRUPT_BACKUP = "corrupt-backup.json"
    const val EMPTY_BACKUP = "empty-backup.json"
    const val NEWER_SCHEMA_BACKUP = "newer-schema-backup.json"
    const val FAKE_DICTIONARY = "fake-dictionary.db"

    val backupFixtureRoot: File = File(Fixtures.externalRoot, "Documents/backup-fixtures")

    fun backupFixture(name: String): File {
        Fixtures.ensureAllFilesAccess()
        val file = File(backupFixtureRoot, name)
        if (!file.isFile) {
            throw AssertionError(
                "The seeded backup fixture ${file.absolutePath} is missing. python3 testing/runner/run.py pushes " +
                    "testing/fixtures/out/backups into ${backupFixtureRoot.absolutePath} before the suite starts."
            )
        }
        return file
    }

    fun pickerBackupFixture(name: String): File =
        Fixtures.pickerFixture(backupFixture(name), ::ownPickerFixture)

    fun deleteOwnedPickerFixtures() {
        val fixtures = synchronized(ownedPickerFixtures) {
            ownedPickerFixtures.toList().also { ownedPickerFixtures.clear() }
        }
        fixtures.forEach { runCatching { Fixtures.delete(it) } }
    }

    private fun ownPickerFixture(file: File) {
        synchronized(ownedPickerFixtures) { ownedPickerFixtures.add(file) }
    }

    fun pdfFixture(name: String = NORMAL): File = Fixtures.requireSeededFixture(name)

    fun dictionaryFile(): File = DictionaryStore.file(TestEnvironment.targetContext)

    fun installFakeDictionary(): File {
        val target = dictionaryFile()
        target.parentFile?.mkdirs()
        target.writeBytes(backupFixture(FAKE_DICTIONARY).readBytes())
        return target
    }

    fun removeDictionary() {
        val target = dictionaryFile()
        target.delete()
        target.parentFile?.listFiles()?.forEach { it.delete() }
    }

    fun dictionaryDirectoryNames(): List<String> =
        dictionaryFile().parentFile?.listFiles()?.map { it.name }.orEmpty()

    fun safetyDirectory(): File = BackupFolder.safetyDir(TestEnvironment.targetContext)

    fun safetySnapshotNames(): List<String> =
        safetyDirectory().listFiles()?.map { it.name }?.sorted().orEmpty()

    fun seedSafetySnapshot(name: String = "mj-pdf-pre-import-20260101-010101.json"): File {
        val directory = safetyDirectory()
        directory.mkdirs()
        val file = File(directory, name)
        file.writeText("{\"schemaVersion\":1,\"exportedAt\":\"2026-01-01T01:01:01Z\",\"settings\":[]}")
        return file
    }

    fun clearSafetyDirectory() {
        safetyDirectory().deleteRecursively()
    }
}
