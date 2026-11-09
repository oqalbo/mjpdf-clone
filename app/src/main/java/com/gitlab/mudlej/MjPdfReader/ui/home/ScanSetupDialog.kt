// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.home

import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ScanSetupDialog(
    private val activity: AppCompatActivity,
    private val pref: Preferences,
    private val onWholeDeviceChosen: () -> Unit,
    private val onPickLocationsChosen: () -> Unit,
) {

    fun show() {
        var selected = if (pref.getScanMode() == ScanMode.SELECTED_LOCATIONS) 1 else 0
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.home_scan_choice_title)
            .setSingleChoiceItems(
                arrayOf(
                    activity.getString(R.string.home_scan_choice_whole_device),
                    activity.getString(R.string.home_scan_choice_selected_locations),
                ),
                selected,
            ) { _, which -> selected = which }
            .setPositiveButton(R.string.ok) { _, _ ->
                if (selected == 0) onWholeDeviceChosen() else onPickLocationsChosen()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
