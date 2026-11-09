// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.actions

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object MetadataDialog {

    fun isShown(): Boolean = runCatching { valueFor(ActionLabels.string(R.string.pdf_pages)) != null }
        .getOrDefault(false)

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("the file properties dialog is shown with its page count row", timeoutMs) { isShown() }
    }

    fun pageCountText(): String? = valueFor(ActionLabels.string(R.string.pdf_pages))

    fun valueFor(label: String): String? {
        val root = dialogRoot()
        return TestEnvironment.onMainThread { valueAfter(root, label) }
    }

    private fun valueAfter(root: View, label: String): String? {
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is TextView && child.text?.toString() == label) {
                val next = root.getChildAt(index + 1) as? TextView
                if (next != null) return next.text?.toString()
            }
            valueAfter(child, label)?.let { return it }
        }
        return null
    }

    private fun dialogRoot(): View {
        if (!Dialogs.isAnyShowing()) {
            throw ConditionTimeoutException("No file properties dialog is on screen to read rows from")
        }
        return Dialogs.dialogRoot()
    }
}
