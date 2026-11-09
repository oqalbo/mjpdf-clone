// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class BackupFolderNamingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var originalLocale: Locale

    private val asciiSnapshotName = Regex("mj-pdf-pre-import-[0-9]{8}-[0-9]{6}\\.json")
    private val asciiAutoBackupName = Regex("mj-pdf-auto-backup-[0-9]{8}-[0-9]{6}\\.json")
    private val asciiManualBackupName = Regex("mj-pdf-backup-[0-9]{8}-[0-9]{6}\\.json")

    private val digitLocales = listOf(
        Locale.forLanguageTag("ar-EG"),
        Locale.forLanguageTag("fa-IR"),
        Locale.forLanguageTag("bn-BD"),
        Locale.forLanguageTag("my-MM"),
        Locale.forLanguageTag("ne-NP"),
        Locale.forLanguageTag("hi-IN"),
        Locale.US,
        Locale.ROOT,
    )

    @Before
    fun captureLocale() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun snapshotNameIsAsciiUnderEveryLocale() {
        for (locale in digitLocales) {
            Locale.setDefault(locale)
            val name = BackupFolder.newSafetySnapshotName()
            assertTrue("$locale produced $name", asciiSnapshotName.matches(name))
        }
    }

    @Test
    fun autoAndManualBackupNamesAreAsciiUnderEveryLocale() {
        for (locale in digitLocales) {
            Locale.setDefault(locale)
            assertTrue(locale.toString(), asciiAutoBackupName.matches(BackupFolder.newAutoBackupFileName()))
            assertTrue(locale.toString(), asciiManualBackupName.matches(BackupFolder.newBackupFileName()))
        }
    }

    @Test
    fun snapshotsWrittenByOlderBuildsAreStillRecognised() {
        val dir = temporaryFolder.newFolder()
        val ascii = write(dir, "mj-pdf-pre-import-20260726-143000.json")
        val arabic = write(dir, "mj-pdf-pre-import-٢٠٢٦٠٧٢٦-١٤٣٠٠٠.json")
        val persian = write(dir, "mj-pdf-pre-import-۲۰۲۶۰۷۲۶-۱۴۳۰۰۰.json")

        val listed = BackupFolder.listSafetySnapshots(dir).map { it.name }.toSet()

        assertEquals(setOf(ascii.name, arabic.name, persian.name), listed)
    }

    @Test
    fun snapshotListingIsNewestFirstByModifiedTimeNotName() {
        val dir = temporaryFolder.newFolder()
        val newest = write(dir, "mj-pdf-pre-import-20260101-000000.json", modified = 3_000_000L)
        val middle = write(dir, "mj-pdf-pre-import-٢٠٢٦٠٧٢٦-١٤٣٠٠٠.json", modified = 2_000_000L)
        val oldest = write(dir, "mj-pdf-pre-import-20991231-235959.json", modified = 1_000_000L)

        val listed = BackupFolder.listSafetySnapshots(dir).map { it.name }

        assertEquals(listOf(newest.name, middle.name, oldest.name), listed)
    }

    @Test
    fun pruneKeepsTheThreeNewestSnapshotsByModifiedTime() {
        val dir = temporaryFolder.newFolder()
        val kept = (1..3).map { write(dir, "mj-pdf-pre-import-2026010$it-000000.json", modified = 9_000_000L + it) }
        val dropped = (4..6).map { write(dir, "mj-pdf-pre-import-2099010$it-000000.json", modified = 1_000_000L + it) }

        BackupFolder.pruneSafetySnapshots(dir)

        for (file in kept) assertTrue("${file.name} should survive", file.exists())
        for (file in dropped) assertFalse("${file.name} should be pruned", file.exists())
    }

    @Test
    fun pruneKeepsUnrecognisedFilesThatAreStillFresh() {
        val dir = temporaryFolder.newFolder()
        val now = System.currentTimeMillis()
        val mystery = write(dir, "mystery.dat", modified = now)
        val stale = write(dir, "forgotten.dat", modified = now - 48L * 60L * 60L * 1000L)
        val scratch = write(dir, "mj-pdf-pre-import-20260726-143000.json.tmp.bin", modified = now)

        BackupFolder.pruneSafetySnapshots(dir)

        assertTrue("a fresh unknown file must not be destroyed", mystery.exists())
        assertFalse("a 48h old unknown file is safe to drop", stale.exists())
        assertFalse("scratch files are always safe to drop", scratch.exists())
    }

    private fun write(dir: File, name: String, modified: Long? = null): File {
        val file = File(dir, name)
        file.writeText("{}")
        if (modified != null) {
            file.setLastModified(modified)
        }
        return file
    }
}
