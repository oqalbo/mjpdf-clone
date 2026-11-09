// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object OnlineDocumentStore {

    sealed class WriteResult {
        class Stored(val file: File) : WriteResult()
        data object TooLarge : WriteResult()
        data object NotPdf : WriteResult()
        data object Failed : WriteResult()
    }

    fun fileFor(context: Context, uri: String?): File? {
        if (uri == null) {
            return null
        }
        val name = fileNameFor(uri) ?: return null
        for (directory in listOf(normalDirectory(context), incognitoDirectory(context))) {
            val file = File(directory, name)
            if (file.isFile && file.length() > 0) {
                file.setLastModified(System.currentTimeMillis())
                return file
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun write(
        context: Context,
        uri: String,
        incognito: Boolean,
        input: InputStream,
        maxBytes: Long,
        onBytesWritten: ((Long) -> Unit)? = null,
    ): WriteResult {
        val directory = if (incognito) incognitoDirectory(context) else normalDirectory(context)
        directory.mkdirs()
        pruneOldFiles(directory)
        val name = fileNameFor(uri) ?: return WriteResult.Failed
        val target = File(directory, name)
        val part = File(directory, "$name.part")
        var refusal: WriteResult? = null
        try {
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                val head = ByteArrayOutputStream()
                var headChecked = false
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        break
                    }
                    if (total + read > maxBytes) {
                        refusal = WriteResult.TooLarge
                        break
                    }
                    if (head.size() < PDF_HEADER_WINDOW) {
                        head.write(buffer, 0, minOf(read, PDF_HEADER_WINDOW - head.size()))
                    }
                    output.write(buffer, 0, read)
                    total += read
                    if (!headChecked && (head.size() >= PDF_HEADER_WINDOW || total >= maxBytes)) {
                        headChecked = true
                        if (!looksLikePdf(head.toByteArray())) {
                            refusal = WriteResult.NotPdf
                            break
                        }
                    }
                    onBytesWritten?.invoke(total)
                }
                if (refusal == null && !headChecked && !looksLikePdf(head.toByteArray())) {
                    refusal = WriteResult.NotPdf
                }
            }
        } catch (e: IOException) {
            part.delete()
            throw e
        }
        refusal?.let {
            part.delete()
            return it
        }
        if (part.length() <= 0L || !part.renameTo(target)) {
            part.delete()
            return WriteResult.Failed
        }
        return WriteResult.Stored(target)
    }

    private fun looksLikePdf(head: ByteArray): Boolean {
        if (head.size < PDF_MAGIC.size) {
            return false
        }
        val limit = head.size - PDF_MAGIC.size
        for (start in 0..limit) {
            var matched = true
            for (offset in PDF_MAGIC.indices) {
                if (head[start + offset] != PDF_MAGIC[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return true
            }
        }
        return false
    }

    fun sweepIncognito(context: Context) {
        incognitoDirectory(context).listFiles()?.forEach { it.delete() }
    }

    private fun pruneOldFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun fileNameFor(uri: String): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
            digest.joinToString("") { "%02x".format(it) } + ".pdf"
        }.getOrNull()
    }

    private fun normalDirectory(context: Context): File = File(context.cacheDir, NORMAL_DIRECTORY)

    private fun incognitoDirectory(context: Context): File = File(context.cacheDir, INCOGNITO_DIRECTORY)

    private const val NORMAL_DIRECTORY = "online-pdf"
    private const val INCOGNITO_DIRECTORY = "online-pdf-incognito"
    private const val COPY_BUFFER_SIZE = 8 * 1024
    private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    private const val PDF_HEADER_WINDOW = 1024
    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)
}
