// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class OpenSourceLibrariesDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.libs)
            .setMessage(LIBRARIES.joinToString("\n\n") { it.text })
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .create()
    }

    private data class LibraryAttribution(
        val name: String,
        val copyright: String,
        val license: String,
        val website: String
    ) {
        val text: String
            get() = "$name\n$copyright\nLicense: $license\nWebsite: $website"
    }

    companion object {
        const val TAG = "open_source_libraries_dialog"

        private val LIBRARIES = listOf(
            LibraryAttribution(
                "MJ PDF's fork of Android PdfViewer",
                "Forked by Mudlej",
                "Apache License",
                "https://gitlab.com/mudlej_android/mj_pdf_reader/-/tree/main/AndroidPdfViewer"
            ),
            LibraryAttribution(
                "MJ PDF's fork of PdfiumAndroid",
                "Forked by Mudlej",
                "Apache License",
                "https://gitlab.com/mudlej_android/mj_pdf_reader/-/tree/main/PdfiumAndroid"
            ),
            LibraryAttribution(
                "Android Open Source Project",
                "Copyright 2016 The Android Open Source Project",
                "Apache License",
                "https://source.android.com/"
            ),
            LibraryAttribution(
                "AndroidX Libraries",
                "Copyright The Android Open Source Project",
                "Apache License",
                "https://developer.android.com/jetpack/androidx"
            ),
            LibraryAttribution(
                "Material Design Icons",
                "Copyright 2014, Austin Andrews",
                "SIL Open Font",
                "https://materialdesignicons.com/"
            )
        )
    }
}
