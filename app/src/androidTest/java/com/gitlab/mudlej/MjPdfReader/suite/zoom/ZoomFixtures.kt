// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.zoom

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object ZoomFixtures {

    const val NORMAL = "normal.pdf"
    const val LARGE = "large.pdf"
    const val SCANNED = "scanned.pdf"
    const val ARABIC = "arabic-rtl.pdf"
    const val LANDSCAPE = "landscape-two-page.pdf"
    const val TALL_PAGE = "tall-page.pdf"

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)
}
