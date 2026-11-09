// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File
import java.security.MessageDigest

data class FileFingerprint(val length: Long, val md5: String) {

    override fun toString(): String = "$length bytes, md5 $md5"
}

object SaveDigest {

    const val PDF_MAGIC = "%PDF"
    const val SETTLE_WINDOW_MS = 2_000L

    fun of(file: File): FileFingerprint = FileFingerprint(file.length(), md5Of(file))

    fun of(uri: Uri): FileFingerprint {
        val bytes = TestEnvironment.targetContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw AssertionError("Nothing could be read from $uri")
        return FileFingerprint(bytes.size.toLong(), hex(MessageDigest.getInstance("MD5").digest(bytes)))
    }

    fun md5Of(file: File): String {
        if (!file.isFile) return ""
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    fun startsWithPdfMagic(file: File): Boolean {
        if (!file.isFile || file.length() < PDF_MAGIC.length) return false
        val head = ByteArray(PDF_MAGIC.length)
        file.inputStream().use { it.read(head) }
        return String(head, Charsets.US_ASCII) == PDF_MAGIC
    }

    fun startsWithPdfMagic(uri: Uri): Boolean {
        val head = TestEnvironment.targetContext.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(PDF_MAGIC.length)
            val read = stream.read(buffer)
            if (read < PDF_MAGIC.length) null else String(buffer, Charsets.US_ASCII)
        }
        return head == PDF_MAGIC
    }

    fun awaitSettled(file: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS): FileFingerprint {
        var last = of(file)
        Await.untilTrueContinuously(
            condition = "the file ${file.name} stops changing",
            stableForMs = SETTLE_WINDOW_MS,
            timeoutMs = timeoutMs,
        ) {
            val now = of(file)
            val stable = now == last
            last = now
            stable
        }
        return last
    }

    fun assertUnchanged(message: String, file: File, before: FileFingerprint) {
        val now = of(file)
        if (now != before) {
            throw AssertionError("$message: ${file.absolutePath} was $before and is now $now")
        }
    }

    fun assertStaysUnchanged(message: String, file: File, before: FileFingerprint, windowMs: Long = SETTLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "$message: ${file.name} keeps reading as $before",
            windowMs = windowMs,
        ) { of(file) == before }
    }

    fun sha256Hex(value: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    private fun hex(bytes: ByteArray): String = bytes.joinToString(separator = "") { "%02x".format(it) }

    private const val BUFFER_BYTES = 1 shl 16
}
