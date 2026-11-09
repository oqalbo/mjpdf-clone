// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.load

import android.app.Activity
import android.content.DialogInterface
import android.text.format.Formatter
import com.gitlab.mudlej.MjPdfReader.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CopyConsentDialog {

    fun show(
        activity: Activity,
        request: CopyConsentRequest,
        onGrantAccess: () -> Unit,
        onCopy: () -> Unit,
    ) {
        val size = request.sizeBytes?.let { Formatter.formatShortFileSize(activity, it) }
        val message = if (size != null) {
            activity.getString(R.string.copy_consent_message, size)
        } else {
            activity.getString(R.string.copy_consent_message_unknown_size)
        }
        val body = if (request.fitsOnDisk) message else {
            message + "\n\n" + activity.getString(R.string.copy_consent_no_space)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.copy_consent_title)
            .setMessage(body)
            .setPositiveButton(R.string.copy_consent_copy) { _, _ -> onCopy() }
            .setNeutralButton(R.string.copy_consent_grant) { _, _ -> onGrantAccess() }
            .setNegativeButton(R.string.copy_consent_just_open, null)
            .show()
        if (!request.fitsOnDisk) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
        }
    }
}
