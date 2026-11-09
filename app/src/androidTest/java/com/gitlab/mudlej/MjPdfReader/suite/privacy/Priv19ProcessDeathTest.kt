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
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.rules.ExpectsProcessDeath
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@ExpectsProcessDeath
@TestId("PRIV-19")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class Priv19ProcessDeathTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @Test
    fun phaseOneReadIncognitoAndBackground() {
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

            reader.jumpToPageIndex(FIRST_STOP_INDEX)
            PrivacyStores.holdNoRecords()

            TestEnvironment.uiDevice.pressHome()
            watcher.awaitReached(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    fun phaseTwoAssertRestoredSessionIsStillIncognito() {
        PhaseRun.requirePhase(SCENARIO, 2)

        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitReady()
        reader.awaitIdentity()

        assertTrue(
            "a low memory kill and restore must not promote a private session into a recorded one",
            reader.isIncognitoIconVisible(),
        )

        reader.jumpToPageIndex(SECOND_STOP_INDEX)

        PrivacyStores.holdNoRecords()
        assertEquals(
            "the restored incognito session must still record nothing",
            0,
            PrivacyStores.recordCount(),
        )
        assertEquals(
            "the restored incognito session must leave no annotation journal",
            emptyList<String>(),
            PrivacyStores.journalFiles().map { it.name },
        )
    }

    private companion object {
        const val SCENARIO = "PRIV-19"
        const val FIRST_STOP_INDEX = 2
        const val SECOND_STOP_INDEX = 4
    }
}
