// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult

data class SearchResultRow(
    val result: SearchResult,
    val nestedQuery: String?,
)
