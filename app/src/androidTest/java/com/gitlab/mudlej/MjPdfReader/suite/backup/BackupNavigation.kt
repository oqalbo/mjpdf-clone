// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity

object BackupNavigation {
    fun finishAppTask() {
        Screens.finishAppTask()
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    fun relaunchIntoHome(): HomeRobot {
        finishAppTask()
        return HomeRobot().launchFromLauncher(Await.LONG_TIMEOUT_MS)
    }

    fun relaunchIntoHome(consumeExpectedModal: () -> Unit): HomeRobot {
        finishAppTask()
        return HomeRobot().launchFromLauncher(Await.LONG_TIMEOUT_MS, consumeExpectedModal)
    }

    fun openHome(): HomeRobot = HomeRobot().launchFromLauncher(Await.LONG_TIMEOUT_MS)
}
