// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gitlab.mudlej.MjPdfReader.databinding.PageHomeFoldersBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PageHomeLibraryBinding
import com.gitlab.mudlej.MjPdfReader.databinding.PageHomeRecentBinding

class HomeTabsAdapter(
    private val onPageAttached: (HomeTab, RecyclerView, SwipeRefreshLayout) -> Unit,
) : RecyclerView.Adapter<HomeTabsAdapter.PageViewHolder>() {

    class PageViewHolder(
        val swipeRefresh: SwipeRefreshLayout,
        val recyclerView: RecyclerView,
    ) : RecyclerView.ViewHolder(swipeRefresh)

    override fun getItemCount() = HomeTab.entries.size

    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val swipeRefresh = when (HomeTab.entries[viewType]) {
            HomeTab.RECENT -> PageHomeRecentBinding.inflate(inflater, parent, false).pageSwipeRefresh
            HomeTab.LIBRARY -> PageHomeLibraryBinding.inflate(inflater, parent, false).pageSwipeRefresh
            HomeTab.FOLDERS -> PageHomeFoldersBinding.inflate(inflater, parent, false).pageSwipeRefresh
        }
        val recyclerView = swipeRefresh.findViewById<RecyclerView>(
            com.gitlab.mudlej.MjPdfReader.R.id.pageRecyclerView
        )
        return PageViewHolder(swipeRefresh, recyclerView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        onPageAttached(HomeTab.entries[position], holder.recyclerView, holder.swipeRefresh)
    }
}
