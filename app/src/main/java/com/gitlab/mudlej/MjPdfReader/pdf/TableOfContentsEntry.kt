// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.pdf

import com.shockwave.pdfium.PdfDocument

class TableOfContentsEntry(entry: PdfDocument.Bookmark, val level: Int, val path: String) : PdfDocument.Bookmark() {
    val subEntries: MutableList<TableOfContentsEntry> = mutableListOf()

    init {
        title = entry.title
        pageIdx = entry.pageIdx
        mNativePtr = entry.mNativePtr
        children = entry.children

        // add all children recursively
        if (hasChildren())
            for ((index, child) in children.withIndex())
                subEntries.add(TableOfContentsEntry(child, level + 1, "$path.$index"))
    }

    fun hasSubEntries() = subEntries.isNotEmpty()
}
