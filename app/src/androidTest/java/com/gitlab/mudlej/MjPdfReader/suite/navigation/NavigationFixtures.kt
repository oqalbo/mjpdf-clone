// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object NavigationFixtures {

    const val NORMAL = "normal.pdf"
    const val ARABIC_RTL = "arabic-rtl.pdf"
    const val LARGE = "large.pdf"
    const val SINGLE_PAGE = "single-page.pdf"
    const val EDGE_LINK = "edge-link.pdf"

    const val NORMAL_PAGES = 20
    const val LARGE_PAGES = 500
    const val SINGLE_PAGE_PAGES = 1
    const val EDGE_LINK_PAGES = 6

    const val EDGE_LINK_SOURCE_PAGE = 0
    const val EDGE_LINK_DESTINATION_PAGE = 3
    const val EDGE_LINK_CENTRE_X_FRACTION = 0.9f
    const val EDGE_LINK_CENTRE_Y_FRACTION = 0.5f

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))
}
