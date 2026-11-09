// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.ui

import android.content.Context
import androidx.annotation.StringRes
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun confirmDialog(
    context: Context,
    @StringRes titleRes: Int,
    message: CharSequence,
    @StringRes confirmRes: Int,
    onConfirm: () -> Unit,
) {
    MaterialAlertDialogBuilder(context)
        .setTitle(titleRes)
        .setMessage(message)
        .setPositiveButton(confirmRes) { _, _ -> onConfirm() }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
