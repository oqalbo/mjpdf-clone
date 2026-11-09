// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class JumpHistoryTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-43")
    fun theNavigationTilesReadAsDisabledWithNoHistory() {
        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            navigation.openActionsDialog()

            for (label in listOf(jumpBackLabel(), jumpForwardLabel(), navigationHistoryLabel())) {
                assertTrue("the tile \"$label\" must be shown rather than removed", navigation.isTilePresent(label))
                assertFalse("the tile \"$label\" must not be clickable with no history", navigation.tileIsClickable(label))
                assertEquals(
                    "the tile \"$label\" must render at the disabled alpha",
                    NavigationRobot.DISABLED_ALPHA,
                    navigation.tileAlpha(label),
                    ALPHA_TOLERANCE,
                )
            }

            navigation.tapTile(jumpBackLabel())

            assertTrue("tapping a disabled tile must leave the dialog open", navigation.isActionsDialogShown())
            assertEquals("tapping a disabled tile must not move the reader", 0 as Int?, reader.readState().page)
            navigation.dismissActionsDialog()
        }
    }

    private fun jumpBackLabel(): String = string(R.string.nav_back)

    private fun jumpForwardLabel(): String = string(R.string.nav_forward)

    private fun navigationHistoryLabel(): String = string(R.string.navigation_history)

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun launchNormal(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)))

    private companion object {
        const val ALPHA_TOLERANCE = 0.001f
    }
}
