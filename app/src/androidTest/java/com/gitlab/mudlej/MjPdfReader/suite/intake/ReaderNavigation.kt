// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object ReaderNavigation {

    fun jumpToPage(reader: ReaderRobot, pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed while jumping to page index $pageIndex")
            val pdfView = activity.findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while jumping to page index $pageIndex")
            pdfView.jumpTo(pageIndex, false)
        }
        reader.awaitPageIndex(pageIndex, timeoutMs)
        return reader.awaitFullQualityRender(timeoutMs)
    }

    fun jumpToLastPage(reader: ReaderRobot, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState {
        val pageCount = reader.readState().pageCount
            ?: throw ConditionTimeoutException("The reader reports no page count, so there is no last page")
        return jumpToPage(reader, pageCount - 1, timeoutMs)
    }
}
