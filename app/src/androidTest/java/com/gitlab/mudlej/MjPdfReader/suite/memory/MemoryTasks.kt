// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import android.content.Intent
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object MemoryTasks {

    fun startReader(reader: ReaderRobot, intent: Intent): ReaderState {
        ReaderProbe.startReader(intent)
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        return reader.awaitReady()
    }

    fun finishReaderTasks() {
        ReaderProbe.finishAndRemoveReaderTasks()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }
}
