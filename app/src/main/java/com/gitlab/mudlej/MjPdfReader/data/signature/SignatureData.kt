// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.signature

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class SignatureData(
    val strokes: List<FloatArray>,
    val aspect: Float,
    val strokeWidth: Float,
    val color: Int,
) {

    fun toNativeStrokes(): Array<FloatArray> = strokes.toTypedArray()

    fun toJson(): String {
        val json = JsonObject()
        json.addProperty(KEY_ASPECT, aspect)
        json.addProperty(KEY_STROKE_WIDTH, strokeWidth)
        json.addProperty(KEY_COLOR, color)
        val strokesArray = JsonArray()
        for (stroke in strokes) {
            val values = JsonArray()
            for (value in stroke) {
                values.add(value)
            }
            strokesArray.add(values)
        }
        json.add(KEY_STROKES, strokesArray)
        return json.toString()
    }

    companion object {
        private const val KEY_ASPECT = "aspect"
        private const val KEY_STROKE_WIDTH = "strokeWidth"
        private const val KEY_COLOR = "color"
        private const val KEY_STROKES = "strokes"

        fun fromJson(text: String): SignatureData? = runCatching {
            val json = JsonParser.parseString(text).asJsonObject
            val strokes = json.getAsJsonArray(KEY_STROKES).map { element ->
                val values = element.asJsonArray
                FloatArray(values.size()) { index -> values.get(index).asFloat }
            }
            if (strokes.isEmpty()) {
                return@runCatching null
            }
            SignatureData(
                strokes = strokes,
                aspect = json.get(KEY_ASPECT).asFloat,
                strokeWidth = json.get(KEY_STROKE_WIDTH).asFloat,
                color = json.get(KEY_COLOR).asInt,
            )
        }.getOrNull()
    }
}
