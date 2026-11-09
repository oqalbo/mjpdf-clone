// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

object DocumentRemover {

    private const val MEDIA_AUTHORITY = "media"

    data class Removal(val deleted: Boolean, val path: String?)

    fun remove(context: Context, uri: Uri): Removal {
        val path = resolvePath(context, uri)
        return Removal(deleted = delete(context, uri), path = path)
    }

    private fun resolvePath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun delete(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).delete() } == true
        }
        if (uri.scheme != "content") {
            return false
        }
        if (uri.authority == MEDIA_AUTHORITY) {
            return deleteWithResolver(context, uri)
        }
        if (runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)) {
            return deleteAsDocument(context, uri)
        }
        return deleteWithResolver(context, uri) || deleteAsDocument(context, uri)
    }

    private fun deleteWithResolver(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    private fun deleteAsDocument(context: Context, uri: Uri): Boolean {
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }.getOrDefault(false)
    }
}
