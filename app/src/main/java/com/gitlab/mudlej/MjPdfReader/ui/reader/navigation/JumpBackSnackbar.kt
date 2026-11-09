// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.navigation

import android.view.View
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.google.android.material.snackbar.Snackbar

class JumpBackSnackbar(private val root: View) {

    private var active: Snackbar? = null

    val isShowing: Boolean
        get() = active != null

    fun show(
        message: String,
        onDone: (() -> Unit)? = null,
        dismissOnTap: Boolean = true,
        onTap: () -> Unit,
    ) {
        active?.dismiss()
        val snackbar = AppSnackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
        active = snackbar
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (active === transientBottomBar) {
                    active = null
                }
            }
        })
        snackbar.setAction(root.context.getString(R.string.done)) {
            onDone?.invoke()
            snackbar.dismiss()
        }
        val textView = snackbar.view.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
        textView.setOnClickListener {
            if (dismissOnTap) {
                snackbar.dismiss()
            }
            onTap()
        }
        snackbar.show()
    }

    fun dismiss() {
        active?.dismiss()
        active = null
    }
}
