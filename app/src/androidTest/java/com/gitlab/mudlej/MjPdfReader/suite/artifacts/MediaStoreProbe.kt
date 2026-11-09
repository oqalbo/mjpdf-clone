// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

data class MediaRow(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val size: Long,
    val pending: Int,
)

object MediaStoreProbe {

    const val PICTURES_PATH = "Pictures/MJ PDF/"
    const val LEGACY_PICTURES_PATH = "Pictures/"
    const val DOCUMENTS_PATH = "Documents/MJ PDF/"

    val screenshotPicturesPath: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) PICTURES_PATH else LEGACY_PICTURES_PATH

    private val resolver get() = TestEnvironment.targetContext.contentResolver

    private val columns = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.IS_PENDING,
    )

    fun imagesNamedLike(prefix: String): List<MediaRow> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$prefix%"),
            )
        } else {
            legacyPicturesNamedLike(prefix)
        }

    fun filesUnder(relativePath: String): List<MediaRow> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            query(
                MediaStore.Files.getContentUri(VOLUME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
            )
        } else {
            queryLegacy(
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("${legacyDirectory(relativePath).absolutePath}/%"),
            )
        }

    fun awaitImagesNamedLike(prefix: String, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<MediaRow> =
        Await.untilValue(
            "MediaStore holds $expected image rows named like \"$prefix\"",
            timeoutMs,
            read = { runCatching { imagesNamedLike(prefix) }.getOrDefault(emptyList()) },
        ) { it.size == expected }

    fun awaitFilesUnder(relativePath: String, expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): List<MediaRow> =
        Await.untilValue(
            "MediaStore holds $expected rows under $relativePath",
            timeoutMs,
            read = { runCatching { filesUnder(relativePath) }.getOrDefault(emptyList()) },
        ) { it.size == expected }

    fun awaitPublishedFileUnder(
        relativePath: String,
        expectedSize: Long,
        expectedMimeType: String,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): MediaRow = Await.untilValue(
        "MediaStore holds one fully published $expectedMimeType row of $expectedSize bytes under $relativePath",
        timeoutMs,
        read = { filesUnder(relativePath) },
    ) { rows ->
        rows.size == 1 &&
            rows.single().size == expectedSize &&
            rows.single().mimeType == expectedMimeType &&
            rows.single().pending == 0
    }.single()

    fun assertNoFilesUnder(relativePath: String, windowMs: Long = AppDirectories.STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no MediaStore row appears under $relativePath",
            windowMs = windowMs,
        ) { runCatching { filesUnder(relativePath).isEmpty() }.getOrDefault(false) }
    }

    fun bytesOf(row: MediaRow): ByteArray =
        if (row.uri.scheme == "file") {
            row.uri.path?.let(::File)?.takeIf { it.isFile }?.readBytes()
                ?: throw AssertionError("The legacy picture ${row.uri} could not be opened for reading")
        } else {
            resolver.openInputStream(row.uri)?.use { it.readBytes() }
                ?: throw AssertionError("The MediaStore row ${row.uri} could not be opened for reading")
        }

    fun writeTo(row: MediaRow, target: File): File {
        target.parentFile?.mkdirs()
        target.writeBytes(bytesOf(row))
        return target
    }

    fun deleteNamedLike(prefix: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return legacyPicturesDirectory().listFiles()
                ?.filter { it.isFile && it.name.startsWith(prefix) }
                ?.count { it.delete() }
                ?: 0
        }
        return runCatching {
            resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$prefix%"),
            )
        }.getOrDefault(0)
    }

    fun deleteFileNamed(name: String): Int = runCatching {
        resolver.delete(
            MediaStore.Files.getContentUri(VOLUME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(name),
        )
    }.getOrDefault(0)

    fun deleteUnder(relativePath: String): Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.delete(
                MediaStore.Files.getContentUri(VOLUME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
            )
        } else {
            resolver.delete(
                MediaStore.Files.getContentUri(VOLUME),
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("${legacyDirectory(relativePath).absolutePath}/%"),
            )
        }
    }.getOrDefault(0)

    private fun query(collection: Uri, selection: String, arguments: Array<String>): List<MediaRow> {
        val rows = mutableListOf<MediaRow>()
        resolver.query(collection, columns, selection, arguments, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
            while (cursor.moveToNext()) {
                rows.add(
                    MediaRow(
                        uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString()),
                        displayName = cursor.getString(nameColumn).orEmpty(),
                        relativePath = cursor.getString(pathColumn).orEmpty(),
                        mimeType = cursor.getString(typeColumn).orEmpty(),
                        size = cursor.getLong(sizeColumn),
                        pending = cursor.getInt(pendingColumn),
                    )
                )
            }
        }
        return rows
    }

    @Suppress("DEPRECATION")
    private fun queryLegacy(selection: String, arguments: Array<String>): List<MediaRow> {
        val collection = MediaStore.Files.getContentUri(VOLUME)
        val legacyColumns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
        )
        val rows = mutableListOf<MediaRow>()
        resolver.query(collection, legacyColumns, selection, arguments, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(dataColumn))
                val displayName = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: file.name
                rows.add(
                    MediaRow(
                        uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString()),
                        displayName = displayName,
                        relativePath = file.parentFile?.relativeToOrNull(Environment.getExternalStorageDirectory())
                            ?.path?.let { "$it/" }.orEmpty(),
                        mimeType = cursor.getString(typeColumn).orEmpty(),
                        size = cursor.getLong(sizeColumn),
                        pending = 0,
                    )
                )
            }
        }
        return rows
    }

    @Suppress("DEPRECATION")
    private fun legacyPicturesNamedLike(prefix: String): List<MediaRow> =
        legacyPicturesDirectory().listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
            ?.sortedBy { it.name }
            ?.map { file ->
                MediaRow(
                    uri = Uri.fromFile(file),
                    displayName = file.name,
                    relativePath = LEGACY_PICTURES_PATH,
                    mimeType = JPEG_MIME_TYPE,
                    size = file.length(),
                    pending = 0,
                )
            }
            .orEmpty()

    @Suppress("DEPRECATION")
    private fun legacyPicturesDirectory(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    @Suppress("DEPRECATION")
    private fun legacyDirectory(relativePath: String): File =
        File(Environment.getExternalStorageDirectory(), relativePath)

    private const val VOLUME = "external"
    private const val JPEG_MIME_TYPE = "image/jpeg"
}
