// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.history

import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.databinding.RowNavigationHistoryBinding

class NavigationHistoryAdapter(
    private val onClick: (NavigationHistoryRow) -> Unit,
) : ListAdapter<NavigationHistoryRow, NavigationHistoryAdapter.NavigationHistoryViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavigationHistoryViewHolder {
        val binding = RowNavigationHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NavigationHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NavigationHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NavigationHistoryViewHolder(
        private val binding: RowNavigationHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: NavigationHistoryRow) {
            val context = binding.root.context
            binding.navigationHistoryTitle.text = context.getString(R.string.bookmark_page_label, entry.pageIndex + 1)
            binding.navigationHistoryTocPath.text = entry.tableOfContentsPath.orEmpty()
            binding.navigationHistoryTocPath.visibility = if (entry.tableOfContentsPath.isNullOrBlank()) View.GONE else View.VISIBLE

            when (entry.kind) {
                NavigationHistoryRow.Kind.CURRENT -> {
                    binding.navigationHistoryIcon.setImageResource(R.drawable.ic_locate_me)
                    binding.navigationHistoryTitle.setTypeface(null, Typeface.BOLD)
                    binding.navigationHistorySubtitle.text = context.getString(R.string.history_current_page)
                    binding.root.setOnClickListener(null)
                    binding.root.isClickable = false
                }
                else -> {
                    val icon = if (entry.kind == NavigationHistoryRow.Kind.FORWARD) R.drawable.ic_nav_forward else R.drawable.ic_history
                    binding.navigationHistoryIcon.setImageResource(icon)
                    binding.navigationHistoryTitle.setTypeface(null, Typeface.NORMAL)
                    binding.navigationHistorySubtitle.text = context.getString(
                        R.string.history_entry_subtitle_format,
                        context.getString(entry.origin.labelRes),
                        DateUtils.getRelativeTimeSpanString(
                            entry.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ),
                    )
                    binding.root.setOnClickListener { onClick(entry) }
                }
            }
        }
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<NavigationHistoryRow>() {
            override fun areItemsTheSame(oldItem: NavigationHistoryRow, newItem: NavigationHistoryRow): Boolean {
                return oldItem.kind == newItem.kind && oldItem.stackIndex == newItem.stackIndex
            }

            override fun areContentsTheSame(oldItem: NavigationHistoryRow, newItem: NavigationHistoryRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
