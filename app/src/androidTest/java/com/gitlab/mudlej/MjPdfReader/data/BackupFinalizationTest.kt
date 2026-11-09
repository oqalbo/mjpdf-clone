// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.net.Uri
import androidx.documentfile.provider.RenameResult
import androidx.documentfile.provider.ReportingFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gitlab.mudlej.MjPdfReader.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupFinalizationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "backup-finalization-${UUID.randomUUID()}")
    private lateinit var database: AppDatabase
    private lateinit var manager: BackupManager
    private val options = BackupExportOptions(includeSettings = false, includeHistory = true, includePasswords = false)

    @Before
    fun setUp() {
        assertTrue(directory.mkdirs())
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        manager = BackupManager(context, PdfRepository(database))
    }

    @After
    fun cleanUp() {
        if (::database.isInitialized) database.close()
        directory.deleteRecursively()
    }

    @Test
    fun successfulRenameLeavesOneImportableBackup() = runBlocking {
        manager.export(ReportingFile(directory, RenameResult.SUCCESS), "backup.json", options)
        assertSingleBackup()
    }

    @Test
    fun renameThatMovesTheFileButReportsFailureDoesNotCreateAnotherBackup() = runBlocking {
        manager.export(ReportingFile(directory, RenameResult.MOVED_BUT_FALSE), "backup.json", options)
        assertSingleBackup()
    }

    @Test
    fun misreportedRenameWithANameCollisionPreservesBothBackups() = runBlocking {
        val previous = File(directory, "backup.json").apply { writeText("previous backup") }
        manager.export(ReportingFile(directory, RenameResult.MOVED_BUT_FALSE), "backup.json", options)

        assertEquals("previous backup", previous.readText())
        assertEquals(2, directory.listFiles()!!.size)
        val exported = directory.listFiles()!!.single { it != previous }
        assertTrue(manager.parse(Uri.fromFile(exported)).includesHistory)
        assertFalse(exported.name.endsWith(".tmp"))
    }

    @Test
    fun refusedRenameStillUsesTheCopyFallback() = runBlocking {
        manager.export(ReportingFile(directory, RenameResult.REFUSED), "backup.json", options)
        assertSingleBackup()
    }

    @Test
    fun anOldFileWithTheSameNameIsNotMistakenForTheCompletedExport() = runBlocking {
        val previous = File(directory, "backup.json").apply { writeText("previous backup") }
        manager.export(ReportingFile(directory, RenameResult.REFUSED), "backup.json", options)

        assertEquals("previous backup", previous.readText())
        assertEquals(2, directory.listFiles()!!.size)
        assertTrue(manager.parse(Uri.fromFile(directory.listFiles()!!.single { it != previous })).includesHistory)
    }

    @Test
    fun anUndeletableTemporaryFileStillReportsCleanupFailure() = runBlocking {
        val failure = runCatching {
            manager.export(ReportingFile(directory, RenameResult.REFUSED, refuseDelete = true), "backup.json", options)
        }.exceptionOrNull()

        assertEquals(R.string.backup_error_cleanup_failed, (failure as? BackupException)?.messageRes)
        assertTrue(directory.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test
    fun aMisreportedRenameWithCorruptedContentsIsNotAccepted() = runBlocking {
        val failure = runCatching {
            manager.export(ReportingFile(directory, RenameResult.CORRUPTED), "backup.json", options)
        }.exceptionOrNull()

        assertTrue("a renamed file with different bytes cannot establish success", failure is BackupException)
    }

    private suspend fun assertSingleBackup() {
        val files = directory.listFiles()!!
        assertEquals(1, files.size)
        assertEquals("backup.json", files.single().name)
        assertTrue(manager.parse(Uri.fromFile(files.single())).includesHistory)
    }

}
