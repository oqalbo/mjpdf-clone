// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

data class PdfRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (bottom + top) / 2f
    val width: Float get() = right - left
    val height: Float get() = top - bottom
}

object AnnotationFixtures {

    const val NORMAL = "normal.pdf"
    const val ARABIC = "arabic-rtl.pdf"
    const val LARGE = "large.pdf"
    const val MJ_AUTHORED = "highlights_mj_authored.pdf"
    const val FOREIGN = "highlights_foreign.pdf"
    const val FORMS = "forms_all_field_types.pdf"

    const val NORMAL_PAGES = 20
    const val ARABIC_PAGES = 12
    const val LARGE_PAGES = 500

    const val PAGE_WIDTH_POINTS = 612f
    const val PAGE_HEIGHT_POINTS = 792f

    const val TEXT_LEFT = 72f
    const val TEXT_COLUMN_WIDTH = 468f

    const val ANNOTATION_BODY_TOP = 700f
    const val ANNOTATION_BODY_LEADING = 16f
    const val ANNOTATION_ASCENT_ABOVE_BASELINE = 12.21f
    const val ANNOTATION_DESCENT_BELOW_BASELINE = 4.59f

    const val NORMAL_BODY_TOP = 702f
    const val NORMAL_BODY_LEADING = 15f

    const val SINGLE_LINE_GROUP = "mj-single"
    const val SINGLE_LINE_NOTE = "a note on the single line highlight"
    const val SINGLE_LINE_INDEX = 3

    const val NO_QUOTE_INDEX = 12

    const val FOREIGN_LINE_INDEX = 3

    val singleLineRect: PdfRect get() = textLineRect(SINGLE_LINE_INDEX)
    val noQuoteRect: PdfRect get() = textLineRect(NO_QUOTE_INDEX)
    val foreignRect: PdfRect get() = textLineRect(FOREIGN_LINE_INDEX)

    const val FIELD_FULL_NAME = "fullname"
    const val FIELD_SUBSCRIBE = "subscribe"
    const val FIELD_PLAN = "plan"
    const val FIELD_COMMENTS = "comments"
    const val FIELD_REFERENCE = "reference"

    const val FULL_NAME_ALTERNATE = "Full name"
    const val REFERENCE_VALUE = "PDF-2026-0142"

    val fullNameRect = PdfRect(200f, 666f, 460f, 688f)
    val subscribeRect = PdfRect(200f, 616f, 216f, 632f)
    val planFirstRect = PdfRect(200f, 566f, 216f, 582f)
    val commentsRect = PdfRect(200f, 462f, 460f, 528f)
    val referenceRect = PdfRect(200f, 406f, 460f, 428f)

    fun textLineRect(lineIndex: Int): PdfRect {
        val baseline = ANNOTATION_BODY_TOP - ANNOTATION_BODY_LEADING * lineIndex
        return PdfRect(
            TEXT_LEFT,
            baseline - ANNOTATION_DESCENT_BELOW_BASELINE,
            TEXT_LEFT + TEXT_COLUMN_WIDTH,
            baseline + ANNOTATION_ASCENT_ABOVE_BASELINE,
        )
    }

    const val LARGE_BODY_LEFT = 40f
    const val LARGE_BODY_TOP = 712f
    const val LARGE_BODY_LEADING = 9.2f
    const val LARGE_BODY_RIGHT = 572f

    fun largeBodyLineRect(lineIndex: Int): PdfRect {
        val baseline = LARGE_BODY_TOP - LARGE_BODY_LEADING * lineIndex
        return PdfRect(LARGE_BODY_LEFT, baseline - 1f, LARGE_BODY_RIGHT, baseline + 5f)
    }

    const val ARABIC_BODY_TOP = 692f
    const val ARABIC_BODY_LEADING = 18f
    const val ARABIC_RIGHT = 540f

    fun arabicBodyLineRect(lineIndex: Int): PdfRect {
        val baseline = ARABIC_BODY_TOP - ARABIC_BODY_LEADING * lineIndex
        return PdfRect(
            TEXT_LEFT,
            baseline - ANNOTATION_DESCENT_BELOW_BASELINE,
            ARABIC_RIGHT,
            baseline + ANNOTATION_ASCENT_ABOVE_BASELINE,
        )
    }

    fun normalBodyLineRect(lineIndex: Int): PdfRect {
        val baseline = NORMAL_BODY_TOP - NORMAL_BODY_LEADING * lineIndex
        return PdfRect(
            TEXT_LEFT,
            baseline - ANNOTATION_DESCENT_BELOW_BASELINE,
            TEXT_LEFT + TEXT_COLUMN_WIDTH,
            baseline + ANNOTATION_ASCENT_ABOVE_BASELINE,
        )
    }

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)
}
