// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.view.Menu
import android.view.View
import androidx.appcompat.widget.SearchView
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.snackbar.Snackbar

fun Menu.attachFilterSearchView(
    root: View,
    onQueryChanged: (String) -> Int?,
    onClosed: () -> Boolean,
) {
    val searchItem = findItem(R.id.search_in_search_activity)
    val searchView = searchItem.actionView as SearchView
    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String) = false

        override fun onQueryTextChange(query: String): Boolean {
            val filteredCount = onQueryChanged(query) ?: return false
            AppSnackbar.make(
                root,
                root.context.getString(R.string.number_of_filtered_results).format(filteredCount),
                Snackbar.LENGTH_SHORT,
            ).show()
            return false
        }
    })
    searchView.setOnCloseListener { onClosed() }
}
