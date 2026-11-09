// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@TestId("PRIV-31")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class Priv31ProcessDeathTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @Test
    fun phaseOneHighlightInIncognitoAndBackground() {
        PhaseRun.requirePhase(SCENARIO, 1)

        ActivityWatcher().use { watcher ->
            PrivacyReader.startFromOutside(
                PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            reader.awaitReady()
            reader.awaitIdentity()
            assertTrue("the session must start incognito", reader.isIncognitoIconVisible())

            reader.createHighlightOnCurrentPage()
            reader.awaitDirtyFab(visible = true)
            assertEquals(
                "the highlight must exist before the process dies",
                1,
                highlightGroups(0),
            )
            assertEquals(
                "the journal must be absent, which is why the edit cannot come back",
                emptyList<String>(),
                PrivacyStores.journalFiles().map { it.name },
            )

            TestEnvironment.uiDevice.pressHome()
            watcher.awaitReached(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    fun phaseTwoAssertNothingIsReplayed() {
        PhaseRun.requirePhase(SCENARIO, 2)

        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitReady()
        reader.awaitIdentity()

        assertTrue(
            "the restored session must still be incognito",
            reader.isIncognitoIconVisible(),
        )
        assertEquals(
            "page 1 must come back clean, with no highlight replayed from anywhere",
            0,
            highlightGroups(0),
        )
        assertFalse(
            "the app must not claim to hold an edit it cannot produce",
            reader.isDirtyFabVisible(),
        )
        assertEquals(
            "no journal may have been written for the lost edit",
            emptyList<String>(),
            PrivacyStores.journalFiles().map { it.name },
        )
        assertEquals(
            "the restored incognito session must still record nothing",
            0,
            PrivacyStores.recordCount(),
        )
    }

    private fun highlightGroups(pageIndex: Int): Int {
        val target = TestEnvironment.onMainThread {
            (TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity)
                ?.annotationDebug()
                ?: throw ConditionTimeoutException("No resumed reader debug target")
        }
        return JSONArray(target.dumpPageHighlights(pageIndex)).length()
    }

    private companion object {
        const val SCENARIO = "PRIV-31"
    }
}
