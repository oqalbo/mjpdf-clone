// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import androidx.test.core.app.ActivityScenario
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
class MainThreadTest(private val fixtureName: String) : ResilienceTestCase() {

    @Ignore("RES-36: zero main-thread disk reads on startup is an unsettled requirement")
    @Test
    @TestId("RES-36")
    fun openingADocumentTouchesNeitherPdfiumNorDiskOnTheMainThread() {
        val file = fixture(fixtureName)
        val since = ResilienceLogcat.since()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()
        }

        ResilienceLogcat.assertNoMainThreadPdfiumWork(since)
        CrashGate.assertNoFatalSince(since)
        ResilienceLogcat.assertNoStallOver(since, LOAD_STALL_BUDGET_MS)
        StrictModeLog.assertNoDiskViolationsSince(since)
    }

    companion object {

        const val LOAD_STALL_BUDGET_MS = 700

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<String> = listOf(
            ResilienceFixtures.NORMAL,
            ResilienceFixtures.LARGE,
            ResilienceFixtures.SCANNED,
            ResilienceFixtures.ARABIC,
        )
    }
}
