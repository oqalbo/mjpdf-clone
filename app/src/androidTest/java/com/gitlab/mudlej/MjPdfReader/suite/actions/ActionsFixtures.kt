// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object ActionsFixtures {

    const val NORMAL = "normal.pdf"
    const val KNOWN_TEXT = "known-text.pdf"
    const val KNOWN_TEXT_GROUND_TRUTH = "known-text-page1.txt"
    const val EIGHT_PAGE = "eight-page.pdf"
    const val NO_LINKS = "no-links.pdf"
    const val SCANNED = "scanned.pdf"
    const val PROTECTED = "protected.pdf"
    const val CORRUPT = "corrupt.pdf"
    const val TOC = "toc.pdf"
    const val LARGE = "large.pdf"
    const val HIGHLIGHTS = "highlights.pdf"

    const val NORMAL_PAGES = 20
    const val EIGHT_PAGE_PAGES = 8
    const val PROTECTED_PAGES = 6

    const val PROTECTED_PASSWORD = "mjpdf"

    const val KNOWN_TEXT_PAGE_1 = "The keeper signed the ledger at the end of every watch, without fail."

    const val TOC_FIRST_ENTRY = "Introduction"
    const val TOC_RESULTS_ENTRY = "Results"
    const val TOC_RESULTS_PAGE_INDEX = 42
    const val TOC_TOP_LEVEL_ENTRIES = 6

    val seededRoot: File = File(Fixtures.externalRoot, "Download")

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))

    fun screenshotPrefix(fixtureName: String): String = fixtureName.removeSuffix(PDF_SUFFIX)

    fun groundTruthPageText(): String {
        val text = file(KNOWN_TEXT_GROUND_TRUTH).readText().trim()
        if (text != KNOWN_TEXT_PAGE_1) {
            throw AssertionError(
                "The committed ground truth in ${KNOWN_TEXT_GROUND_TRUTH} reads \"$text\" but this suite expects " +
                    "\"$KNOWN_TEXT_PAGE_1\". One of the two changed without the other. The fixture on device is the " +
                    "authority, so regenerate the fixture or update ActionsFixtures.KNOWN_TEXT_PAGE_1 to match it."
            )
        }
        return text
    }

    private const val PDF_SUFFIX = ".pdf"
}
