// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry
import com.shockwave.pdfium.PdfDocument

internal fun bookmark(title: String, page: Long, vararg children: PdfDocument.Bookmark): PdfDocument.Bookmark =
    PdfDocument.Bookmark().apply {
        this.title = title
        pageIdx = page
        setChildren(children.toList())
    }

internal fun entries(vararg roots: PdfDocument.Bookmark): List<TableOfContentsEntry> =
    roots.mapIndexed { index, bookmark -> TableOfContentsEntry(bookmark, 0, index.toString()) }
