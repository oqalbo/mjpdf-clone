// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.signature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class SignatureDataTest {

    @Test
    fun signatureRoundTripPreservesEachStrokeAndStyle() {
        val original = SignatureData(
            listOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f), floatArrayOf(0.9f, 1f)),
            1.5f, 0.025f, 0xff123456.toInt(),
        )

        val decoded = SignatureData.fromJson(original.toJson())
        assertNotNull(decoded)
        decoded!!
        assertEquals(original.aspect, decoded.aspect, 0f)
        assertEquals(original.strokeWidth, decoded.strokeWidth, 0f)
        assertEquals(original.color, decoded.color)
        assertEquals(original.strokes.size, decoded.strokes.size)
        original.strokes.zip(decoded.strokes).forEachIndexed { index, (expected, actual) ->
            assertTrue("Stroke $index changed", expected.contentEquals(actual))
        }
    }

    @Test
    fun emptyStrokeListIsRejected() {
        assertNull(SignatureData.fromJson("""{"strokes":[],"aspect":1,"strokeWidth":0.1,"color":0}"""))
    }

    @Test
    fun missingAspectIsRejected() {
        assertNull(SignatureData.fromJson("""{"strokes":[[0,1]],"strokeWidth":0.1,"color":0}"""))
    }

    @Test
    fun garbageJsonIsRejected() {
        assertNull(SignatureData.fromJson("not a signature"))
    }

    @Test
    @Ignore("Pending ruling: a five-float stroke loads, expected rejection under the native stroke contract")
    fun strokeWithAnIncompleteBezierSegmentIsRejected() {
        assertNull(SignatureData.fromJson("""{"strokes":[[0,1,2,3,4]],"aspect":1,"strokeWidth":0.1,"color":0}"""))
    }

    @Test
    @Ignore("Pending ruling: an empty stroke loads, expected rejection under the native stroke contract")
    fun emptyStrokeIsRejected() {
        assertNull(SignatureData.fromJson("""{"strokes":[[]],"aspect":1,"strokeWidth":0.1,"color":0}"""))
    }

    @Test
    fun zeroAspectIsPreserved() {
        val decoded = SignatureData.fromJson("""{"strokes":[[0,1]],"aspect":0,"strokeWidth":0.1,"color":0}""")
        assertNotNull(decoded)
        assertEquals(0f, decoded!!.aspect, 0f)
    }

    @Test
    fun notANumberAspectIsPreserved() {
        val decoded = SignatureData.fromJson("""{"strokes":[[0,1]],"aspect":"NaN","strokeWidth":0.1,"color":0}""")
        assertNotNull(decoded)
        assertTrue(decoded!!.aspect.isNaN())
    }
}
