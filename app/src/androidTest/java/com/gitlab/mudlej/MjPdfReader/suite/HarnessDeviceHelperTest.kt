// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.scan.ScanPrefs
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.home.scan.ScanPhase
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HarnessDeviceHelperTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val selection = SelectionRobot()
    private val home = HomeRobot()

    @Test
    @TestId("WAVE0-10")
    fun aLongPressCreatesASelectionAndTheEndHandleDragMovesIt() {
        SettingsSeeder.seed(Preferences.inlineTextSelectionKey, true)
        val fixture = pushNormalFixture()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()

            val selected = longPressUntilSomethingIsSelected()
            assertNotNull("a long press must produce a selection start index", selected.selectionStart)
            assertNotNull("a long press must produce a selection end index", selected.selectionEnd)
            assertNotNull("a selection must expose its end handle centre", selected.selectionEndHandle)
            assertTrue(
                "a selection must expose at least one run rectangle",
                selection.selectionRects().isNotEmpty(),
            )

            val startIndex = selected.selectionStart!!
            val endIndex = selected.selectionEnd!!

            val dragDistance = END_HANDLE_DRAG_DP * TestEnvironment.targetContext.resources.displayMetrics.density
            val afterDrag = selection.dragEndHandleBy(dragDistance, 0f)

            assertTrue(
                "dragging the end handle right must not move the selection start, was $startIndex now ${afterDrag.selectionStart}",
                afterDrag.selectionStart == startIndex,
            )
            assertTrue(
                "dragging the end handle right must grow the selection, was $endIndex now ${afterDrag.selectionEnd}",
                (afterDrag.selectionEnd ?: 0) > endIndex,
            )
        }
    }

    @Test
    @TestId("WAVE0-11")
    fun theScanLifecycleHelperSeesThePipelineCompleteWhenHomeScans() {
        Fixtures.ensureAllFilesAccess()
        ScanPrefs.seedSelected(rules.testDirectory.directory())
        val before = ScanLifecycle.generation()
        home.launchFromLauncher()
        home.awaitRendered()

        ScanLifecycle.awaitDiscoveryFinishedAfter(before)
        val index = ScanLifecycle.awaitCompletedAfter(before)
        home.awaitScanCommitted(index)

        assertTrue("the scan index is loaded after discovery: $index", index.loaded)
        assertTrue(
            "the scan generation advances beyond $before: $index",
            index.generation > before,
        )
        assertTrue(
            "the backend completed independently of Home rendering: $index",
            index.phase == ScanPhase.IDLE,
        )
    }

    @Test
    @TestId("WAVE0-12")
    fun theSearchIdleHelperReportsNoProgressBeforeAnySearchRuns() {
        SearchIdle.clear()
        assertNull("no search has run, so the progress accessor must report nothing", SearchIdle.progress())
    }

    private fun longPressUntilSomethingIsSelected(): com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState {
        var lastError: Throwable? = null
        LONG_PRESS_TARGETS.forEach { (horizontal, vertical) ->
            val attempt = runCatching { selection.longPressInsidePage(horizontal, vertical) }
            attempt.getOrNull()?.let { return it }
            lastError = attempt.exceptionOrNull()
        }
        throw AssertionError(
            "No long press target produced a selection on the fixture, last failure was $lastError"
        )
    }

    private fun pushNormalFixture(): File {
        Fixtures.ensureAllFilesAccess()
        return Fixtures.pushAsset(FIXTURE_NAME, rules.testDirectory.fileNamed(FIXTURE_NAME))
    }

    private companion object {
        const val FIXTURE_NAME = "normal.pdf"
        const val END_HANDLE_DRAG_DP = 120f

        val LONG_PRESS_TARGETS = listOf(
            0.30f to 0.18f,
            0.45f to 0.28f,
            0.30f to 0.38f,
            0.50f to 0.12f,
        )
    }
}
