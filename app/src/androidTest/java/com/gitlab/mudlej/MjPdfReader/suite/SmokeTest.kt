// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gitlab.mudlej.MjPdfReader.R
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderQuality
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SmokeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val home = HomeRobot()

    @Test
    @TestId("SMOKE-01")
    fun theReaderOpensAFixtureAndReportsItThroughTheStateHook() {
        val fixture = pushNormalFixture()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            val state = reader.awaitFullQualityRender()
            ReaderProbe.awaitVisibility(R.id.progressBar, View.GONE)
            ReaderProbe.assertVisibilityHolds(R.id.progressBar, View.GONE)

            assertEquals("page count of normal.pdf", 20 as Int?, state.pageCount)
            assertEquals("the reader opens on the first page, zero based", 0 as Int?, state.page)
            assertEquals("render quality at capture time", RenderQuality.FULL, state.quality)
        }
    }

    @Test
    @TestId("SMOKE-02")
    fun swipingForwardMovesTheReaderToTheNextPage() {
        val fixture = pushNormalFixture()

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            reader.awaitPageIndex(0)

            repeat(SWIPE_ATTEMPTS) {
                if (reader.readState().page != 1) reader.swipeForward()
            }

            val state = reader.awaitPageIndex(1)
            assertEquals("the reader settles on the second page, zero based", 1 as Int?, state.page)
        }
    }

    @Test
    @TestId("SMOKE-03")
    fun homePublishesItsRenderGrammarOnTheRootView() {
        home.launchFromLauncher()
        val state = home.awaitRendered()

        assertTrue(
            "home render generation must have advanced at least once, read: $state",
            (state.generation ?: 0) >= 1,
        )
        assertNotNull("home must report a tab, read: $state", state.tab)

        val groups = state.description.groupNames
        assertTrue("the home group is missing from $groups", "home" in groups)
        assertTrue("the scan group is missing from $groups", "scan" in groups)
        assertNotNull("the layout direction group is missing from $groups", state.layoutDirection)
        assertNotNull("home must report a rendered item count, read: $state", state.renderedItems)
        assertNotNull("home must report a search flag, read: $state", state.searching)
        assertNotNull("the scan group must report loaded, read: $state", state.scanLoaded)
    }

    private fun pushNormalFixture(): File {
        Fixtures.ensureAllFilesAccess()
        return Fixtures.pushAsset(FIXTURE_NAME, rules.testDirectory.fileNamed(FIXTURE_NAME))
    }

    private companion object {
        const val FIXTURE_NAME = "normal.pdf"
        const val SWIPE_ATTEMPTS = 4
    }
}
