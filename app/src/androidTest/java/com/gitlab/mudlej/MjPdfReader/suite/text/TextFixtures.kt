// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File

object TextFixtures {

    const val NORMAL = "normal.pdf"
    const val SCANNED = "scanned.pdf"
    const val PROTECTED = "protected.pdf"
    const val ARABIC_RTL = "arabic-rtl.pdf"
    const val LARGE = "large.pdf"
    const val TOC = "toc.pdf"
    const val STRUCTURED = "text-structured.pdf"
    const val SINGLE_PAGE = "text-single-page.pdf"

    const val NORMAL_PAGES = 20
    const val SCANNED_PAGES = 8
    const val PROTECTED_PAGES = 6
    const val ARABIC_RTL_PAGES = 12
    const val LARGE_PAGES = 500
    const val TOC_PAGES = 60
    const val STRUCTURED_PAGES = 4
    const val SINGLE_PAGE_PAGES = 1

    const val PROTECTED_PASSWORD = "mjpdf"

    const val TOC_RESULTS_TITLE = "Results"
    const val TOC_RESULTS_PAGE_INDEX = 42

    const val STRUCTURED_HEADING = "Chapter One"
    const val STRUCTURED_HYPHEN_WORD = "extraordinary"
    const val STRUCTURED_HYPHEN_HEAD = "extraor-"
    const val STRUCTURED_LIGATURE_WORD = "magnificent"

    const val STRUCTURED_HEADING_PAGE = 0
    const val STRUCTURED_LISTS_PAGE = 1
    const val STRUCTURED_HYPHEN_PAGE = 2
    const val STRUCTURED_LIGATURE_PAGE = 3

    val STRUCTURED_BULLETS = listOf(
        "• Trim the wick before the light is lit, never after.",
        "• Enter the weather at midnight and again at four.",
        "• Never leave the lamp room while the lamp is burning.",
    )

    val STRUCTURED_NUMBERED = listOf(
        "1. Sign the ledger at the end of every watch.",
        "2. Report a cracked lens to the board the same day.",
    )

    val STRUCTURED_CODE_LINES = listOf(
        "sum = 0",
        "for value in readings:",
        "sum += value",
        "print(sum)",
    )

    const val STRUCTURED_DEEPEST_CODE_LINE = "sum += value"
    const val STRUCTURED_CODE_INDENT_SPACES = 4

    const val ARABIC_SHAPING_PAGE = 2

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))

    fun launchReader(name: String): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(uri(name)))
}
