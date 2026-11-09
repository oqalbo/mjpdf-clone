// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.recyclerview.widget.ListAdapter
import com.gitlab.mudlej.MjPdfReader.databinding.SearchResultItemBinding

class SearchResultAdapter(
    private val searchResultFunctions: SearchResultFunctions
) : ListAdapter<SearchResultRow, SearchResultViewHolder>(SearchResultComparator()) {

    var nestedQuery: String? = null
    var progressBar: ProgressBar? = null
    var ignoreAccents = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        return SearchResultViewHolder(parent.context,
            SearchResultItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            searchResultFunctions,
        )
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, index: Int) {
        getItem(index)?.let {
            holder.bind(it, ignoreAccents)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<SearchResultRow>, currentList: MutableList<SearchResultRow>) {
        progressBar?.visibility = View.GONE
        super.onCurrentListChanged(previousList, currentList)
    }
}
