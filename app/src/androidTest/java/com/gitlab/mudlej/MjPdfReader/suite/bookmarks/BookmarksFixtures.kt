// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object BookmarksFixtures {

    const val NORMAL = "normal.pdf"
    const val LARGE = "large.pdf"
    const val CORRUPT = "corrupt.pdf"
    const val ARABIC_RTL = "arabic-rtl.pdf"
    const val TOC = "toc.pdf"
    const val TOC_DEEP = "toc-deep.pdf"
    const val TOC_UNRESOLVED = "toc-unresolved.pdf"
    const val LINKS = "links.pdf"
    const val NO_LINKS = "no-links.pdf"
    const val SHARED_PREFIX_A = "shared-prefix-a.pdf"
    const val SHARED_PREFIX_B = "shared-prefix-b.pdf"

    const val NORMAL_PAGES = 20
    const val LARGE_PAGES = 500
    const val ARABIC_RTL_PAGES = 12
    const val TOC_PAGES = 60
    const val LINKS_PAGES = 45
    const val NO_LINKS_PAGES = 20
    const val SHARED_PREFIX_PAGES = 2

    const val TOC_TOP_LEVEL_ENTRIES = 6
    const val TOC_TOTAL_ENTRIES = 24
    const val TOC_FIRST_ENTRY_TITLE = "Introduction"
    const val TOC_FIRST_ENTRY_CHILDREN = 3
    const val TOC_RESULTS_TITLE = "Results"
    const val TOC_RESULTS_PAGE_INDEX = 42
    const val TOC_CASE_INSENSITIVE_QUERY = "APPENDIX"
    const val TOC_LOWERCASE_TITLE = "appendix notes"
    const val TOC_UPPERCASE_TITLE = "Appendix"

    const val TOC_DEEP_TOP_LEVEL_ENTRIES = 8
    const val TOC_DEEP_TOTAL_ENTRIES = 412
    const val TOC_DEEP_LEVEL_THREE_PAGE_INDEX = 2
    const val TOC_DEEP_LEVEL_THREE_PATH = "Part 1 ▶ Section 1.1 ▶ Subsection 1.1.1"

    const val TOC_FLAT = "toc-flat.pdf"
    const val TOC_FLAT_TOP_LEVEL_ENTRIES = 3
    const val TOC_FLAT_FIRST_TITLE = "Opening"

    const val TOC_UNRESOLVED_TOP_LEVEL_ENTRIES = 7
    const val TOC_UNRESOLVED_TOTAL_ENTRIES = 25
    const val TOC_UNRESOLVED_TITLE = "Unresolved reference"
    const val TOC_UNRESOLVED_OPEN_PAGE_INDEX = 4

    const val LINKS_LISTED_ROWS = 6
    const val LINKS_INTERNAL_SOURCE_PAGE_INDEX = 2
    const val LINKS_INTERNAL_DESTINATION_PAGE_INDEX = 39
    const val LINKS_EXTERNAL_PAGE_INDEX = 11
    const val LINKS_EXTERNAL_ROW_PAGE_NUMBER = 12
    const val LINKS_EXTERNAL_URL = "https://example.com/beta"
    const val LINKS_BROWSER_URL = "https://example.org/delta"
    const val LINKS_COPY_URL = "https://example.com/alpha"
    const val LINKS_UNHANDLED_URL = "mjtest://example"
    const val LINKS_HOST_QUERY = "example.org"

    val LINKS_ALL_URLS = listOf(
        "https://example.com/alpha",
        "https://example.com/alpha",
        "https://example.com/beta",
        "https://example.org/gamma",
        "https://example.org/delta",
        "mjtest://example",
    )

    val LINKS_ALL_PAGE_NUMBERS = listOf(1, 5, 12, 19, 31, 37)

    const val LINK_RECT_LEFT_POINTS = 220.0f
    const val LINK_RECT_BOTTOM_POINTS = 387.0f
    const val LINK_RECT_RIGHT_POINTS = 392.0f
    const val LINK_RECT_TOP_POINTS = 405.0f
    const val LINK_PAGE_WIDTH_POINTS = 612.0f
    const val LINK_PAGE_HEIGHT_POINTS = 792.0f

    const val GRID_DEFAULT_COLUMNS = 3
    const val GRID_MIN_COLUMNS = 2
    const val GRID_MAX_COLUMNS = 7

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))

    fun linkRectCentreXFraction(): Float =
        (LINK_RECT_LEFT_POINTS + LINK_RECT_RIGHT_POINTS) / 2f / LINK_PAGE_WIDTH_POINTS

    fun linkRectCentreYFraction(): Float =
        1f - (LINK_RECT_BOTTOM_POINTS + LINK_RECT_TOP_POINTS) / 2f / LINK_PAGE_HEIGHT_POINTS
}
