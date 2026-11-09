// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.search

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.SearchResult
import com.gitlab.mudlej.MjPdfReader.databinding.SearchResultItemBinding
import com.gitlab.mudlej.MjPdfReader.core.text.accentInsensitiveRanges
import com.gitlab.mudlej.MjPdfReader.core.text.indexesOf

class SearchResultViewHolder(
    private val context: Context,
    private val binding: SearchResultItemBinding,
    private val searchResultFunctions: SearchResultFunctions,
) : RecyclerView.ViewHolder(binding.root) {

    companion object {
        private const val COLLAPSED_MAX_LINES = 4
    }

    fun bind(row: SearchResultRow, ignoreAccents: Boolean = false) {
        val searchResult = row.result
        val text = stylizeText(searchResult, row.nestedQuery, ignoreAccents)
        binding.apply {
            resultText.setText(text, TextView.BufferType.SPANNABLE)
            resultPageNumber.text = context.getString(R.string.search_result_page_label, searchResult.pageNumber)

            // show more text
            if (!searchResult.expanded) {
                resultText.maxLines = COLLAPSED_MAX_LINES
                showMoreButton.visibility = View.VISIBLE
                showMoreButton.setOnClickListener {
                    searchResultFunctions.onShowMoreResultTextClicked(searchResult)
                }
            }
            else {
                resultText.maxLines = Int.MAX_VALUE
                showMoreButton.visibility = View.GONE
                showMoreButton.setOnClickListener(null)
            }

            // got to page
            root.setOnClickListener {
                searchResultFunctions.onSearchResultClicked(searchResult)
            }
        }
    }

    private fun stylizeText(searchResult: SearchResult, nestedQuery: String?, ignoreAccents: Boolean): Spannable {
        val color = ContextCompat.getColor(context, R.color.search)
        val spannable = SpannableString(searchResult.text)
        val length = spannable.length

        // stylize nested query result
        nestedQuery?.let { query ->
            if (query.isEmpty() || query.isBlank() || query.length < 3) {
                return@let
            }

            val ranges = if (ignoreAccents) {
                searchResult.text.accentInsensitiveRanges(query)
            } else {
                searchResult.text.indexesOf(query, ignoreCase = true).map { it until it + query.length }
            }
            for (range in ranges) {
                val index = range.first
                if (index == searchResult.inputStart) {
                    continue // skip the main query string
                }

                val end = (range.last + 1).coerceAtMost(length)
                if (index !in 0 until end) continue

                spannable.setSpan(UnderlineSpan(), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // stylize the main query input
        val inputStart = searchResult.inputStart.coerceIn(0, length)
        val inputEnd = searchResult.inputEnd.coerceIn(inputStart, length)
        if (inputStart < inputEnd) {
            spannable.setSpan(ForegroundColorSpan(color), inputStart, inputEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), inputStart, inputEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}
