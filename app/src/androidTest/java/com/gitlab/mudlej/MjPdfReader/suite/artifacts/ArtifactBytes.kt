// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import java.io.File
import java.security.MessageDigest

object ArtifactBytes {

    val PDF_HEADER: ByteArray = "%PDF-".toByteArray(Charsets.US_ASCII)
    val PDF_TRAILER: ByteArray = "%%EOF".toByteArray(Charsets.US_ASCII)
    val JPEG_MAGIC: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    val PNG_MAGIC: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    val UTF8_BOM: ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    const val TAIL_WINDOW = 1024

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun startsWith(file: File, prefix: ByteArray): Boolean {
        if (file.length() < prefix.size) return false
        file.inputStream().use { stream ->
            val head = ByteArray(prefix.size)
            if (stream.read(head) != prefix.size) return false
            return head.contentEquals(prefix)
        }
    }

    fun firstBytes(file: File, count: Int): ByteArray {
        file.inputStream().use { stream ->
            val head = ByteArray(count)
            val read = stream.read(head)
            return if (read == count) head else head.copyOf(read.coerceAtLeast(0))
        }
    }

    fun tailContains(file: File, needle: ByteArray, window: Int = TAIL_WINDOW): Boolean {
        val length = file.length()
        val start = (length - window).coerceAtLeast(0L)
        val size = (length - start).toInt()
        val tail = ByteArray(size)
        file.inputStream().use { stream ->
            stream.skip(start)
            var filled = 0
            while (filled < size) {
                val read = stream.read(tail, filled, size - filled)
                if (read < 0) break
                filled += read
            }
        }
        return occurrences(tail, needle) > 0
    }

    fun occurrences(file: File, needle: ByteArray): Int = occurrences(file.readBytes(), needle)

    fun occurrences(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return 0
        var count = 0
        var index = 0
        while (index <= haystack.size - needle.size) {
            if (matchesAt(haystack, needle, index)) {
                count++
                index += needle.size
            } else {
                index++
            }
        }
        return count
    }

    fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        for (index in 0..haystack.size - needle.size) {
            if (matchesAt(haystack, needle, index)) return index
        }
        return -1
    }

    fun containsText(file: File, text: String): Boolean =
        indexOf(file.readBytes(), text.toByteArray(Charsets.UTF_8)) >= 0

    private fun matchesAt(haystack: ByteArray, needle: ByteArray, offset: Int): Boolean {
        for (position in needle.indices) {
            if (haystack[offset + position] != needle[position]) return false
        }
        return true
    }
}
