// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.VERY_LONG)
class IncognitoTaskSeparationTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val home = PrivacyHome()

    @After
    fun leaveNoTaskBehind() {
        runCatching { appTasks().forEach { it.finishAndRemoveTask() } }
        home.picker.dismissIfShown()
    }

    @Test
    @TestId("PRIV-23")
    fun incognitoOpensInItsOwnTaskAndDoesNotDisturbAnOpenNormalReader() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        PrivacyFixtures.file(PrivacyFixtures.SECOND)

        home.launch()
        home.openNormallyFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        val normalUri = reader.documentUri()
        reader.awaitIdentity()
        assertFalse("document A must open normally", reader.isIncognitoIconVisible())

        TestEnvironment.uiDevice.pressHome()
        TestEnvironment.uiDevice.waitForIdle()

        home.reopen()
        home.openIncognitoFromDownloads(PrivacyFixtures.SECOND)
        reader.awaitReady()
        reader.awaitIdentity()
        assertTrue("document B must open in incognito", reader.isIncognitoIconVisible())
        val incognitoUri = reader.documentUri()

        val tasks = Await.untilValue(
            "the app holds two tasks, one per reader",
            Await.LONG_TIMEOUT_MS,
            read = { appTasks().size },
        ) { it >= EXPECTED_TASKS }
        assertTrue("the incognito reader must open a task of its own, the app holds $tasks", tasks >= EXPECTED_TASKS)
        assertNotEquals(
            "the two readers must be showing different documents",
            normalUri.toString(),
            incognitoUri.toString(),
        )

        val normalTask = appTasks().firstOrNull { task ->
            task.taskInfo.baseIntent.dataString == normalUri.toString()
        } ?: throw AssertionError(
            "No app task was launched with document A. Home is a task of its own with no data URI, so a " +
                "task chosen merely for not being the incognito one is Home as often as it is the reader. " +
                "The app holds " + appTasks().map { it.taskInfo.baseIntent.dataString }
        )
        TestEnvironment.onMainThread { normalTask.moveToFront() }
        Screens.awaitTopIs(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitReady()

        Await.until("the reader in front is the normal one", Await.LONG_TIMEOUT_MS) {
            !reader.isIncognitoIconVisible()
        }
        assertEquals(
            "switching back must show document A rather than the incognito document",
            normalUri.toString(),
            reader.documentUri().toString(),
        )

        val rows = PrivacyStores.records()
        assertEquals("exactly one document may be remembered", 1, rows.size)
        assertEquals(
            "the remembered document must be the one opened normally",
            PrivacyFixtures.NORMAL_TITLE,
            rows.single().fileName,
        )
    }

    private fun appTasks(): List<ActivityManager.AppTask> {
        val manager = TestEnvironment.targetContext
            .getSystemService(ActivityManager::class.java)
        return manager?.appTasks.orEmpty()
    }

    private companion object {
        const val EXPECTED_TASKS = 2
    }
}
