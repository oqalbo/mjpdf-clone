// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupFile(val file: File) {

    val bytes: ByteArray = file.readBytes()

    val text: String = ExportFile.strictDecode(bytes)

    val root: JSONObject = JSONObject(text)

    fun memberNames(): Set<String> = root.keys().asSequence().toSet()

    fun has(member: String): Boolean = root.has(member)

    fun schemaVersion(): Int = root.getInt("schemaVersion")

    fun appVersionCode(): Long = root.getLong("appVersionCode")

    fun exportedAt(): String = root.getString("exportedAt")

    fun settings(): List<JSONObject> = arrayOf("settings")

    fun records(): List<JSONObject> = arrayOf("pdfRecords")

    fun bookmarks(): List<JSONObject> = arrayOf("userBookmarks")

    fun settingKeys(): List<String> = settings().map { it.getString("key") }

    fun isPrettyPrinted(): Boolean = text.contains("\n") && text.contains("  ")

    fun hasByteOrderMark(): Boolean = ArtifactBytes.startsWith(file, ArtifactBytes.UTF8_BOM)

    fun containsText(needle: String): Boolean =
        ArtifactBytes.indexOf(bytes, needle.toByteArray(Charsets.UTF_8)) >= 0

    fun textOccurrences(needle: String): Int =
        ArtifactBytes.occurrences(bytes, needle.toByteArray(Charsets.UTF_8))

    fun hasNullValuedMember(): Boolean = anyNullIn(root)

    private fun arrayOf(member: String): List<JSONObject> {
        val array: JSONArray = root.optJSONArray(member) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun anyNullIn(node: Any?): Boolean = when (node) {
        is JSONObject -> node.keys().asSequence().any { key ->
            node.isNull(key) || anyNullIn(node.opt(key))
        }
        is JSONArray -> (0 until node.length()).any { index ->
            node.isNull(index) || anyNullIn(node.opt(index))
        }
        else -> false
    }
}
