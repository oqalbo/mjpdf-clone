// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.TestDirectoryRule
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity

internal fun HomeRobot.restartHome() {
    TestEnvironment.onMainThread {
        TestEnvironment.resumedActivityOfType(HomeActivity::class.java)?.finish()
    }
    Screens.awaitGone(HomeActivity::class.java)
    launchFromLauncher()
    awaitRendered()
}

internal fun HomePage.openTestFolder(testDirectory: TestDirectoryRule) {
    val root = Fixtures.externalRoot.absolutePath
    val relative = testDirectory.expectedPath().absolutePath.removePrefix("$root/")
    openFolderPath(*relative.split("/").toTypedArray())
}
