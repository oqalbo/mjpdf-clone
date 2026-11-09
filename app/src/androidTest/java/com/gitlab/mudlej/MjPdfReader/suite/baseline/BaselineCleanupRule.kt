// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.TestIds
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class BaselineCleanupRule : TestWatcher() {

    private var testId: String = ""

    fun testId(): String {
        if (testId.isEmpty()) {
            throw BaselineFailure(
                "The baseline cleanup rule has not started yet, so no test id is known. Read it inside a test " +
                    "body rather than during field initialisation."
            )
        }
        return testId
    }

    override fun starting(description: Description) {
        testId = TestIds.of(description)
        CaptureGate.reset()
        BaselineProfile.forget()
    }

    override fun finished(description: Description) {
        CaptureGate.reset()
    }
}
