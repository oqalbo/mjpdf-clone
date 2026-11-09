// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JournalLine(val raw: String, private val json: JSONObject) {

    val op: String get() = json.getString("op")
    val page: Int get() = json.getInt("page")
    val group: String? get() = json.optString("group").takeIf { json.has("group") }
    val color: Int? get() = if (json.has("color")) json.getInt("color") else null
    val contents: String? get() = if (json.has("contents")) json.getString("contents") else null
    val note: String? get() = if (json.has("note")) json.getString("note") else null
    val created: String? get() = if (json.has("created")) json.getString("created") else null
    val modified: String? get() = if (json.has("modified")) json.getString("modified") else null
    val fieldIndex: Int? get() = if (json.has("fieldIndex")) json.getInt("fieldIndex") else null
    val fieldName: String? get() = if (json.has("fieldName")) json.getString("fieldName") else null
    val text: String? get() = if (json.has("text")) json.getString("text") else null
    val checked: Boolean? get() = if (json.has("checked")) json.getBoolean("checked") else null
    val strokeWidth: Float? get() = if (json.has("strokeWidth")) json.getDouble("strokeWidth").toFloat() else null

    val rects: List<List<Float>>
        get() = floatRows(json.optJSONArray("rects"))

    val rect: List<Float>
        get() = floatRow(json.optJSONArray("rect"))

    val strokes: List<List<Float>>
        get() = floatRows(json.optJSONArray("strokes"))

    private fun floatRows(array: JSONArray?): List<List<Float>> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index -> floatRow(array.getJSONArray(index)) }
    }

    private fun floatRow(array: JSONArray?): List<Float> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index -> array.getDouble(index).toFloat() }
    }

    override fun toString(): String = raw
}

object AnnotationJournalFile {

    const val OP_ADD = "add"
    const val OP_RECOLOR = "recolor"
    const val OP_SET_NOTE = "setNote"
    const val OP_DELETE = "delete"
    const val OP_SET_FIELD_TEXT = "setFieldText"
    const val OP_SET_FIELD_CHECKED = "setFieldChecked"
    const val OP_ADD_SIGNATURE = "addSignature"

    const val STABLE_WINDOW_MS = 1_500L

    fun directory(): File = File(TestEnvironment.targetContext.filesDir, "annotations")

    fun fileFor(documentUri: String): File =
        File(directory(), "${SourceKey.of(Uri.parse(documentUri))}.journal")

    fun fileOfOpenDocument(reader: ReaderRobot): File =
        fileFor(documentUriOfOpenDocument(reader))

    fun documentUriOfOpenDocument(reader: ReaderRobot): String =
        reader.awaitState("the reader reports the document uri it is using") {
            !it.documentUri.isNullOrEmpty()
        }.documentUri!!

    fun lineCount(file: File): Int = if (file.isFile) rawLines(file).size else 0

    fun rawLines(file: File): List<String> =
        if (file.isFile) file.readLines().filter { it.isNotBlank() } else emptyList()

    fun lines(file: File): List<JournalLine> = rawLines(file).map { raw ->
        JournalLine(raw, JSONObject(raw))
    }

    fun awaitLineCount(file: File, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<JournalLine> {
        Await.untilValue(
            "the annotation journal ${file.name} holds $expected lines",
            timeoutMs,
            read = { lineCount(file) },
        ) { it == expected }
        return lines(file)
    }

    fun awaitAbsent(file: File, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the annotation journal ${file.name} is gone", timeoutMs) { !file.exists() }
    }

    fun assertStaysAbsent(file: File, windowMs: Long = STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the annotation journal ${file.name} is never created",
            windowMs = windowMs,
        ) { lineCount(file) == 0 }
    }

    fun assertLineCountStays(file: File, expected: Int, windowMs: Long = STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the annotation journal ${file.name} stays at $expected lines",
            windowMs = windowMs,
        ) { lineCount(file) == expected }
    }

    fun files(): List<File> = directory().listFiles()?.toList().orEmpty()

    fun endsWithNewline(file: File): Boolean = file.readBytes().lastOrNull() == '\n'.code.toByte()
}
