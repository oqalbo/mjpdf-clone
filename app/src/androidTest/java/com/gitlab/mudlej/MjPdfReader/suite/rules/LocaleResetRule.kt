// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.rules

import com.gitlab.mudlej.MjPdfReader.suite.robot.LocaleRobot
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class LocaleResetRule : TestWatcher() {

    val robot = LocaleRobot()

    override fun starting(description: Description) {
        robot.reset()
    }

    override fun finished(description: Description) {
        robot.reset()
    }
}
