// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.history

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object HistoryFlows {

    fun readAndLeave(name: String, oneBasedPage: Int? = null): String =
        readAndLeaveUri(HistoryFixtures.uri(name), oneBasedPage)

    fun readAndLeaveUri(uri: Uri, oneBasedPage: Int? = null): String {
        val hist = HistoryReader()
        var hash = ""
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(uri)).use {
            hist.awaitReady()
            hash = hist.reader.awaitFileHashResolved()
            if (oneBasedPage == null) {
                Records.awaitRowExists(hash)
            } else {
                hist.jumpToPage(oneBasedPage)
                Records.awaitStoredPage(hash, oneBasedPage - 1)
            }
        }
        return hash
    }

    fun readWithHistoryOffAndLeave(name: String, oneBasedPage: Int? = null): String {
        val hist = HistoryReader()
        var hash = ""
        ActivityScenario.launch<MainActivity>(HistoryReader.viewIntent(name)).use {
            hist.awaitReady()
            hash = hist.reader.awaitFileHashResolved()
            if (oneBasedPage != null) {
                hist.jumpToPage(oneBasedPage)
            }
        }
        return hash
    }
}
