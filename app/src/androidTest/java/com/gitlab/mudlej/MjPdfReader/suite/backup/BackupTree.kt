// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import android.provider.DocumentsContract
import com.gitlab.mudlej.MjPdfReader.data.BackupFolder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import java.io.File

object BackupTree {
    val manualNamePattern = Regex("mj-pdf-backup-\\d{8}-\\d{6}\\.json")
    val snapshotNamePattern = Regex("mj-pdf-pre-import-\\d{8}-\\d{6}\\.json")

    val autoNamePattern = Regex("mj-pdf-auto-backup-\\d{8}-\\d{6}\\.json")

    val autoRetentionPattern =
        Regex("mj-pdf-auto-backup-\\p{Nd}{8}(-\\p{Nd}{6})?( ?\\(\\p{Nd}+\\))?\\.json")

    val documentsRoot: File = File(Fixtures.externalRoot, "Documents")

    fun pick(
        directory: File,
        picker: SafPickerRobot = SafPickerRobot(),
        preferenceTimeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ) {
        picker.awaitShown()
        picker.navigateToDocuments()
        segmentsUnderDocuments(directory).forEach { picker.openFolder(it) }
        picker.confirmUseThisFolder()
        val expectedUris = listOf(
            "primary:${directory.relativeTo(Fixtures.externalRoot).invariantSeparatorsPath}",
            "home:${segmentsUnderDocuments(directory).joinToString("/")}",
        ).map { documentId ->
            DocumentsContract.buildTreeDocumentUri(
                "com.android.externalstorage.documents", documentId,
            ).toString()
        }.toSet()
        Await.untilValue(
            "the backup folder preference identifies ${directory.absolutePath}",
            preferenceTimeoutMs,
            read = { BackupSeed.string(Preferences.backupFolderTreeUriKey) },
        ) { it in expectedUris }
    }

    fun openFixture(
        fixture: File,
        picker: SafPickerRobot = SafPickerRobot(),
        onSelectionDispatched: () -> Unit = {},
        preparePicker: (SafPickerRobot) -> Unit = {},
    ) {
        picker.awaitShown()
        picker.navigateToDownloads()
        preparePicker(picker)
        picker.chooseFileNamedInCurrentFolder(
            fixture.name,
            timeoutMs = Await.DEFAULT_TIMEOUT_MS,
            onSelectionDispatched = onSelectionDispatched,
        )
    }

    fun segmentsUnderDocuments(directory: File): List<String> {
        val path = directory.absolutePath
        val root = documentsRoot.absolutePath
        if (!path.startsWith(root)) {
            throw AssertionError(
                "The backup tree $path must live under $root so the system picker can reach it through " +
                    "the Documents root"
            )
        }
        return path.removePrefix(root).split(File.separator).filter { it.isNotBlank() }
    }

    fun folderIn(directory: File): File = File(directory, BackupFolder.folderName)

    fun folderExists(directory: File): Boolean = folderIn(directory).isDirectory

    fun createFolderIn(directory: File): File = folderIn(directory).also { it.mkdirs() }

    fun files(directory: File): List<File> =
        folderIn(directory).listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    fun fileNames(directory: File): List<String> = files(directory).map { it.name }

    fun matching(directory: File, pattern: Regex): List<File> =
        files(directory).filter { pattern.matches(it.name) }

    fun manualBackups(directory: File): List<File> = matching(directory, manualNamePattern)

    fun autoBackups(directory: File): List<File> = matching(directory, autoRetentionPattern)

    fun temporaryFiles(directory: File): List<File> = files(directory).filter { it.name.endsWith(".tmp") }

    fun awaitManualBackup(directory: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS): File =
        Await.untilValue(
            "a manual backup file appears in ${folderIn(directory).absolutePath}",
            timeoutMs,
            read = { manualBackups(directory) },
        ) { it.isNotEmpty() }.maxByOrNull { it.name }!!

    fun awaitManualBackupCount(
        directory: File,
        expected: Int,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): List<File> = Await.untilValue(
        "${folderIn(directory).absolutePath} holds $expected manual backup files",
        timeoutMs,
        read = { manualBackups(directory) },
    ) { it.size == expected }

    fun awaitAutoBackup(directory: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS): File =
        Await.untilValue(
            "an auto backup file appears in ${folderIn(directory).absolutePath}",
            timeoutMs,
            read = { autoBackups(directory) },
        ) { it.isNotEmpty() }.maxByOrNull { it.name }!!

    fun awaitNoTemporaryFiles(directory: File, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "no .tmp file remains in ${folderIn(directory).absolutePath}",
            timeoutMs,
            read = { temporaryFiles(directory).map { it.name } },
        ) { it.isEmpty() }
    }

    fun assertNothingWritten(directory: File, windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "nothing is written into ${folderIn(directory).absolutePath}",
            windowMs = windowMs,
        ) { files(directory).isEmpty() }
    }

    fun assertNoFolderCreated(directory: File, windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no ${BackupFolder.folderName} directory appears in ${directory.absolutePath}",
            windowMs = windowMs,
        ) { !folderExists(directory) }
    }

    fun seed(directory: File, name: String, content: ByteArray, lastModified: Long? = null): File {
        val folder = createFolderIn(directory)
        val target = File(folder, name)
        Fixtures.pushBytes(content, target)
        lastModified?.let { Fixtures.setLastModified(target, it) }
        return target
    }

    fun requireEmpty(directory: File) {
        val existing = if (folderExists(directory)) fileNames(directory) else emptyList()
        if (existing.isNotEmpty()) {
            throw ConditionTimeoutException(
                "This test's own tree ${directory.absolutePath} was expected to start empty but its " +
                    "${BackupFolder.folderName} directory already holds $existing"
            )
        }
    }

    fun safetyDirectory(): File =
        BackupFolder.safetyDir(com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment.targetContext)

    fun safetyFiles(): List<File> =
        safetyDirectory().takeIf { it.isDirectory }?.listFiles()?.filter { it.isFile }
            ?.sortedBy { it.name }.orEmpty()

    fun safetySnapshots(): List<File> = safetyFiles().filter { snapshotNamePattern.matches(it.name) }

    fun awaitSafetySnapshotCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<File> =
        Await.untilValue(
            "the backup-safety directory holds $expected snapshot files",
            timeoutMs,
            read = { safetySnapshots() },
        ) { it.size == expected }

    fun assertNoSafetyDirectory(windowMs: Long = Await.SHORT_TIMEOUT_MS) {
        Await.assertThroughout(
            condition = "no backup-safety directory is created",
            windowMs = windowMs,
        ) { !safetyDirectory().exists() }
    }

    fun clearSafetyDirectory() {
        safetyDirectory().deleteRecursively()
    }
}
