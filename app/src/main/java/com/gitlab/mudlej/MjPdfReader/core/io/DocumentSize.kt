// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import com.gitlab.mudlej.MjPdfReader.data.OnlineDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DocumentSize {

    suspend fun of(context: Context, uri: Uri?): Long? {
        if (uri == null) {
            return null
        }
        return withContext(Dispatchers.IO) {
            OnlineDocumentStore.fileFor(context, uri.toString())?.let { return@withContext it.length() }
            runCatching {
                when (uri.scheme) {
                    ContentResolver.SCHEME_CONTENT ->
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                        }
                    ContentResolver.SCHEME_FILE -> uri.path?.let { path -> File(path).length().takeIf { it > 0 } }
                    else -> null
                }
            }.getOrNull()
        }
    }

    fun availableBytes(context: Context): Long? {
        val directory = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?: context.filesDir
            ?: return null
        return runCatching { StatFs(directory.absolutePath).availableBytes }.getOrNull()
    }
}
