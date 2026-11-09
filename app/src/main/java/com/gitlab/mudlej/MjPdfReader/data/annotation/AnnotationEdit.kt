// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.annotation

import android.graphics.RectF
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed class AnnotationEdit {

    abstract val page: Int

    data class Add(
        override val page: Int,
        val group: String,
        val rects: List<RectF>,
        val color: Int,
        val contents: String,
        val createdDate: String? = null,
    ) : AnnotationEdit()

    data class Recolor(
        override val page: Int,
        val group: String,
        val color: Int,
    ) : AnnotationEdit()

    data class SetNote(
        override val page: Int,
        val group: String,
        val note: String,
        val modifiedDate: String? = null,
    ) : AnnotationEdit()

    data class Delete(
        override val page: Int,
        val group: String,
    ) : AnnotationEdit()

    data class SetFieldText(
        override val page: Int,
        val fieldIndex: Int,
        val fieldName: String,
        val text: String,
    ) : AnnotationEdit()

    data class SetFieldChecked(
        override val page: Int,
        val fieldIndex: Int,
        val fieldName: String,
        val checked: Boolean,
    ) : AnnotationEdit()

    data class AddSignature(
        override val page: Int,
        val rect: RectF,
        val strokes: List<FloatArray>,
        val color: Int,
        val strokeWidth: Float,
    ) : AnnotationEdit()

    fun toJsonLine(): String {
        val json = JsonObject()
        json.addProperty(KEY_OP, opName())
        json.addProperty(KEY_PAGE, page)
        when (this) {
            is Add -> {
                json.addProperty(KEY_GROUP, group)
                json.addProperty(KEY_COLOR, color)
                json.addProperty(KEY_CONTENTS, contents)
                json.add(KEY_RECTS, rectsToJson(rects))
                createdDate?.let { json.addProperty(KEY_CREATED, it) }
            }
            is Recolor -> {
                json.addProperty(KEY_GROUP, group)
                json.addProperty(KEY_COLOR, color)
            }
            is SetNote -> {
                json.addProperty(KEY_GROUP, group)
                json.addProperty(KEY_NOTE, note)
                modifiedDate?.let { json.addProperty(KEY_MODIFIED, it) }
            }
            is Delete -> json.addProperty(KEY_GROUP, group)
            is SetFieldText -> {
                json.addProperty(KEY_FIELD_INDEX, fieldIndex)
                json.addProperty(KEY_FIELD_NAME, fieldName)
                json.addProperty(KEY_TEXT, text)
            }
            is SetFieldChecked -> {
                json.addProperty(KEY_FIELD_INDEX, fieldIndex)
                json.addProperty(KEY_FIELD_NAME, fieldName)
                json.addProperty(KEY_CHECKED, checked)
            }
            is AddSignature -> {
                json.addProperty(KEY_COLOR, color)
                json.addProperty(KEY_STROKE_WIDTH, strokeWidth)
                json.add(KEY_RECT, rectToJson(rect))
                json.add(KEY_STROKES, strokesToJson(strokes))
            }
        }
        return json.toString()
    }

    private fun opName(): String = when (this) {
        is Add -> OP_ADD
        is Recolor -> OP_RECOLOR
        is SetNote -> OP_SET_NOTE
        is Delete -> OP_DELETE
        is SetFieldText -> OP_SET_FIELD_TEXT
        is SetFieldChecked -> OP_SET_FIELD_CHECKED
        is AddSignature -> OP_ADD_SIGNATURE
    }

    companion object {
        private const val KEY_OP = "op"
        private const val KEY_PAGE = "page"
        private const val KEY_GROUP = "group"
        private const val KEY_COLOR = "color"
        private const val KEY_CONTENTS = "contents"
        private const val KEY_RECTS = "rects"
        private const val KEY_CREATED = "created"
        private const val KEY_NOTE = "note"
        private const val KEY_MODIFIED = "modified"
        private const val KEY_FIELD_INDEX = "fieldIndex"
        private const val KEY_FIELD_NAME = "fieldName"
        private const val KEY_TEXT = "text"
        private const val KEY_CHECKED = "checked"
        private const val OP_ADD = "add"
        private const val OP_RECOLOR = "recolor"
        private const val OP_SET_NOTE = "setNote"
        private const val OP_DELETE = "delete"
        private const val KEY_RECT = "rect"
        private const val KEY_STROKES = "strokes"
        private const val KEY_STROKE_WIDTH = "strokeWidth"
        private const val OP_SET_FIELD_TEXT = "setFieldText"
        private const val OP_SET_FIELD_CHECKED = "setFieldChecked"
        private const val OP_ADD_SIGNATURE = "addSignature"

        fun fromJsonLine(line: String): AnnotationEdit? = runCatching {
            val json = JsonParser.parseString(line).asJsonObject
            val page = json.get(KEY_PAGE).asInt
            when (json.get(KEY_OP).asString) {
                OP_ADD -> Add(
                    page = page,
                    group = json.get(KEY_GROUP).asString,
                    rects = rectsFromJson(json.getAsJsonArray(KEY_RECTS)),
                    color = json.get(KEY_COLOR).asInt,
                    contents = json.get(KEY_CONTENTS).asString,
                    createdDate = optionalString(json, KEY_CREATED),
                )
                OP_RECOLOR -> Recolor(
                    page = page,
                    group = json.get(KEY_GROUP).asString,
                    color = json.get(KEY_COLOR).asInt,
                )
                OP_SET_NOTE -> SetNote(
                    page = page,
                    group = json.get(KEY_GROUP).asString,
                    note = json.get(KEY_NOTE).asString,
                    modifiedDate = optionalString(json, KEY_MODIFIED),
                )
                OP_DELETE -> Delete(page = page, group = json.get(KEY_GROUP).asString)
                OP_SET_FIELD_TEXT -> SetFieldText(
                    page = page,
                    fieldIndex = json.get(KEY_FIELD_INDEX).asInt,
                    fieldName = json.get(KEY_FIELD_NAME).asString,
                    text = json.get(KEY_TEXT).asString,
                )
                OP_SET_FIELD_CHECKED -> SetFieldChecked(
                    page = page,
                    fieldIndex = json.get(KEY_FIELD_INDEX).asInt,
                    fieldName = json.get(KEY_FIELD_NAME).asString,
                    checked = json.get(KEY_CHECKED).asBoolean,
                )
                OP_ADD_SIGNATURE -> AddSignature(
                    page = page,
                    rect = rectFromJson(json.getAsJsonArray(KEY_RECT)),
                    strokes = strokesFromJson(json.getAsJsonArray(KEY_STROKES)),
                    color = json.get(KEY_COLOR).asInt,
                    strokeWidth = json.get(KEY_STROKE_WIDTH).asFloat,
                )
                else -> null
            }
        }.getOrNull()

        private fun optionalString(json: JsonObject, key: String): String? {
            val element = json.get(key) ?: return null
            return if (element.isJsonNull) null else element.asString
        }

        private fun rectsToJson(rects: List<RectF>): JsonArray {
            val array = JsonArray()
            for (rect in rects) {
                val values = JsonArray()
                values.add(rect.left)
                values.add(rect.top)
                values.add(rect.right)
                values.add(rect.bottom)
                array.add(values)
            }
            return array
        }

        private fun rectsFromJson(array: JsonArray): List<RectF> {
            return array.map { element ->
                val values = element.asJsonArray
                RectF().apply {
                    left = values.get(0).asFloat
                    top = values.get(1).asFloat
                    right = values.get(2).asFloat
                    bottom = values.get(3).asFloat
                }
            }
        }

        private fun rectToJson(rect: RectF): JsonArray {
            val values = JsonArray()
            values.add(rect.left)
            values.add(rect.top)
            values.add(rect.right)
            values.add(rect.bottom)
            return values
        }

        private fun rectFromJson(values: JsonArray): RectF {
            return RectF().apply {
                left = values.get(0).asFloat
                top = values.get(1).asFloat
                right = values.get(2).asFloat
                bottom = values.get(3).asFloat
            }
        }

        private fun strokesToJson(strokes: List<FloatArray>): JsonArray {
            val array = JsonArray()
            for (stroke in strokes) {
                val values = JsonArray()
                for (value in stroke) {
                    values.add(value)
                }
                array.add(values)
            }
            return array
        }

        private fun strokesFromJson(array: JsonArray): List<FloatArray> {
            return array.map { element ->
                val values = element.asJsonArray
                FloatArray(values.size()) { index -> values.get(index).asFloat }
            }
        }
    }
}
