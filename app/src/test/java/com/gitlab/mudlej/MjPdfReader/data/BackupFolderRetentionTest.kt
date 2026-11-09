// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import androidx.documentfile.provider.DocumentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupFolderRetentionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun retentionKeepsTheTenNewestAutomaticBackupsAndAllOtherFiles() {
        val dir = temporaryFolder.newFolder()
        val droppedNames = listOf("mj-pdf-auto-backup-20991231.json", "mj-pdf-auto-backup-20991231-235959 (1).json")
        val keptNames = (10..19).map { "mj-pdf-auto-backup-202601$it-000000.json" }
        val otherNames = listOf("mj-pdf-backup-20260101-000000.json", "notes.txt", "mj-pdf-auto-backup-invalid.json")
        keptNames.reversed().forEachIndexed { index, name -> write(dir, name, 20_000L - index * 1_000L) }
        droppedNames.forEachIndexed { index, name -> write(dir, name, 2_000L + index * 1_000L) }
        otherNames.forEach { name -> write(dir, name, 1_000L) }

        BackupFolder.enforceRetention(DocumentFile.fromFile(dir))

        assertEquals((keptNames + otherNames).toSet(), dir.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun sweepDeletesOnlyOldJsonTemporaryFiles() {
        val dir = temporaryFolder.newFolder()
        write(dir, "old.json.tmp", 1_000L)
        write(dir, "fresh.json.tmp", 4_102_444_800_000L)
        write(dir, "unrelated.tmp", 1_000L)

        BackupFolder.sweepStaleTmpFiles(DocumentFile.fromFile(dir))

        assertEquals(setOf("fresh.json.tmp", "unrelated.tmp"), dir.listFiles()!!.map { it.name }.toSet())
    }

    private fun write(dir: File, name: String, modified: Long) {
        val file = File(dir, name)
        file.writeText("{}")
        assertTrue("Could not set modified time for $name", file.setLastModified(modified))
    }
}
