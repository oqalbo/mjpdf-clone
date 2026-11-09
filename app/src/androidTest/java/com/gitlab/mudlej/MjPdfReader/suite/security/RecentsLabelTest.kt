// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.ActivityManager
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class RecentsLabelTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    @Test
    @TestId("SEC-62")
    fun theDocumentNameIsRegisteredAsTheReadersRecentsLabel() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.NORMAL)
        ).use { scenario ->
            reader.awaitReady()
            reader.awaitFullQualityRender()

            var readerTaskId = -1
            scenario.onActivity { readerTaskId = it.taskId }
            TestEnvironment.uiDevice.pressHome()
            Screens.awaitStage(MainActivity::class.java, androidx.test.runner.lifecycle.Stage.STOPPED)
            val manager = TestEnvironment.targetContext.getSystemService(ActivityManager::class.java)
            val label = Await.untilValue(
                condition = "the reader task supplies its document name to Recents",
                read = {
                    manager.appTasks.map { it.taskInfo }
                        .firstOrNull {
                            val taskId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.taskId else it.id
                            taskId == readerTaskId
                        }
                        ?.taskDescription?.label
                },
            ) { it == SecurityFixtures.NORMAL }
            assertEquals(SecurityFixtures.NORMAL, label)
        }
    }
}
