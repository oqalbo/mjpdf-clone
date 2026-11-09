// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry

data class TableOfContentsRow(
    val entry: TableOfContentsEntry,
    val expandable: Boolean,
    val expanded: Boolean,
)
