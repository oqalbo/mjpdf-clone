// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data.annotation

import android.graphics.RectF
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AnnotationEditJournalLineTest {
    @Test
    fun addingAnAnnotationPreservesEveryJournalField() {
        val edit = AnnotationEdit.Add(7, "group-a", listOf(rect(1f, 2f, 30f, 40f), rect(-2f, 5f, 8f, 9f)),
            -16711936, "selected text", "D:20261103091500Z")
        val decoded = roundTrip(edit, "add") as AnnotationEdit.Add

        assertEquals(7, decoded.page)
        assertEquals("group-a", decoded.group)
        assertEquals(-16711936, decoded.color)
        assertEquals("selected text", decoded.contents)
        assertEquals("D:20261103091500Z", decoded.createdDate)
        assertEquals(2, decoded.rects.size)
        edit.rects.zip(decoded.rects).forEach { (expected, actual) -> assertRect(expected, actual) }
    }

    @Test
    fun recoloringPreservesTheTargetAndColor() {
        val decoded = roundTrip(AnnotationEdit.Recolor(8, "group-b", -65536), "recolor") as AnnotationEdit.Recolor

        assertEquals(8, decoded.page)
        assertEquals("group-b", decoded.group)
        assertEquals(-65536, decoded.color)
    }

    @Test
    fun settingANotePreservesItsModificationDateAndTarget() {
        val decoded = roundTrip(AnnotationEdit.SetNote(9, "group-c", "note text", "D:20261103100000Z"),
            "setNote") as AnnotationEdit.SetNote

        assertEquals(9, decoded.page)
        assertEquals("group-c", decoded.group)
        assertEquals("note text", decoded.note)
        assertEquals("D:20261103100000Z", decoded.modifiedDate)
    }

    @Test
    fun deletingPreservesThePageAndGroup() {
        val decoded = roundTrip(AnnotationEdit.Delete(10, "group-d"), "delete") as AnnotationEdit.Delete

        assertEquals(10, decoded.page)
        assertEquals("group-d", decoded.group)
    }

    @Test
    fun settingFieldTextPreservesItsIndexNameAndText() {
        val decoded = roundTrip(AnnotationEdit.SetFieldText(11, 4, "full name", "Reader"),
            "setFieldText") as AnnotationEdit.SetFieldText

        assertEquals(11, decoded.page)
        assertEquals(4, decoded.fieldIndex)
        assertEquals("full name", decoded.fieldName)
        assertEquals("Reader", decoded.text)
    }

    @Test
    fun settingFieldCheckedPreservesBothBooleanStates() {
        for (checked in listOf(true, false)) {
            val decoded = roundTrip(AnnotationEdit.SetFieldChecked(12, 5, "consent", checked),
                "setFieldChecked") as AnnotationEdit.SetFieldChecked

            assertEquals(12, decoded.page)
            assertEquals(5, decoded.fieldIndex)
            assertEquals("consent", decoded.fieldName)
            assertEquals(checked, decoded.checked)
        }
    }

    @Test
    fun addingASignaturePreservesItsRectangleAndSeparateStrokes() {
        val edit = AnnotationEdit.AddSignature(13, rect(2f, 3f, 80f, 90f),
            listOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), floatArrayOf(0.7f, 0.8f)), -16777216, 2.5f)
        val decoded = roundTrip(edit, "addSignature") as AnnotationEdit.AddSignature

        assertEquals(13, decoded.page)
        assertRect(edit.rect, decoded.rect)
        assertEquals(2, decoded.strokes.size)
        edit.strokes.zip(decoded.strokes).forEach { (expected, actual) -> assertArrayEquals(expected, actual, 0f) }
        assertEquals(-16777216, decoded.color)
        assertEquals(2.5f, decoded.strokeWidth, 0f)
    }

    @Test
    fun noteLineSeparatorsStayInsideOneJournalLine() {
        val note = "first\nsecond\rthird\u2028fourth"
        val line = AnnotationEdit.SetNote(2, "group", note).toJsonLine()

        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
        assertFalse(line.contains('\u2028'))
        assertEquals(note, (AnnotationEdit.fromJsonLine(line) as AnnotationEdit.SetNote).note)
    }

    @Test
    fun aTruncatedJournalLineIsDiscarded() {
        val complete = AnnotationEdit.Add(2, "group", listOf(rect(1f, 2f, 3f, 4f)), 42, "text").toJsonLine()

        assertNull(AnnotationEdit.fromJsonLine(complete.dropLast(3)))
    }

    @Test
    fun anUnknownOperationIsDiscarded() {
        assertNull(AnnotationEdit.fromJsonLine("""{"op":"futureOperation","page":4}"""))
    }

    @Test
    fun aMissingOrExplicitlyNullCreatedDateDecodesAsNull() {
        val fields = """"op":"add","page":2,"group":"g","color":42,"contents":"text","rects":[]"""
        for (line in listOf("{$fields}", "{$fields,\"created\":null}")) {
            val decoded = AnnotationEdit.fromJsonLine(line) as AnnotationEdit.Add
            assertNull(decoded.createdDate)
            assertEquals(2, decoded.page)
        }
    }

    @Test
    fun nanCoordinatesSurviveBothRectangleDecodersAndSignatureStrokes() {
        val add = AnnotationEdit.Add(3, "group", listOf(rect(Float.NaN, 2f, 3f, 4f)), 42, "text")
        val decodedAdd = roundTrip(add, "add") as AnnotationEdit.Add
        assertTrue(decodedAdd.rects.single().left.isNaN())
        assertRect(add.rects.single(), decodedAdd.rects.single())

        val signature = AnnotationEdit.AddSignature(4, rect(1f, Float.NaN, 3f, 4f),
            listOf(floatArrayOf(Float.NaN, 0.5f)), 43, 1f)
        val decodedSignature = roundTrip(signature, "addSignature") as AnnotationEdit.AddSignature
        assertTrue(decodedSignature.rect.top.isNaN())
        assertRect(signature.rect, decodedSignature.rect)
        assertTrue(decodedSignature.strokes.single()[0].isNaN())
        assertEquals(0.5f, decodedSignature.strokes.single()[1], 0f)
    }

    private fun roundTrip(edit: AnnotationEdit, operation: String): AnnotationEdit {
        val line = edit.toJsonLine()
        assertEquals(operation, JsonParser.parseString(line).asJsonObject.get("op").asString)
        return requireNotNull(AnnotationEdit.fromJsonLine(line))
    }

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) = RectF().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    private fun assertRect(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0f)
        assertEquals(expected.top, actual.top, 0f)
        assertEquals(expected.right, actual.right, 0f)
        assertEquals(expected.bottom, actual.bottom, 0f)
    }
}
