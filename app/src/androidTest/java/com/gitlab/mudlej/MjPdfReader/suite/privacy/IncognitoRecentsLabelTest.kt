// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.ActivityManager
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.rules.TestDirectoryRule
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoRecentsLabelTest {

    private val suite = SuiteRules.standard()
    private val directory = TestDirectoryRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(directory)

    private val reader = PrivacyReader()

    @After
    fun leaveRecents() {
        runCatching { TestEnvironment.uiDevice.pressBack() }
        runCatching { TestEnvironment.uiDevice.pressHome() }
        reader.finishTask()
    }

    @Test
    @TestId("PRIV-36")
    fun theRecentsTaskLabelShowsTheIncognitoDocumentsFileName() {
        val distinctive = PrivacyFixtures.copyInto(
            directory.directory(),
            PrivacyFixtures.NORMAL,
            PrivacyFixtures.DISTINCTIVE_NAME,
        )
        Fixtures.scanAndWait(distinctive)

        ActivityScenario.launch<MainActivity>(
            PrivacyReader.incognitoViewIntent(Uri.fromFile(distinctive))
        ).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.awaitIncognitoIcon(visible = true)

            val labels = Await.untilValue(
                "the reader task publishes its task description label",
                Await.LONG_TIMEOUT_MS,
                pollMs = 250L,
                read = { taskDescriptionLabels() },
            ) { it.any { label -> label.contains(PrivacyFixtures.DISTINCTIVE_TITLE) } }

            assertTrue(
                "MainActivity.updateTitle must publish the document file name as the task label in incognito " +
                    "too, the labels read $labels",
                labels.any { it.contains(PrivacyFixtures.DISTINCTIVE_TITLE) },
            )

            TestEnvironment.uiDevice.pressHome()
            TestEnvironment.uiDevice.waitForIdle()
            TestEnvironment.uiDevice.pressRecentApps()
            TestEnvironment.uiDevice.wait(Until.hasObject(By.pkg(LAUNCHER_PACKAGE)), Await.DEFAULT_TIMEOUT_MS)

            assertFalse(
                "the overview card must not render the incognito document's file name, the overview reads " +
                    dumpTexts(),
                dumpTexts().any { it.contains(PrivacyFixtures.DISTINCTIVE_TITLE) },
            )
        }
    }

    private fun taskDescriptionLabels(): List<String> {
        val manager = TestEnvironment.targetContext.getSystemService(ActivityManager::class.java)
        return manager?.appTasks.orEmpty().mapNotNull { it.taskInfo.taskDescription?.label }
    }

    private fun dumpTexts(): List<String> {
        val dump = File(TestEnvironment.targetContext.cacheDir, HIERARCHY_FILE)
        runCatching { TestEnvironment.uiDevice.dumpWindowHierarchy(dump) }
        val xml = runCatching { dump.readText() }.getOrDefault("")
        runCatching { dump.delete() }
        return TEXT_ATTRIBUTE.findAll(xml).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
    }

    private companion object {
        const val HIERARCHY_FILE = "priv-36-hierarchy.xml"
        val TEXT_ATTRIBUTE = Regex("text=\"([^\"]*)\"")
        val LAUNCHER_PACKAGE: java.util.regex.Pattern = java.util.regex.Pattern.compile(".*launcher.*")
    }
}
