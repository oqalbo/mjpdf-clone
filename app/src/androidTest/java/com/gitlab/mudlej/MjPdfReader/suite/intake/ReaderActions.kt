// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object ReaderActions {

    fun reload() {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
                ?: throw ConditionTimeoutException("No reader is resumed to reload")
            activity.reloadPdf()
        }
        TestEnvironment.waitForIdleSync()
    }
}
