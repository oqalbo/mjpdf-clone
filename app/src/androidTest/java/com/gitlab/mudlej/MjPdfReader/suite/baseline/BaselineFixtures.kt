// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.annotations.PdfRect
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.FixtureNames
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object BaselineFixtures {

    const val GOLDEN_BOOK = "TLCL-25.12A.pdf"
    const val GOLDEN_BOOK_PAGES = 597
    const val BOOK_BODY_PAGE_INDEX = 40
    const val BOOK_SECOND_BODY_PAGE_INDEX = 60
    const val BOOK_DEEP_PAGE_INDEX = 249
    const val BOOK_SEARCH_PAGE_INDEX = 40
    const val BOOK_SEARCH_QUERY = "JFIF"
    const val BOOK_RETURN_TRIP_INDEX = 4
    const val BOOK_WRONG_PAGE_INDEX = 7

    const val ARABIC = FixtureNames.ARABIC
    const val SCANNED = FixtureNames.SCANNED
    const val FORMS = "forms.pdf"
    const val FORM_VALUES = "formvalues.pdf"
    const val LINKS = FixtureNames.LINKS
    const val LIGATURES = "ligatures.pdf"
    const val MARGINS = "margins.pdf"
    const val PALETTE = "palette.pdf"
    const val SPANNING = "spanning.pdf"
    const val SIGNATURE_SEED = "signature.json"

    const val ARABIC_PAGES = 12
    const val SCANNED_PAGES = 8

    const val ARABIC_MID_PAGE_INDEX = 8
    const val SCANNED_MID_PAGE_INDEX = 6

    const val LIGATURE_QUERY = "office"

    val PALETTE_COLOURS = listOf(
        0xFFFFF176.toInt(),
        0xFFFFB74D.toInt(),
        0xFF81C784.toInt(),
        0xFF64B5F6.toInt(),
        0xFFF06292.toInt(),
        0xFFB39DDB.toInt(),
    )

    val PALETTE_RECTANGLES = listOf(
        PdfRect(72.0f, 663.41f, 334.23f, 680.21f),
        PdfRect(72.0f, 615.41f, 345.89f, 632.21f),
        PdfRect(72.0f, 567.41f, 336.15f, 584.21f),
        PdfRect(72.0f, 519.41f, 325.69f, 536.21f),
        PdfRect(72.0f, 471.41f, 334.85f, 488.21f),
        PdfRect(72.0f, 423.41f, 306.21f, 440.21f),
    )

    const val SPANNING_EXPECTED_RECTANGLES = 3

    const val SPANNING_SENTENCE =
        "The sentence that follows is deliberately long enough to wrap across three rendered lines, so that a " +
            "selection over the whole of it produces three separate highlight rectangles rather than one " +
            "single tidy box."

    const val MIXED_SCRIPT_LTR_QUOTE =
        "The manuscript uses the word الكتاب for a bound volume throughout. The translator kept it in the " +
            "original script on every page, and set a note in the margin the first time a reader would meet it."

    const val MAX_QUOTE_CHARACTERS = 400

    const val QUOTE_CARD_SIZE = 1080

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)

    fun seedSignature(targetDirectory: File): File {
        val source = Fixtures.seededFixture(SIGNATURE_SEED)
        if (!source.isFile) {
            throw AssertionError(
                "The signature seed ${source.absolutePath} is not on the device. python3 testing/runner/run.py pushes " +
                    "testing/fixtures/out into that directory before the suite starts."
            )
        }
        targetDirectory.mkdirs()
        val target = File(targetDirectory, "signature.json")
        target.writeBytes(source.readBytes())
        return target
    }
}
