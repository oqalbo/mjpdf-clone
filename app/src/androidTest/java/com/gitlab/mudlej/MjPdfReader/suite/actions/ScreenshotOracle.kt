// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

data class ScreenshotRow(
    val id: Long,
    val displayName: String,
    val relativePath: String?,
    val mimeType: String?,
) {
    val uri: Uri
        get() = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
}

data class ImageSize(val width: Int, val height: Int)

object ScreenshotOracle {

    const val JPEG_FIRST_BYTE = 0xFF
    const val JPEG_SECOND_BYTE = 0xD8

    fun rows(displayNamePrefix: String): List<ScreenshotRow> {
        val resolver = TestEnvironment.targetContext.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val rows = mutableListOf<ScreenshotRow>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$displayNamePrefix%"),
            "${MediaStore.Images.Media._ID} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                rows.add(
                    ScreenshotRow(
                        id = cursor.getLong(idColumn),
                        displayName = cursor.getString(nameColumn),
                        relativePath = if (pathColumn >= 0) cursor.getString(pathColumn) else null,
                        mimeType = cursor.getString(mimeColumn),
                    )
                )
            }
        }
        return rows
    }

    fun awaitRowCount(
        displayNamePrefix: String,
        expected: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): List<ScreenshotRow> = Await.untilValue(
        "MediaStore holds exactly $expected image rows named \"$displayNamePrefix...\"",
        timeoutMs,
        read = { rows(displayNamePrefix) },
    ) { it.size == expected }

    fun awaitSingleRow(displayNamePrefix: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): ScreenshotRow =
        awaitRowCount(displayNamePrefix, 1, timeoutMs).first()

    fun deleteRows(displayNamePrefix: String): Int {
        val resolver = TestEnvironment.targetContext.contentResolver
        return runCatching {
            resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("$displayNamePrefix%"),
            )
        }.getOrDefault(0)
    }

    fun sizeOf(row: ScreenshotRow): ImageSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        TestEnvironment.targetContext.contentResolver.openInputStream(row.uri).use { stream ->
            stream ?: throw ConditionTimeoutException("MediaStore row ${row.uri} cannot be opened for reading")
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw AssertionError(
                "MediaStore row ${row.displayName} does not decode as an image, its bounds read " +
                    "${options.outWidth} by ${options.outHeight}"
            )
        }
        return ImageSize(options.outWidth, options.outHeight)
    }

    fun isJpeg(row: ScreenshotRow): Boolean {
        val header = ByteArray(2)
        TestEnvironment.targetContext.contentResolver.openInputStream(row.uri).use { stream ->
            stream ?: throw ConditionTimeoutException("MediaStore row ${row.uri} cannot be opened for reading")
            if (stream.read(header) != header.size) return false
        }
        return (header[0].toInt() and 0xFF) == JPEG_FIRST_BYTE &&
            (header[1].toInt() and 0xFF) == JPEG_SECOND_BYTE
    }

    fun expectedRelativePathPrefix(): String =
        "${Environment.DIRECTORY_PICTURES}/${TestEnvironment.targetContext.getString(R.string.mj_app_name)}"

    fun externalFilesCopies(displayNamePrefix: String): List<File> {
        val directory = TestEnvironment.targetContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return emptyList()
        return directory.listFiles()?.filter { it.name.startsWith(displayNamePrefix) }.orEmpty().sortedBy { it.name }
    }

    @Suppress("DEPRECATION")
    fun publicPictures(displayNamePrefix: String): List<File> =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .listFiles()
            ?.filter { it.name.startsWith(displayNamePrefix) }
            .orEmpty()
            .sortedBy { it.name }

    fun awaitPublicPictureCount(
        displayNamePrefix: String,
        expected: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): List<File> = Await.untilValue(
        "the public Pictures directory holds $expected files named \"$displayNamePrefix...\"",
        timeoutMs,
        read = { publicPictures(displayNamePrefix) },
    ) { it.size == expected }

    fun awaitSinglePublicPicture(
        displayNamePrefix: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): File = awaitPublicPictureCount(displayNamePrefix, 1, timeoutMs).single()

    fun sizeOf(file: File): ImageSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw AssertionError(
                "File ${file.absolutePath} does not decode as an image, its bounds read " +
                    "${options.outWidth} by ${options.outHeight}"
            )
        }
        return ImageSize(options.outWidth, options.outHeight)
    }

    fun isJpeg(file: File): Boolean {
        val header = ByteArray(2)
        file.inputStream().use { stream ->
            if (stream.read(header) != header.size) return false
        }
        return (header[0].toInt() and 0xFF) == JPEG_FIRST_BYTE &&
            (header[1].toInt() and 0xFF) == JPEG_SECOND_BYTE
    }

    fun awaitExternalFilesCopyCount(
        displayNamePrefix: String,
        expected: Int,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): List<File> = Await.untilValue(
        "the app's external Pictures directory holds exactly $expected files named \"$displayNamePrefix...\"",
        timeoutMs,
        read = { externalFilesCopies(displayNamePrefix) },
    ) { it.size == expected }

    fun deleteExternalFilesCopies(displayNamePrefix: String) {
        externalFilesCopies(displayNamePrefix).forEach { runCatching { it.delete() } }
    }

    fun deletePublicPictures(displayNamePrefix: String) {
        publicPictures(displayNamePrefix).forEach { runCatching { it.delete() } }
    }

    fun clear(displayNamePrefix: String) {
        deleteRows(displayNamePrefix)
        deleteExternalFilesCopies(displayNamePrefix)
        deletePublicPictures(displayNamePrefix)
    }
}
