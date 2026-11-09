// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.shockwave.pdfium.PdfDocument
import org.json.JSONArray
import org.json.JSONObject

data class HighlightGroup(
    val group: String,
    val color: Int,
    val quote: String,
    val note: String,
    val index: Int,
    val rects: Int,
)

data class FormFieldSnapshot(
    val page: Int,
    val index: Int,
    val type: Int,
    val name: String,
    val value: String,
    val checked: Boolean,
    val readOnly: Boolean,
) {
    val isTextField: Boolean get() = type == PdfDocument.FormField.TYPE_TEXT_FIELD
    val isCheckbox: Boolean get() = type == PdfDocument.FormField.TYPE_CHECKBOX
    val isRadioButton: Boolean get() = type == PdfDocument.FormField.TYPE_RADIO_BUTTON
}

data class PendingStamp(
    val page: Int,
    val rect: PdfRect,
    val color: Int,
    val strokeWidth: Float,
)

object AnnotationDump {

    const val FIELD_PROBE_LIMIT = 16

    fun highlights(pageIndex: Int): List<HighlightGroup> {
        val array = JSONArray(readerDebug().dumpPageHighlights(pageIndex))
        return (0 until array.length()).map { position ->
            val entry = array.getJSONObject(position)
            HighlightGroup(
                group = entry.getString("group"),
                color = entry.getInt("color"),
                quote = entry.optString("quote"),
                note = entry.optString("note"),
                index = entry.getInt("index"),
                rects = entry.getInt("rects"),
            )
        }
    }

    fun highlightGroup(pageIndex: Int, group: String): HighlightGroup? =
        highlights(pageIndex).firstOrNull { it.group == group }

    fun requireHighlightGroup(pageIndex: Int, group: String): HighlightGroup =
        highlightGroup(pageIndex, group)
            ?: throw AssertionError(
                "Page $pageIndex carries no highlight group \"$group\". The live dump held " +
                    "${highlights(pageIndex).map { it.group }}"
            )

    fun awaitHighlightGroups(pageIndex: Int, expected: Int, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): List<HighlightGroup> =
        Await.untilValue(
            "page $pageIndex reports $expected highlight groups",
            timeoutMs,
            read = { highlights(pageIndex) },
        ) { it.size == expected }

    fun awaitHighlightGroup(
        pageIndex: Int,
        group: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        predicate: (HighlightGroup) -> Boolean,
    ): HighlightGroup = Await.untilValue(
        "the highlight group \"$group\" on page $pageIndex reaches the expected state",
        timeoutMs,
        read = { highlightGroup(pageIndex, group) },
    ) { it != null && predicate(it) }!!

    fun formFields(pageIndex: Int): List<FormFieldSnapshot> =
        (0 until FIELD_PROBE_LIMIT).mapNotNull { index -> formField(pageIndex, index) }

    fun formField(pageIndex: Int, annotationIndex: Int): FormFieldSnapshot? {
        val raw = readerDebug().dumpFormField(pageIndex, annotationIndex)
        val json = JSONObject(raw)
        if (!json.has("index")) return null
        return FormFieldSnapshot(
            page = json.getInt("page"),
            index = json.getInt("index"),
            type = json.getInt("type"),
            name = json.getString("name"),
            value = json.getString("value"),
            checked = json.getBoolean("checked"),
            readOnly = json.getBoolean("readOnly"),
        )
    }

    fun formFieldNamed(pageIndex: Int, name: String): FormFieldSnapshot =
        formFields(pageIndex).firstOrNull { it.name == name }
            ?: throw ConditionTimeoutException(
                "Page $pageIndex carries no form field named \"$name\". The probe found " +
                    "${formFields(pageIndex).map { it.name }}"
            )

    fun awaitFormFieldNamed(
        pageIndex: Int,
        name: String,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
        predicate: (FormFieldSnapshot) -> Boolean,
    ): FormFieldSnapshot = Await.untilValue(
        "the form field \"$name\" on page $pageIndex reaches the expected state",
        timeoutMs,
        read = { runCatching { formFieldNamed(pageIndex, name) }.getOrNull() },
    ) { it != null && predicate(it) }!!

    fun radioControls(pageIndex: Int, name: String): List<FormFieldSnapshot> =
        formFields(pageIndex).filter { it.name == name }

    fun pendingStamp(): PendingStamp? {
        val json = JSONObject(TestEnvironment.onMainThread { readerDebug().dumpPendingStamp() })
        if (!json.has("rect")) return null
        val rect = json.getJSONArray("rect")
        return PendingStamp(
            page = json.getInt("page"),
            rect = PdfRect(
                left = rect.getDouble(0).toFloat(),
                bottom = rect.getDouble(3).toFloat(),
                right = rect.getDouble(2).toFloat(),
                top = rect.getDouble(1).toFloat(),
            ),
            color = json.getInt("color"),
            strokeWidth = json.getDouble("strokeWidth").toFloat(),
        )
    }

    fun requirePendingStamp(): PendingStamp = pendingStamp()
        ?: throw ConditionTimeoutException("No signature stamp is pending placement")

    fun awaitPendingStamp(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS): PendingStamp =
        Await.untilNotNull("a signature stamp is pending placement", timeoutMs) { pendingStamp() }

    fun awaitNoPendingStamp(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("no signature stamp is pending placement", timeoutMs) { pendingStamp() == null }
    }

    private fun readerDebug() = TestEnvironment.onMainThread {
        (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity)
            ?.annotationDebug()
            ?: throw ConditionTimeoutException("No resumed reader debug target")
    }
}
