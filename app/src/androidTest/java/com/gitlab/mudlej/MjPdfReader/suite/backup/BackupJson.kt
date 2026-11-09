// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupJson(val raw: String) {
    val root: JSONObject = JSONObject(raw)

    val schemaVersion: Int get() = root.getInt("schemaVersion")

    val appVersionCode: Long get() = root.getLong("appVersionCode")

    val exportedAt: String get() = root.getString("exportedAt")

    val memberNames: List<String> get() = root.keys().asSequence().toList()

    fun has(member: String): Boolean = root.has(member)

    val settings: List<JSONObject>? get() = objects("settings")

    val records: List<JSONObject>? get() = objects("pdfRecords")

    val bookmarks: List<JSONObject>? get() = objects("userBookmarks")

    fun settingKeys(): List<String> = settings.orEmpty().map { it.getString("key") }

    fun setting(key: String): JSONObject? = settings.orEmpty().firstOrNull { it.optString("key") == key }

    fun recordHashes(): List<String> = records.orEmpty().map { it.getString("hash") }

    fun record(hash: String): JSONObject? =
        records.orEmpty().firstOrNull { it.optString("hash") == hash }

    fun containsText(fragment: String): Boolean = raw.contains(fragment)

    private fun objects(member: String): List<JSONObject>? {
        if (!root.has(member)) return null
        val array: JSONArray = root.getJSONArray(member)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    companion object {
        fun of(file: File): BackupJson = BackupJson(file.readText(Charsets.UTF_8))

        fun stringSet(json: JSONObject, member: String): List<String> {
            if (!json.has(member)) return emptyList()
            val array = json.getJSONArray(member)
            return (0 until array.length()).map { array.getString(it) }
        }
    }
}
