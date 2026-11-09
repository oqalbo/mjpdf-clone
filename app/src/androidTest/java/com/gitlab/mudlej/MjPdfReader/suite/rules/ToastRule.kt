// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.core.debug.ToastRecorder
import com.gitlab.mudlej.MjPdfReader.suite.core.AccessibilityToasts
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ToastRule : TestWatcher() {

    override fun starting(description: Description) {
        runCatching { ToastRecorder.clear() }
        runCatching { AccessibilityToasts.start() }
    }

    override fun finished(description: Description) {
        runCatching { AccessibilityToasts.stop() }
        runCatching { ToastRecorder.clear() }
    }
}
