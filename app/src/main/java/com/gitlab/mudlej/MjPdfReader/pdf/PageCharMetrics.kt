// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

class PageCharMetrics(
    val count: Int,
    val left: FloatArray,
    val bottom: FloatArray,
    val right: FloatArray,
    val top: FloatArray,
    val fontSize: FloatArray,
    val fontWeight: IntArray,
    val codePoint: IntArray,
    val angleRadians: FloatArray,
    val fontFlags: IntArray,
) {
    companion object {
        private const val VALUES_PER_CHAR = 9

        fun fromRaw(values: DoubleArray): PageCharMetrics? {
            if (values.isEmpty() || values.size % VALUES_PER_CHAR != 0) return null

            val count = values.size / VALUES_PER_CHAR
            val left = FloatArray(count)
            val bottom = FloatArray(count)
            val right = FloatArray(count)
            val top = FloatArray(count)
            val fontSize = FloatArray(count)
            val fontWeight = IntArray(count)
            val codePoint = IntArray(count)
            val angleRadians = FloatArray(count)
            val fontFlags = IntArray(count)
            for (i in 0 until count) {
                val base = i * VALUES_PER_CHAR
                left[i] = values[base].toFloat()
                bottom[i] = values[base + 1].toFloat()
                right[i] = values[base + 2].toFloat()
                top[i] = values[base + 3].toFloat()
                fontSize[i] = values[base + 4].toFloat()
                fontWeight[i] = values[base + 5].toInt()
                codePoint[i] = values[base + 6].toInt()
                angleRadians[i] = values[base + 7].toFloat()
                fontFlags[i] = values[base + 8].toInt()
            }
            return PageCharMetrics(count, left, bottom, right, top, fontSize, fontWeight, codePoint, angleRadians, fontFlags)
        }
    }
}
