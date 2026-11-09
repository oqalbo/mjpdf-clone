// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.tableofcontents

import android.content.Intent
import android.os.Bundle
import com.gitlab.mudlej.MjPdfReader.pdf.PDF

data class TableOfContentsState(
    val expandedPaths: ArrayList<String> = arrayListOf(),
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val query: String? = null,
) {
    fun putInto(intent: Intent) {
        intent.putStringArrayListExtra(PDF.tableOfContentsExpandedPathsKey, ArrayList(expandedPaths))
        intent.putExtra(PDF.tableOfContentsScrollPositionKey, scrollPosition)
        intent.putExtra(PDF.tableOfContentsScrollOffsetKey, scrollOffset)
        query?.let { intent.putExtra(PDF.tableOfContentsQueryKey, it) }
    }

    fun putInto(bundle: Bundle) {
        bundle.putStringArrayList(PDF.tableOfContentsExpandedPathsKey, ArrayList(expandedPaths))
        bundle.putInt(PDF.tableOfContentsScrollPositionKey, scrollPosition)
        bundle.putInt(PDF.tableOfContentsScrollOffsetKey, scrollOffset)
        query?.let { bundle.putString(PDF.tableOfContentsQueryKey, it) }
    }

    companion object {
        fun from(intent: Intent?): TableOfContentsState {
            if (intent == null) return TableOfContentsState()

            return TableOfContentsState(
                expandedPaths = intent.getStringArrayListExtra(PDF.tableOfContentsExpandedPathsKey) ?: arrayListOf(),
                scrollPosition = intent.getIntExtra(PDF.tableOfContentsScrollPositionKey, 0),
                scrollOffset = intent.getIntExtra(PDF.tableOfContentsScrollOffsetKey, 0),
                query = intent.getStringExtra(PDF.tableOfContentsQueryKey),
            )
        }

        fun from(bundle: Bundle?): TableOfContentsState {
            if (bundle == null) return TableOfContentsState()

            return TableOfContentsState(
                expandedPaths = bundle.getStringArrayList(PDF.tableOfContentsExpandedPathsKey) ?: arrayListOf(),
                scrollPosition = bundle.getInt(PDF.tableOfContentsScrollPositionKey, 0),
                scrollOffset = bundle.getInt(PDF.tableOfContentsScrollOffsetKey, 0),
                query = bundle.getString(PDF.tableOfContentsQueryKey),
            )
        }
    }
}
