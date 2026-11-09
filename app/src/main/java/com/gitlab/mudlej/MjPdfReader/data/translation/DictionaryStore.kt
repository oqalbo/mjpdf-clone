// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.translation

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

data class DictionarySense(
    val pos: String,
    val definition: String,
    val example: String?,
    val synonyms: String?,
)

data class DictionaryEntry(
    val lemma: String,
    val senses: List<DictionarySense>,
)

object DictionaryStore {

    private const val folderName = "dictionaries"
    private const val fileName = "en-wordnet.db"

    private val suffixRules = listOf(
        "ses" to "s", "xes" to "x", "zes" to "z", "ches" to "ch", "shes" to "sh",
        "ies" to "y", "men" to "man", "s" to "",
        "es" to "e", "es" to "", "ed" to "e", "ed" to "",
        "ing" to "e", "ing" to "",
        "er" to "", "er" to "e", "est" to "", "est" to "e",
    )

    fun file(context: Context): File = File(File(context.filesDir, folderName), fileName)

    fun isInstalled(context: Context): Boolean = file(context).exists()

    fun installedSize(context: Context): Long = file(context).length()

    fun delete(context: Context): Boolean = file(context).delete()

    fun lookup(context: Context, word: String): DictionaryEntry? {
        val dbFile = file(context).takeIf { it.exists() } ?: return null
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                candidates(db, normalize(word)).firstNotNullOfOrNull { candidate -> entryFor(db, candidate) }
            }
        }.getOrNull()
    }

    internal fun normalize(word: String): String {
        return word.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .removeSuffix("'s")
            .removeSuffix("'")
    }

    private fun candidates(db: SQLiteDatabase, word: String): List<String> {
        val exceptions = mutableListOf<String>()
        db.rawQuery("SELECT lemma FROM exceptions WHERE inflected = ?", arrayOf(word)).use { cursor ->
            while (cursor.moveToNext()) {
                exceptions.add(cursor.getString(0))
            }
        }
        return candidates(word, exceptions)
    }

    internal fun candidates(word: String, exceptions: List<String> = emptyList()): List<String> {
        val list = mutableListOf(word)
        list.addAll(exceptions)
        suffixRules.forEach { (suffix, replacement) ->
            if (word.endsWith(suffix) && word.length > suffix.length + 1) {
                list.add(word.dropLast(suffix.length) + replacement)
            }
        }
        return list.distinct()
    }

    private fun entryFor(db: SQLiteDatabase, lemma: String): DictionaryEntry? {
        val senses = mutableListOf<DictionarySense>()
        db.rawQuery(
            "SELECT pos, definition, example, synonyms FROM senses WHERE lemma = ? " +
                "ORDER BY CASE pos WHEN 'n' THEN 0 WHEN 'v' THEN 1 WHEN 'a' THEN 2 ELSE 3 END, senseNumber",
            arrayOf(lemma),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                senses.add(
                    DictionarySense(
                        pos = cursor.getString(0),
                        definition = cursor.getString(1),
                        example = cursor.getString(2),
                        synonyms = cursor.getString(3),
                    )
                )
            }
        }
        return if (senses.isEmpty()) null else DictionaryEntry(lemma, senses)
    }
}
