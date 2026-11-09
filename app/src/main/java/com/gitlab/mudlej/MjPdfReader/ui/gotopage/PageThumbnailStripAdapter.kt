// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.gotopage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.ItemPageThumbnailStripBinding
import com.gitlab.mudlej.MjPdfReader.pdf.PageThumbnailCache
import kotlinx.coroutines.Job

class PageThumbnailStripAdapter(
    private val pageCount: Int,
    private val currentPageIndex: Int,
    private val cache: PageThumbnailCache,
    private val pdfDarkTheme: Boolean,
    private val onPageClicked: (Int) -> Unit,
) : RecyclerView.Adapter<PageThumbnailStripAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPageThumbnailStripBinding) : RecyclerView.ViewHolder(binding.root) {
        var renderJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPageThumbnailStripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        applyPdfThemeToThumbnail(binding.pageImage, pdfDarkTheme)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.renderJob?.cancel()
        val binding = holder.binding
        binding.pageNumber.text = (position + 1).toString()
        applyCurrentPageStroke(binding.pageCard, position == currentPageIndex)
        binding.pageCard.setOnClickListener { onPageClicked(holder.bindingAdapterPosition) }
        val cellWidthPx = binding.pageCard.layoutParams.width
        holder.renderJob = cache.bind(binding.pageImage, position, cellWidthPx)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.renderJob?.cancel()
        holder.renderJob = null
    }

    override fun getItemCount() = pageCount
}
