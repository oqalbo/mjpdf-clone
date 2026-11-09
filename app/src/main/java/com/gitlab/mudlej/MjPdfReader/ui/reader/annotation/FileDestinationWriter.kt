// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import com.gitlab.mudlej.MjPdfReader.core.io.computeHash
import com.gitlab.mudlej.MjPdfReader.core.io.computeTailHash
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

internal class FileDestinationWriter(
    private val target: File,
    private val rename: (File, File) -> Boolean = { sibling, destination -> sibling.renameTo(destination) },
) {
    fun write(
        hash: String,
        tailHash: String,
        sizeBytes: Long,
        verificationFails: () -> Boolean = { false },
        writeBytes: (OutputStream) -> Unit,
    ): Boolean {
        val parent = target.parentFile ?: return false
        val sibling = File(parent, "${target.name}.saving")
        val wrote = runCatching {
            FileOutputStream(sibling).use { output ->
                writeBytes(output)
                output.fd.sync()
            }
            true
        }.getOrDefault(false)
        if (!wrote || verificationFails() || sibling.length() != sizeBytes ||
            computeHash(sibling) != hash || computeTailHash(sibling) != tailHash
        ) {
            sibling.delete()
            return false
        }
        if (!rename(sibling, target)) {
            sibling.delete()
            return false
        }
        return true
    }
}
