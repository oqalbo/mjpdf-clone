// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import com.gitlab.mudlej.MjPdfReader.pdf.TableOfContentsEntry

interface TableOfContentsFunctions {
    fun onEntryClicked(entry: TableOfContentsEntry)
}