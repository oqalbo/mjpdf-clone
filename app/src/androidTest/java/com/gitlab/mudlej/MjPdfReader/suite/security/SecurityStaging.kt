// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.intake.StubSharer
import java.io.File
import java.util.Locale

object SecurityStaging {
    private const val HOST_PREFIX = "127.0.0.1:"

    fun stageWithPort(name: String, port: Int, directory: File): File {
        val source = SecurityFixtures.file(name)
        val patched = patchPort(source.readBytes(), port)
        val target = File(directory, name)
        target.parentFile?.mkdirs()
        target.writeBytes(patched)
        return target
    }

    fun patchPort(bytes: ByteArray, port: Int): ByteArray {
        val padded = String.format(Locale.ROOT, "%0${SecurityFixtures.PORT_PLACEHOLDER.length}d", port)
        if (padded.length != SecurityFixtures.PORT_PLACEHOLDER.length) {
            throw AssertionError(
                "Port $port does not fit the ${SecurityFixtures.PORT_PLACEHOLDER.length} digit placeholder, so " +
                    "patching it would move every byte offset after it and break the cross reference table."
            )
        }
        val prefix = HOST_PREFIX.toByteArray(Charsets.US_ASCII)
        val placeholder = SecurityFixtures.PORT_PLACEHOLDER.toByteArray(Charsets.US_ASCII)
        val replacement = padded.toByteArray(Charsets.US_ASCII)
        val result = bytes.copyOf()
        var index = 0
        var replaced = 0
        while (index <= result.size - prefix.size - placeholder.size) {
            if (matchesAt(result, index, prefix) && matchesAt(result, index + prefix.size, placeholder)) {
                replacement.copyInto(result, index + prefix.size)
                replaced++
                index += prefix.size + placeholder.size
            } else {
                index++
            }
        }
        if (replaced == 0) {
            throw AssertionError(
                "No $HOST_PREFIX${SecurityFixtures.PORT_PLACEHOLDER} placeholder was found to patch. The fixture " +
                    "manifest records one for every beacon fixture, so a fixture without one has been regenerated " +
                    "with a different construction."
            )
        }
        return result
    }

    private fun matchesAt(haystack: ByteArray, offset: Int, needle: ByteArray): Boolean {
        for (position in needle.indices) {
            if (haystack[offset + position] != needle[position]) return false
        }
        return true
    }
}

object HostileNames {
    const val KEY = "hostile.pdf"

    const val DATABASE_TRAVERSAL = "../../../../data/data/com.gitlab.mudlej.MjPdfReader/databases/app-db.pdf"
    const val SMB_DOUBLE_ENCODED = "SMB%2F..%2F..%2Fescape.pdf"
    const val SDCARD_TRAVERSAL = "../../../../sdcard/escape.pdf"
    const val DOTS_ONLY = "......"
    const val BLANK = ""

    val LONG_NAME: String = "a".repeat(4_000) + ".pdf"

    fun serve(reportedDisplayName: String): Uri {
        StubSharer.requireInstalled()
        val uri = StubSharer.register(KEY, SecurityFixtures.file(SecurityFixtures.HOSTILE_NAME))
        StubSharerProvider.setDisplayName(KEY, reportedDisplayName)
        return uri
    }

    fun clear() {
        StubSharer.clear()
    }
}
