// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File
import java.io.RandomAccessFile
import java.math.BigInteger
import java.security.MessageDigest

object IntakeFixtures {

    val seededRoot: File = File(Fixtures.externalRoot, "Download")

    const val NORMAL = "normal.pdf"
    const val LARGE = "large.pdf"
    const val PROTECTED = "protected.pdf"
    const val ARABIC = "arabic-rtl.pdf"
    const val EMPTY = "empty.pdf"
    const val CORRUPT = "corrupt.pdf"
    const val NOT_A_PDF = "not-a-pdf.pdf"
    const val SHARED_PREFIX_A = "shared-prefix-a.pdf"
    const val SHARED_PREFIX_B = "shared-prefix-b.pdf"
    const val DOTTED_FOLDER = "dotted.folder"

    const val NORMAL_PAGES = 20
    const val LARGE_PAGES = 500
    const val PROTECTED_PAGES = 6
    const val PROTECTED_PASSWORD = "mjpdf"
    const val ARABIC_PAGES = 12
    const val DOTTED_PAGES = 3
    const val SHARED_PREFIX_PAGES = 2

    const val WINDOW_BYTES = 1024 * 1024
    const val WHOLE_FILE_LIMIT_BYTES = 2L * WINDOW_BYTES
    const val IDENTITY_PREFIX = "h2"

    fun seeded(name: String): File = Fixtures.requireSeededFixture(name, seededRoot)

    fun copyInto(directory: File, name: String, targetName: String = name): File {
        val source = seeded(name)
        val target = File(directory, targetName)
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.length() != source.length()) {
            throw AssertionError(
                "Copy of $name landed at ${target.length()} bytes against a source of ${source.length()}"
            )
        }
        return target
    }

    fun expectedIdentity(file: File): String {
        val digester = MessageDigest.getInstance("MD5")
        val length = file.length()
        RandomAccessFile(file, "r").use { access ->
            if (length <= WHOLE_FILE_LIMIT_BYTES) {
                val buffer = ByteArray(WINDOW_BYTES)
                var position = 0L
                while (position < length) {
                    val count = minOf(WINDOW_BYTES.toLong(), length - position).toInt()
                    access.seek(position)
                    access.readFully(buffer, 0, count)
                    digester.update(buffer, 0, count)
                    position += count
                }
            } else {
                val head = ByteArray(WINDOW_BYTES)
                access.seek(0L)
                access.readFully(head, 0, WINDOW_BYTES)
                digester.update(head, 0, WINDOW_BYTES)
                val tail = ByteArray(WINDOW_BYTES)
                access.seek(length - WINDOW_BYTES)
                access.readFully(tail, 0, WINDOW_BYTES)
                digester.update(tail, 0, WINDOW_BYTES)
            }
        }
        digester.update(bigEndianLength(length))
        return IDENTITY_PREFIX + hexOf(digester.digest())
    }

    fun expectedLegacyIdentity(file: File): String {
        val length = file.length()
        val count = minOf(WINDOW_BYTES.toLong(), length).toInt()
        val buffer = ByteArray(count)
        RandomAccessFile(file, "r").use { access ->
            access.seek(0L)
            access.readFully(buffer, 0, count)
        }
        val digester = MessageDigest.getInstance("MD5")
        digester.update(buffer, 0, count)
        return hexOf(digester.digest())
    }

    private fun bigEndianLength(length: Long): ByteArray {
        val bytes = ByteArray(8)
        for (index in 0 until 8) {
            bytes[index] = (length shr (56 - index * 8)).toByte()
        }
        return bytes
    }

    private fun hexOf(digest: ByteArray): String = String.format("%032x", BigInteger(1, digest))
}
