// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import android.content.ContentUris
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.SharedStorage
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File

data class DurableCopyRow(
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val uri: Uri,
)

object IntakeReader {

    const val DURABLE_COPY_PATH = "Documents/MJ PDF/"
    const val ONLINE_CACHE_DIRECTORY = "online-pdf"
    const val ONLINE_INCOGNITO_CACHE_DIRECTORY = "online-pdf-incognito"
    const val PREVIEW_CACHE_DIRECTORY = "previews"
    const val IMPORTED_DIRECTORY = "imported"
    private const val DURABLE_COPY_FOLDER = "MJ PDF"

    fun viewIntent(uri: Uri, mimeType: String? = PDF.FILE_TYPE): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            if (mimeType == null) data = uri else setDataAndType(uri, mimeType)
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun implicitViewIntent(uri: Uri, mimeType: String? = null): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            if (mimeType == null) data = uri else setDataAndType(uri, mimeType)
        }

    fun fileViewIntent(file: File, mimeType: String? = PDF.FILE_TYPE): Intent =
        viewIntent(Uri.fromFile(file), mimeType)

    fun pickerIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
        }

    fun incognitoViewIntent(uri: Uri?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            uri?.let { setDataAndType(it, PDF.FILE_TYPE) }
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
            putExtra(PDF.incognitoKey, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun sendTextIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
        }

    fun startFromOutside(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TestEnvironment.targetContext.startActivity(intent)
    }

    fun actionBarTitle(): String? = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) ?: return@onMainThread null
        activity.findViewById<TextView>(R.id.actionbarTitle)?.text?.toString()
    }

    fun awaitActionBarTitle(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the reader action bar title reads \"$expected\"",
            timeoutMs,
            read = { actionBarTitle() },
        ) { it == expected }
    }

    fun isIncognitoIconVisible(): Boolean = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: return@onMainThread false
        activity.findViewById<View>(R.id.actionbarIncognitoIcon)?.visibility == View.VISIBLE
    }

    fun isViewVisible(viewId: Int): Boolean = TestEnvironment.onMainThread {
        val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
            ?: return@onMainThread false
        activity.findViewById<View>(viewId)?.visibility == View.VISIBLE
    }

    fun awaitViewVisible(viewId: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("view $viewId is visible in the reader", timeoutMs) { isViewVisible(viewId) }
    }

    fun awaitPickFileButton(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        awaitViewVisible(R.id.pickFileButton, timeoutMs)
    }

    fun cacheDirectory(name: String): File = File(TestEnvironment.targetContext.cacheDir, name)

    fun filesDirectory(name: String): File = File(TestEnvironment.targetContext.filesDir, name)

    fun onlineCacheFiles(): List<File> =
        cacheDirectory(ONLINE_CACHE_DIRECTORY).listFiles()?.toList().orEmpty()

    fun incognitoCacheFiles(): List<File> =
        cacheDirectory(ONLINE_INCOGNITO_CACHE_DIRECTORY).listFiles()?.toList().orEmpty()

    fun previewCacheEntries(): List<File> =
        cacheDirectory(PREVIEW_CACHE_DIRECTORY).walkTopDown().filter { it.isFile }.toList()

    fun previewDirectories(): List<File> =
        cacheDirectory(PREVIEW_CACHE_DIRECTORY).listFiles()?.filter { it.isDirectory }.orEmpty()

    fun previewDirectoryFor(docKey: String): File =
        File(cacheDirectory(PREVIEW_CACHE_DIRECTORY), docKey)

    fun hasPreviewsFor(docKey: String): Boolean =
        previewDirectoryFor(docKey).listFiles()?.any { it.isFile && !it.name.endsWith(".tmp") } == true

    fun hasPreviewsForIdentity(identity: String): Boolean =
        previewDirectories().any { directory ->
            directory.name.startsWith(identity) &&
                directory.listFiles()?.any { it.isFile && !it.name.endsWith(".tmp") } == true
        }

    fun importedFiles(): List<File> =
        filesDirectory(IMPORTED_DIRECTORY).listFiles()?.toList().orEmpty()

    fun persistedPermissions(): List<UriPermission> =
        TestEnvironment.targetContext.contentResolver.persistedUriPermissions

    fun durableCopies(): List<DurableCopyRow> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                DURABLE_COPY_FOLDER,
            )
            return directory.listFiles()
                ?.filter(File::isFile)
                ?.map { file ->
                    DurableCopyRow(
                        file.name,
                        DURABLE_COPY_PATH,
                        file.length(),
                        Uri.fromFile(file),
                    )
                }
                .orEmpty()
        }

        val resolver = TestEnvironment.targetContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
        )
        val rows = mutableListOf<DurableCopyRow>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(DURABLE_COPY_PATH),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                rows += DurableCopyRow(
                    cursor.getString(nameColumn).orEmpty(),
                    cursor.getString(pathColumn).orEmpty(),
                    cursor.getLong(sizeColumn),
                    ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                )
            }
        }
        return rows
    }

    fun awaitDurableCopy(displayName: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): DurableCopyRow =
        Await.untilValue(
            "a durable copy named $displayName appears under $DURABLE_COPY_PATH",
            timeoutMs,
            read = { durableCopies().firstOrNull { it.displayName == displayName } },
        ) { it != null }!!

    fun holdNoDurableCopy(windowMs: Long = IntakeDb.QUIET_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no durable copy appears under $DURABLE_COPY_PATH",
            windowMs = windowMs,
        ) { durableCopies().isEmpty() }
    }

    fun deleteDurableCopies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val resolver = TestEnvironment.targetContext.contentResolver
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            runCatching {
                resolver.delete(
                    collection,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(DURABLE_COPY_PATH),
                )
            }
        } else {
            SharedStorage.deleteLegacyDirectory(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    DURABLE_COPY_FOLDER,
                )
            )
        }
        runCatching { filesDirectory(IMPORTED_DIRECTORY).deleteRecursively() }
    }
}
