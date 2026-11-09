// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import android.database.sqlite.SQLiteDatabase
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryStore
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

data class SeededSense(
    val lemma: String,
    val pos: String,
    val definition: String,
    val example: String?,
    val synonyms: String?,
    val senseNumber: Int,
)

object SelectionDictionary {

    const val FOX = "fox"
    const val JUMP = "jump"
    const val ABSENT = "quick"

    const val FOX_SYNONYMS = "vixen, reynard"
    const val FOX_EXAMPLE = "the fox crossed the lane at dusk"

    val SEEDED_SENSES = listOf(
        SeededSense(FOX, "n", "a small wild dog with a bushy tail", FOX_EXAMPLE, null, 1),
        SeededSense(FOX, "n", "a person known for cunning", null, FOX_SYNONYMS, 2),
        SeededSense(FOX, "v", "to deceive or outwit", null, null, 1),
        SeededSense(JUMP, "v", "to push off the ground with both feet", null, null, 1),
    )

    val FOX_SENSES = SEEDED_SENSES.filter { it.lemma == FOX }

    fun file(): File = DictionaryStore.file(TestEnvironment.targetContext)

    fun directory(): File = file().parentFile!!

    fun isInstalled(): Boolean = DictionaryStore.isInstalled(TestEnvironment.targetContext)

    fun remove() {
        val directory = directory()
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
    }

    fun seed() {
        remove()
        val target = file()
        target.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(target.path, null)
        try {
            database.execSQL(
                "CREATE TABLE senses (lemma TEXT, pos TEXT, definition TEXT, example TEXT, " +
                    "synonyms TEXT, senseNumber INTEGER)"
            )
            database.execSQL("CREATE TABLE exceptions (inflected TEXT, lemma TEXT)")
            SEEDED_SENSES.forEach { sense ->
                database.execSQL(
                    "INSERT INTO senses (lemma, pos, definition, example, synonyms, senseNumber) VALUES (?,?,?,?,?,?)",
                    arrayOf<Any?>(
                        sense.lemma,
                        sense.pos,
                        sense.definition,
                        sense.example,
                        sense.synonyms,
                        sense.senseNumber,
                    ),
                )
            }
            database.execSQL("INSERT INTO exceptions (inflected, lemma) VALUES (?,?)", arrayOf("oxen", "ox"))
        } finally {
            database.close()
        }
        if (!isInstalled()) {
            throw AssertionError(
                "The seeded dictionary at ${target.absolutePath} does not exist after writing it, so " +
                    "DictionaryStore.isInstalled would report no dictionary and the branch under test never runs"
            )
        }
    }
}
