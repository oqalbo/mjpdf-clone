// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import android.content.Intent
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeDebug
import java.io.File

object TextModeProbe {

    fun intentFor(file: File, pageIndex: Int = 0, incognito: Boolean = false): Intent =
        Intent(TestEnvironment.targetContext, TextModeActivity::class.java).apply {
            putExtra(PDF.filePathKey, Uri.fromFile(file).toString())
            putExtra(PDF.pageNumberKey, pageIndex)
            putExtra(PDF.incognitoKey, incognito)
        }

    fun itemCount(): Int = ListScreens.itemCount(R.id.textPagesRecyclerView)

    fun firstVisiblePosition(): Int = ListScreens.firstVisiblePosition(R.id.textPagesRecyclerView)

    fun awaitPagePopulated(position: Int, timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        ListScreens.awaitItemsAtLeast(R.id.textPagesRecyclerView, position + 1, timeoutMs)
        Await.until("text mode binds and fills page index $position", timeoutMs) {
            firstVisiblePosition() == position &&
                TestEnvironment.onMainThread {
                    val activity = TestEnvironment.resumedActivityOfType(TextModeActivity::class.java)
                        ?: return@onMainThread null
                    val pages = activity.findViewById<RecyclerView>(R.id.textPagesRecyclerView)
                        ?: return@onMainThread null
                    TextModeDebug.pageSnapshot(pages, position)
                }
                    ?.let { it.pageTextVisible && !it.pageText.isNullOrBlank() } == true
        }
    }
}
