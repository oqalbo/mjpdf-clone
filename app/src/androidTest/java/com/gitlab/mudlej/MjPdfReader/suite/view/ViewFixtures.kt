// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object ViewFixtures {

    const val NORMAL = "normal.pdf"
    const val MARGINS = "margins.pdf"
    const val FULL_BLEED = "full-bleed.pdf"
    const val THREE_PAGE = "three-page.pdf"
    const val LARGE = "large.pdf"
    const val ARABIC = "arabic-rtl.pdf"

    const val NORMAL_PAGE_COUNT = 20
    const val THREE_PAGE_PAGE_COUNT = 3

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)
}
