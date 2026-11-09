// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import android.app.Activity
import android.graphics.Typeface
import android.text.format.Formatter
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

fun showLibraryStatsDialog(
    activity: Activity,
    items: List<HomeItem>,
    scanEntries: List<ScannedPdfEntry>,
) {
    val density = activity.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), 0, dp(24), dp(8))
    }

    fun addHeader(@StringRes labelRes: Int, topMarginDp: Int) {
        val header = TextView(activity).apply {
            setText(labelRes)
            setTextAppearance(resolveTextAppearance(activity, com.google.android.material.R.attr.textAppearanceLabelLarge))
            setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(topMarginDp) }
        content.addView(header, params)
    }

    fun addRow(@StringRes labelRes: Int, value: String) {
        val bodyAppearance = resolveTextAppearance(activity, com.google.android.material.R.attr.textAppearanceBodyMedium)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val label = TextView(activity).apply {
            setText(labelRes)
            setTextAppearance(bodyAppearance)
        }
        val amount = TextView(activity).apply {
            text = value
            setTextAppearance(bodyAppearance)
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(amount)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(6) }
        content.addView(row, params)
    }

    fun formatCount(count: Int) = String.format(Locale.US, "%,d", count)
    fun statusCount(status: ReadingStatus) = items.count { it.readingStatus == status }

    val pagesRead = items.sumOf { if (it.pageNumber > 0) it.pageNumber + 1 else 0 }

    addHeader(R.string.home_stats_section_device, 8)
    addRow(R.string.home_stats_on_device, formatCount(scanEntries.size))
    addRow(R.string.home_stats_storage, Formatter.formatShortFileSize(activity, scanEntries.sumOf { it.size }))

    addHeader(R.string.home_stats_section_library, 16)
    addRow(R.string.home_stats_in_library, formatCount(items.size))
    addRow(R.string.home_stats_pages_read, formatCount(pagesRead))

    addHeader(R.string.home_stats_section_status, 16)
    addRow(R.string.home_chip_to_read, formatCount(statusCount(ReadingStatus.TO_READ)))
    addRow(R.string.home_chip_reading, formatCount(statusCount(ReadingStatus.READING)))
    addRow(R.string.home_chip_on_hold, formatCount(statusCount(ReadingStatus.ON_HOLD)))
    addRow(R.string.home_chip_completed, formatCount(statusCount(ReadingStatus.COMPLETED)))
    addRow(R.string.home_chip_abandoned, formatCount(statusCount(ReadingStatus.ABANDONED)))
    addRow(R.string.home_stats_no_status, formatCount(statusCount(ReadingStatus.UNSET)))

    MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.home_menu_stats)
        .setView(ScrollView(activity).apply { addView(content) })
        .setPositiveButton(R.string.ok, null)
        .show()
}

private fun resolveTextAppearance(activity: Activity, attr: Int): Int {
    val typedValue = TypedValue()
    activity.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.resourceId
}
