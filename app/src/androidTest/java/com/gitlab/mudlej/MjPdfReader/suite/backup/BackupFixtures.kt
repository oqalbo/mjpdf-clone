// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import java.io.File

object BackupFixtures {
    private val ownedPickerFixtures = linkedSetOf<File>()

    const val DIRECTORY_NAME = "backup-fixtures"

    const val VALID_FULL = "valid-full.json"
    const val VALID_SETTINGS_ONLY = "valid-settings-only.json"
    const val VALID_HISTORY_ONLY = "valid-history-only.json"
    const val VALID_WITH_PASSWORDS = "valid-with-passwords.json"
    const val LEGACY_MINIFIED = "legacy-minified.json"
    const val LEGACY_MINIFIED_H2_ONLY = "legacy-minified-h2-only.json"
    const val SCHEMA_ZERO = "schema-zero.json"
    const val SCHEMA_FUTURE = "schema-future.json"
    const val TRUNCATED = "truncated.json"
    const val EMPTY_SECTIONS = "empty-sections.json"
    const val BAD_SETTINGS = "bad-settings.json"
    const val DIRTY_RECORDS = "dirty-records.json"
    const val MISSING_FILES = "missing-files.json"
    const val NOT_A_BACKUP = "not-a-backup.txt"
    const val OVERSIZED = "oversized.json"

    val VALID_FULL_HASHES = listOf("h21f04dcfbd93581a3dd07bb6517b42606", "h29c89ea4066986994f76c2eef38acada1")

    const val VALID_FULL_SETTINGS_COUNT = 5
    const val VALID_FULL_RECORD_COUNT = 2
    const val VALID_FULL_BOOKMARK_COUNT = 3

    const val PASSWORD_RECORD_HASH = "h29c89ea4066986994f76c2eef38acada1"
    const val SAVED_PASSWORD = "mjpdf-saved-secret"

    const val DIRTY_VALID_RECORD_HASH = "h21f04dcfbd93581a3dd07bb6517b42606"
    const val DIRTY_UNPARSEABLE_DATE_HASH = "h2665e9c4164ee92d79cb07b76f43d9e2b"
    const val DIRTY_NEGATIVE_SORT_PAGE_INDEX = 8

    val MISSING_FILE_HASHES = listOf("h2bd25c2d37efcfe5d206c6a912f36a1d5", "h2d845f01ebac74ee5efe51e22ef7ab3a6")

    val LEGACY_HASHES = listOf("bf69b62d5a5739f7e52890bf5d0b2c69", "b708986a2df62a447d23646cf031a1c5")

    const val LEGACY_BOOKMARK_COUNT = 1

    const val BAD_SETTINGS_SKIPPED = 3
    val BAD_SETTINGS_APPLIED_KEYS = listOf("screenOn", "scrollSpeed")
    val BAD_SETTINGS_SKIPPED_KEYS = listOf("notARealSetting", "horizontalScroll", "tapToTurnPages")

    val directory: File = File(Fixtures.externalRoot, "Documents/$DIRECTORY_NAME")

    fun file(name: String): File = File(directory, name)

    fun require(name: String): File {
        val target = file(name)
        if (!target.isFile) {
            throw AssertionError(
                "The backup fixture ${target.absolutePath} is missing. python3 testing/runner/run.py pushes " +
                    "testing/fixtures/out/backups into ${directory.absolutePath} before the suite starts, and " +
                    "a connected gradle run performs no seeding at all."
            )
        }
        return target
    }

    fun json(name: String): BackupJson = BackupJson.of(require(name))

    fun open(
        name: String,
        picker: SafPickerRobot = SafPickerRobot(),
        onSelectionDispatched: () -> Unit = {},
    ) {
        openInDirectory(directory, name, picker, onSelectionDispatched)
    }

    fun openFrom(directoryName: String, name: String, picker: SafPickerRobot = SafPickerRobot()) {
        openInDirectory(File(BackupTree.documentsRoot, directoryName), name, picker)
    }

    fun openInTree(tree: File, name: String, picker: SafPickerRobot = SafPickerRobot()) {
        openInDirectory(BackupTree.folderIn(tree), name, picker)
    }

    fun openInDirectory(
        directory: File,
        name: String,
        picker: SafPickerRobot = SafPickerRobot(),
        onSelectionDispatched: () -> Unit = {},
    ) {
        val target = File(directory, name)
        val pickerFixture = Fixtures.pickerFixture(target, ::ownPickerFixture)
        BackupFilePicker.choose(picker, pickerFixture.name, onSelectionDispatched)
    }

    fun deleteOwnedPickerFixtures() {
        val fixtures = synchronized(ownedPickerFixtures) {
            ownedPickerFixtures.toList().also { ownedPickerFixtures.clear() }
        }
        fixtures.forEach { runCatching { Fixtures.delete(it) } }
    }

    private fun ownPickerFixture(file: File) {
        synchronized(ownedPickerFixtures) { ownedPickerFixtures.add(file) }
    }

    fun generateOversized(): File {
        val target = file(OVERSIZED)
        if (target.isFile && target.length() > SIZE_CEILING_BYTES) return target
        target.parentFile?.mkdirs()
        target.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("{\"schemaVersion\":1,\"exportedAt\":\"2026-01-01T00:00:00Z\",\"pad\":\"")
            val block = "a".repeat(PAD_BLOCK_BYTES)
            var written = 0L
            while (written < PAD_BYTES) {
                writer.write(block)
                written += PAD_BLOCK_BYTES
            }
            writer.write("\"}")
        }
        Await.until("the oversized fixture is written", Await.VERY_LONG_TIMEOUT_MS) {
            target.length() > SIZE_CEILING_BYTES
        }
        return target
    }

    fun deleteOversized() {
        runCatching { file(OVERSIZED).delete() }
    }

    fun writeInto(directory: File, name: String, content: String): File {
        directory.mkdirs()
        val target = File(directory, name)
        Fixtures.pushBytes(content.toByteArray(Charsets.UTF_8), target)
        return target
    }

    const val SIZE_CEILING_BYTES = 32L * 1024L * 1024L
    private const val PAD_BYTES = 33L * 1024L * 1024L
    private const val PAD_BLOCK_BYTES = 64 * 1024
}
