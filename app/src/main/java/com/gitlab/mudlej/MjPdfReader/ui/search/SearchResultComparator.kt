// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import androidx.recyclerview.widget.DiffUtil

class SearchResultComparator : DiffUtil.ItemCallback<SearchResultRow>() {
    override fun areItemsTheSame(oldItem: SearchResultRow, newItem: SearchResultRow) =
        oldItem.result.pageNumber == newItem.result.pageNumber
                && oldItem.result.originalIndex == newItem.result.originalIndex

    override fun areContentsTheSame(oldItem: SearchResultRow, newItem: SearchResultRow) =
        oldItem.result == newItem.result && oldItem.nestedQuery == newItem.nestedQuery
}
