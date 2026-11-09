// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PrivacyInfoDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.privacy)
            .setMessage(R.string.privacy_info)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setIcon(R.drawable.privacy_icon)
            .create()
    }

    companion object {
        const val TAG = "privacy_dialog"
    }
}
