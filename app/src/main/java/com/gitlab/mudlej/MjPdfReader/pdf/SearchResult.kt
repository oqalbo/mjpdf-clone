// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

data class SearchResult(
    val originalIndex: Int,
    val inputStart: Int,
    val inputEnd: Int,
    val text: String,
    val pageNumber: Int,
    val longText: Boolean = false,
    val expanded: Boolean = false,
    var searchResultIndexInList: Int = 0,
)