// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

object BackupFolder {

    const val folderName = "MJ PDF"
    private const val retainedAutoBackups = 10
    private const val retainedSafetySnapshots = 3
    private const val manualBackupPrefix = "mj-pdf-backup"
    private const val autoBackupPrefix = "mj-pdf-auto-backup"
    private const val safetySnapshotPrefix = "mj-pdf-pre-import"
    private const val safetyFolderName = "backup-safety"
    private const val staleTmpAgeMillis = 60L * 60L * 1000L
    private const val unknownSafetyFileAgeMillis = 24L * 60L * 60L * 1000L
    private val autoBackupNameRegex =
        Regex("mj-pdf-auto-backup-\\p{Nd}{8}(-\\p{Nd}{6})?( ?\\(\\p{Nd}+\\))?\\.json")
    private val safetySnapshotNameRegex = Regex("mj-pdf-pre-import-\\p{Nd}{8}-\\p{Nd}{6}\\.json")

    fun resolve(context: Context, treeUriString: String?): DocumentFile? {
        if (treeUriString.isNullOrBlank()) {
            return null
        }
        val treeUri = Uri.parse(treeUriString)
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
        if (!granted) {
            return null
        }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!root.isDirectory || !root.canWrite()) {
            return null
        }
        if (root.name == folderName) {
            return root
        }
        root.findFile(folderName)?.let { existing ->
            return if (existing.isDirectory) existing else null
        }
        return root.createDirectory(folderName)
    }

    fun describe(treeUriString: String?): String? {
        if (treeUriString.isNullOrBlank()) {
            return null
        }
        val path = runCatching {
            DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString))
                .substringAfter(':')
                .trim('/')
        }.getOrNull() ?: return folderName
        return when {
            path.isBlank() -> folderName
            path.endsWith(folderName) -> path
            else -> "$path/$folderName"
        }
    }

    fun newBackupFileName(): String = timestampedFileName(manualBackupPrefix)

    fun newAutoBackupFileName(): String = timestampedFileName(autoBackupPrefix)

    fun newSafetySnapshotName(): String = timestampedFileName(safetySnapshotPrefix)

    private fun timestampedFileName(prefix: String): String {
        val now = LocalDateTime.now()
        return "%s-%04d%02d%02d-%02d%02d%02d.json".format(
            Locale.US,
            prefix, now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second)
    }

    fun enforceRetention(folder: DocumentFile) {
        folder.listFiles()
            .filter { it.isFile && it.name?.matches(autoBackupNameRegex) == true }
            .sortedWith(compareByDescending<DocumentFile> { it.lastModified() }.thenByDescending { it.name })
            .drop(retainedAutoBackups)
            .forEach { it.delete() }
    }

    fun sweepStaleTmpFiles(folder: DocumentFile) {
        val cutoff = System.currentTimeMillis() - staleTmpAgeMillis
        folder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".json.tmp") == true && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

    fun safetyDir(context: Context): File = File(context.filesDir, safetyFolderName)

    fun listSafetySnapshots(context: Context): List<File> = listSafetySnapshots(safetyDir(context))

    fun listSafetySnapshots(dir: File): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.name.matches(safetySnapshotNameRegex) }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
    }

    fun pruneSafetySnapshots(context: Context) = pruneSafetySnapshots(safetyDir(context))

    fun pruneSafetySnapshots(dir: File) {
        listSafetySnapshots(dir).drop(retainedSafetySnapshots).forEach { it.delete() }
        val cutoff = System.currentTimeMillis() - unknownSafetyFileAgeMillis
        dir.listFiles()
            ?.filter { it.isFile && !it.name.matches(safetySnapshotNameRegex) }
            ?.filter { it.name.contains(".tmp") || it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }
}
