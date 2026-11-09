// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.settings

import android.content.Context
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showListCardBadgesPreferenceDialog(context: Context, preferences: Preferences) {
    val labels = arrayOf(
        context.getString(R.string.home_badge_pages),
        context.getString(R.string.home_badge_progress),
        context.getString(R.string.home_badge_last_opened),
        context.getString(R.string.home_badge_file_size),
        context.getString(R.string.home_badge_status),
    )
    val checked = booleanArrayOf(
        preferences.getHomeBadgePages(),
        preferences.getHomeBadgeProgress(),
        preferences.getHomeBadgeLastOpened(),
        preferences.getHomeBadgeFileSize(),
        preferences.getHomeBadgeStatus(),
    )
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.home_list_badges_title)
        .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
        .setPositiveButton(R.string.apply) { _, _ ->
            preferences.setHomeBadgePages(checked[0])
            preferences.setHomeBadgeProgress(checked[1])
            preferences.setHomeBadgeLastOpened(checked[2])
            preferences.setHomeBadgeFileSize(checked[3])
            preferences.setHomeBadgeStatus(checked[4])
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
