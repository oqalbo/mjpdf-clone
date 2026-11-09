// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.AppLogcat
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.resilience.ResilienceLogcat
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class HeapGrowthTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("MEM-27")
    fun fiftyDocumentOpensReleaseEveryReaderAndDescriptor() {
        val references = MemoryFixtures.fiftyDistinct().map(::openAndClose)
        references.forEach { Await.untilCollected(it) }
    }

    @Test
    @TestId("MEM-28")
    @TestDuration(DurationCategory.VERY_LONG)
    fun repeatedOptionsMenuOpeningReleasesTheLastWindow() {
        val since = AppLogcat.since()

        ActivityScenario.launch<MainActivity>(
            MemoryReader.fixtureViewIntent(MemoryFixtures.NORMAL)
        ).use {
            reader.awaitReady()
            val dialog = ActionsDialogRobot()
            var lastDecor: WeakReference<View>? = null
            repeat(MENU_OPENINGS) {
                dialog.open()
                lastDecor = WeakReference(dialog.dialogRoot())
                dialog.dismiss()
            }

            val decor = lastDecor
                ?: throw AssertionError("No menu window was captured, so nothing was opened")
            Await.untilCollected(decor)

            val leaks = AppLogcat.appLinesSince(since)
                .count { line -> line.contains(ResilienceLogcat.WINDOW_LEAK_MARKER) }
            assertEquals(
                "a dismissed menu window that is still registered logs WindowLeaked, and $MENU_OPENINGS " +
                    "openings must produce none",
                0,
                leaks,
            )
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun openAndClose(fixture: java.io.File): WeakReference<MainActivity> {
        var captured: WeakReference<MainActivity>? = null
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            captured = WeakReference(MemoryReader.activity())
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        MemoryReader.awaitDescriptorCount(fixture, 0)
        return captured ?: throw AssertionError("The reader for ${fixture.name} never became resumed")
    }

    private companion object {
        const val MENU_OPENINGS = 50
    }
}
