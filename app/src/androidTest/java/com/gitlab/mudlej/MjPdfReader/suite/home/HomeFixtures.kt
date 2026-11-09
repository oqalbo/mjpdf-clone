// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

object HomeFixtures {

    const val ALPHA = "alpha.pdf"
    const val ALPHA_COPY = "alpha-copy.pdf"
    const val BETA = "beta.pdf"
    const val GAMMA = "gamma.pdf"
    const val APPLE = "Apple.pdf"
    const val BANANA = "banana.pdf"
    const val CHERRY = "Cherry.pdf"
    const val FILENAME_DIFFERS = "filename-differs.pdf"
    const val REPORT_ANNOTATED = "report-annotated.pdf"
    const val EIGHT_PAGE = "eight-page.pdf"

    const val EIGHT_PAGE_PAGES = 8

    const val ALPHA_RED = 0xFFFF0000.toInt()
    const val GAMMA_BLUE = 0xFF0000FF.toInt()

    const val TREE_DIRECTORY = "TestBooks"

    val treeMembers: List<String> = listOf(
        "Fiction/a.pdf",
        "Fiction/Short/b.pdf",
        "Fiction/Short/c.pdf",
        "Science/d.pdf",
    )
}
