// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showScrollingInfoCardPreferenceDialog(context: Context, preferences: Preferences) {
    val labels = arrayOf(
        context.getString(R.string.scrolling_info_time),
        context.getString(R.string.scrolling_info_pdf_name),
        context.getString(R.string.scrolling_info_page_number),
        context.getString(R.string.scrolling_info_reading_percentage),
    )
    val checked = booleanArrayOf(
        preferences.getFullScreenInfoShowTime(),
        preferences.getFullScreenInfoShowPdfName(),
        preferences.getFullScreenInfoShowPageNumber(),
        preferences.getFullScreenInfoShowReadingPercentage(),
    )
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.scrolling_info_card)
        .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
        .setPositiveButton(R.string.apply) { _, _ ->
            preferences.setFullScreenInfoShowTime(checked[0])
            preferences.setFullScreenInfoShowPdfName(checked[1])
            preferences.setFullScreenInfoShowPageNumber(checked[2])
            preferences.setFullScreenInfoShowReadingPercentage(checked[3])
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
