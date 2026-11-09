// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import android.content.UriPermission
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import java.io.File

object PrivacyStores {

    const val QUIET_WINDOW_MS = 1_500L

    const val ANNOTATION_DIRECTORY = "annotations"
    const val RESCUE_DIRECTORY = "annotation-rescue"
    const val SIGNATURE_DIRECTORY = "signature"
    const val SIGNATURE_FILE = "signature.json"
    const val IMPORTED_DIRECTORY = "imported"
    const val COVER_DIRECTORY = "covers"
    const val PREVIEW_DIRECTORY = "previews"
    const val ONLINE_DIRECTORY = "online-pdf"
    const val ONLINE_INCOGNITO_DIRECTORY = "online-pdf-incognito"
    const val JOURNAL_SUFFIX = ".journal"
    const val DURABLE_COPY_PATH = "Documents/MJ PDF/"

    private const val DURABLE_COPY_FOLDER = "MJ PDF"

    private val journal by lazy { AnnotationJournal(TestEnvironment.targetContext) }

    fun filesDirectory(name: String): File = File(TestEnvironment.targetContext.filesDir, name)

    fun cacheDirectory(name: String): File = File(TestEnvironment.targetContext.cacheDir, name)

    fun records(): List<PdfRecord> = Records.all()

    fun recordCount(): Int = records().size

    fun record(hash: String): PdfRecord? = Records.byHash(hash)

    fun recordNamed(fileName: String): PdfRecord? = records().firstOrNull { it.fileName == fileName }

    fun bookmarks(): List<UserBookmark> = Records.database.userBookmarkDao().findAll()

    fun bookmarkCount(): Int = countOf("UserBookmark")

    fun scannedEntry(path: String): ScannedPdfEntry? =
        Records.database.scannedPdfEntryDao().findAll().firstOrNull { it.path == path }

    fun awaitScannedEntry(
        path: String,
        timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS,
    ): ScannedPdfEntry =
        Await.untilNotNull("the scanned PDF cache holds the exact path $path", timeoutMs) {
            scannedEntry(path)
        }

    fun holdScannedEntryUnchanged(
        path: String,
        expected: ScannedPdfEntry?,
        windowMs: Long = QUIET_WINDOW_MS,
    ) {
        hold("the scanned PDF cache row for $path stays exactly $expected", windowMs) {
            scannedEntry(path) == expected
        }
    }

    fun saveDestinationCount(): Int = countOf("PdfAnnotationSaveDestination")

    fun countOf(table: String): Int =
        Records.database.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    val annotationDirectory: File get() = filesDirectory(ANNOTATION_DIRECTORY)

    val rescueDirectory: File get() = filesDirectory(RESCUE_DIRECTORY)

    val signatureFile: File get() = File(filesDirectory(SIGNATURE_DIRECTORY), SIGNATURE_FILE)

    val importedDirectory: File get() = filesDirectory(IMPORTED_DIRECTORY)

    val coverDirectory: File get() = cacheDirectory(COVER_DIRECTORY)

    val previewDirectory: File get() = cacheDirectory(PREVIEW_DIRECTORY)

    val onlineDirectory: File get() = cacheDirectory(ONLINE_DIRECTORY)

    val onlineIncognitoDirectory: File get() = cacheDirectory(ONLINE_INCOGNITO_DIRECTORY)

    fun journalFor(uri: Uri): File = File(annotationDirectory, "${SourceKey.of(uri)}$JOURNAL_SUFFIX")

    fun drainJournalWriter(uri: Uri): Int = journal.readAll(uri).size

    fun journalFiles(): List<File> =
        annotationDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(JOURNAL_SUFFIX) }.orEmpty()

    fun rescueFiles(): List<File> = rescueDirectory.listFiles()?.filter { it.isFile }.orEmpty()

    fun importedFiles(): List<File> = importedDirectory.listFiles()?.filter { it.isFile }.orEmpty()

    fun partFilesUnderFiles(): List<File> =
        TestEnvironment.targetContext.filesDir.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.toList()

    fun coverFiles(): List<File> =
        coverDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }.orEmpty()

    fun coverFilesFor(fileHash: String): List<File> =
        coverDirectory.listFiles()?.filter { it.isFile && it.name.startsWith("$fileHash-w") }.orEmpty()

    fun previewDirectories(): List<File> =
        previewDirectory.listFiles()?.filter { it.isDirectory }.orEmpty()

    fun hasPreviewsFor(fileHash: String): Boolean =
        previewDirectories().any { directory ->
            directory.name.startsWith(fileHash) &&
                directory.listFiles()?.any { it.isFile && !it.name.endsWith(".tmp") } == true
        }

    fun previewFiles(): List<File> =
        previewDirectory.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList()

    fun onlineFiles(): List<File> = onlineDirectory.listFiles()?.filter { it.isFile }.orEmpty()

    fun onlineIncognitoFiles(): List<File> =
        onlineIncognitoDirectory.listFiles()?.filter { it.isFile }.orEmpty()

    fun persistedPermissions(): List<UriPermission> =
        TestEnvironment.targetContext.contentResolver.persistedUriPermissions

    fun persistedUris(): List<String> = persistedPermissions().map { it.uri.toString() }

    fun durableCopyNames(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mediaStoreDurableCopyNames()
        } else {
            legacyDurableCopyNames()
        }

    private fun legacyDurableCopyNames(): List<String> {
        if (!publicDurableCopyDirectory.exists()) return emptyList()
        if (!publicDurableCopyDirectory.isDirectory) {
            throw AssertionError("${publicDurableCopyDirectory.absolutePath} is not a directory")
        }
        val files = publicDurableCopyDirectory.listFiles()
            ?: throw AssertionError("Could not inspect ${publicDurableCopyDirectory.absolutePath}")
        return files.filter { it.isFile }.map { it.name }.sorted()
    }

    private fun mediaStoreDurableCopyNames(): List<String> {
        val resolver = TestEnvironment.targetContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val names = mutableListOf<String>()
        val cursor = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(DURABLE_COPY_PATH),
            null,
        ) ?: throw AssertionError("Could not query MediaStore under $DURABLE_COPY_PATH")
        cursor.use {
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) names += cursor.getString(nameColumn).orEmpty()
        }
        return names.sorted()
    }

    fun deleteDurableCopies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val resolver = TestEnvironment.targetContext.contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(DURABLE_COPY_PATH),
            )
        } else {
            com.gitlab.mudlej.MjPdfReader.suite.fixtures.SharedStorage.deleteLegacyDirectory(publicDurableCopyDirectory)
        }
        runCatching { importedDirectory.deleteRecursively() }
    }

    @Suppress("DEPRECATION")
    private val publicDurableCopyDirectory: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            DURABLE_COPY_FOLDER,
        )

    fun downloadsFile(name: String): File = File(PrivacyFixtures.seededRoot, name)

    fun awaitRecordCount(expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<PdfRecord> =
        Await.untilValue(
            "the PdfRecord table holds exactly $expected row(s)",
            timeoutMs,
            read = { records() },
        ) { it.size == expected }

    fun awaitRecordNamed(fileName: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): PdfRecord =
        Await.untilNotNull("a PdfRecord row named \"$fileName\" exists", timeoutMs) { recordNamed(fileName) }

    fun holdRecordCount(expected: Int, windowMs: Long = QUIET_WINDOW_MS) {
        hold("the PdfRecord table stays at $expected row(s), it holds ${recordCount()}", windowMs) {
            recordCount() == expected
        }
    }

    fun holdNoRecords(windowMs: Long = QUIET_WINDOW_MS) = holdRecordCount(0, windowMs)

    fun holdNoJournal(windowMs: Long = QUIET_WINDOW_MS) {
        hold("no annotation journal file is ever written", windowMs) { journalFiles().isEmpty() }
    }

    fun holdNoPreviewsFor(fileHash: String, windowMs: Long = QUIET_WINDOW_MS) {
        hold("no page preview is written for $fileHash", windowMs) { !hasPreviewsFor(fileHash) }
    }

    fun holdNoDurableCopy(windowMs: Long = QUIET_WINDOW_MS) {
        hold("no durable copy appears under $DURABLE_COPY_PATH or in files/imported", windowMs) {
            durableCopyNames().isEmpty() && importedFiles().isEmpty()
        }
    }

    fun holdNoPersistedGrants(windowMs: Long = QUIET_WINDOW_MS) {
        hold("the persisted URI permission list stays empty, it holds ${persistedUris()}", windowMs) {
            persistedPermissions().isEmpty()
        }
    }

    fun holdBookmarkCount(expected: Int, windowMs: Long = QUIET_WINDOW_MS) {
        hold("the UserBookmark table stays at $expected row(s)", windowMs) { bookmarkCount() == expected }
    }

    fun holdNoRescueCopy(windowMs: Long = QUIET_WINDOW_MS) {
        hold("no rescue copy is written under files/$RESCUE_DIRECTORY", windowMs) { rescueFiles().isEmpty() }
    }

    fun holdNoSignatureTemplate(windowMs: Long = QUIET_WINDOW_MS) {
        hold("no signature template is written to ${signatureFile.absolutePath}", windowMs) {
            !signatureFile.exists()
        }
    }

    fun hold(condition: String, windowMs: Long = QUIET_WINDOW_MS, predicate: () -> Boolean) {
        Await.assertThroughout(
            condition = condition,
            windowMs = windowMs,
        ) { predicate() }
    }

    fun awaitFileCount(directory: File, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<File> =
        Await.untilValue(
            "${directory.absolutePath} holds exactly $expected file(s)",
            timeoutMs,
            read = { directory.listFiles()?.filter { it.isFile }.orEmpty() },
        ) { it.size == expected }
}
