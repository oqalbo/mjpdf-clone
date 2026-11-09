// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class OrientationRule : TestWatcher() {
    override fun starting(description: Description) {
        Rotation.reset()
    }

    override fun finished(description: Description) {
        Rotation.reset()
    }
}
