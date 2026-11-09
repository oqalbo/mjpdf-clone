// Written by Mudlej. License is GPLv3.

package androidx.documentfile.provider

import android.net.Uri
import java.io.File

internal enum class RenameResult { SUCCESS, MOVED_BUT_FALSE, REFUSED, CORRUPTED }

internal class ReportingFile(
    private var file: File,
    private val result: RenameResult,
    private val refuseDelete: Boolean = false,
) : DocumentFile(null) {
    override fun getUri(): Uri = Uri.fromFile(file)
    override fun getName(): String = file.name
    override fun getType(): String = if (file.isDirectory) "vnd.android.document/directory" else "application/json"
    override fun isDirectory(): Boolean = file.isDirectory
    override fun isFile(): Boolean = file.isFile
    override fun isVirtual(): Boolean = false
    override fun lastModified(): Long = file.lastModified()
    override fun length(): Long = file.length()
    override fun canRead(): Boolean = file.canRead()
    override fun canWrite(): Boolean = file.canWrite()
    override fun exists(): Boolean = file.exists()
    override fun delete(): Boolean = !refuseDelete && file.delete()
    override fun listFiles(): Array<DocumentFile> = file.listFiles().orEmpty()
        .map { ReportingFile(it, result, refuseDelete) }.toTypedArray()
    override fun createDirectory(displayName: String): DocumentFile? = null

    override fun createFile(mimeType: String, displayName: String): DocumentFile {
        val target = unusedName(file, displayName)
        check(target.createNewFile())
        return ReportingFile(target, result, refuseDelete)
    }

    override fun renameTo(displayName: String): Boolean {
        if (result == RenameResult.REFUSED) return false
        val target = unusedName(file.parentFile!!, displayName)
        check(file.renameTo(target))
        if (result == RenameResult.CORRUPTED) {
            target.writeText(target.readText().replace("\"exportedAt\": \"", "\"exportedAt\": \"altered-"))
        }
        if (result != RenameResult.SUCCESS) return false
        file = target
        return true
    }

    private fun unusedName(parent: File, name: String): File {
        val requested = File(parent, name)
        var candidate = requested
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(parent, "${requested.nameWithoutExtension} (${suffix++}).${requested.extension}")
        }
        return candidate
    }
}
