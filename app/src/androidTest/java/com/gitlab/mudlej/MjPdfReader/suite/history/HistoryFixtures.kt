// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object HistoryFixtures {

    const val ALPHA = "hist-alpha.pdf"
    const val BETA = "hist-beta.pdf"
    const val ALPHA_COPY = "hist-alpha-copy.pdf"
    const val TOC = "toc.pdf"
    const val LINKS = "links.pdf"
    const val LARGE = "large.pdf"
    const val PROTECTED = "protected.pdf"

    const val ALPHA_PAGES = 20
    const val BETA_PAGES = 12
    const val TOC_PAGES = 60
    const val LINKS_PAGES = 45
    const val LARGE_PAGES = 500
    const val PROTECTED_PAGES = 6

    const val PROTECTED_PASSWORD = "mjpdf"

    const val ALPHA_TITLE = "hist-alpha"
    const val BETA_TITLE = "hist-beta"
    const val ALPHA_COPY_TITLE = "hist-alpha-copy"

    const val LINK_ORIGIN_PAGE = 2
    const val LINK_DESTINATION_PAGE = 39
    const val SECOND_LINK_ORIGIN_PAGE = 7
    const val SECOND_LINK_DESTINATION_PAGE = 0

    const val LINK_CENTRE_X_FRACTION = 0.5f
    const val LINK_CENTRE_Y_FRACTION = 0.5f

    const val TOC_RESULTS_TITLE = "Results"
    const val TOC_RESULTS_PAGE = 42
    const val TOC_RESULTS_PATH = "Results"
    const val TOC_SECOND_STOP_PAGE = 49

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))
}
