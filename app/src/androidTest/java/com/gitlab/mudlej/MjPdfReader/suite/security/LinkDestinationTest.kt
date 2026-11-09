// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class LinkDestinationTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("SEC-34")
    fun anOutOfRangeInternalLinkIsClampedToARealPage() {
        ActivityScenario.launch<MainActivity>(
            SecurityReader.fixtureViewIntent(SecurityFixtures.LINK_DESTINATIONS)
        ).use {
            reader.awaitReady()
            val pageCount = reader.readState().pageCount ?: 0
            assertEquals(
                "the fixture must carry three pages, one link each",
                SecurityFixtures.LINK_DESTINATIONS_PAGES,
                pageCount,
            )

            assertEquals(
                "a destination of -1 must clamp to the first page",
                FIRST_PAGE_INDEX,
                tapLinkOnPage(NEGATIVE_LINK_PAGE_INDEX),
            )
            assertEquals(
                "a destination of Integer.MAX_VALUE must clamp to the last page",
                pageCount - 1,
                tapLinkOnPage(MAX_VALUE_LINK_PAGE_INDEX),
            )
            val offTreeLanding = tapLinkOnPage(OFF_TREE_LINK_PAGE_INDEX)
            assertTrue(
                "a destination that is not in the page tree must land inside the document, it landed on " +
                    "index $offTreeLanding of $pageCount pages",
                offTreeLanding in 0 until pageCount,
            )
        }
    }

    private fun tapLinkOnPage(pageIndex: Int): Int {
        SecurityReader.jumpToPage(reader, pageIndex)
        reader.awaitRenderIdle()

        SecurityReader.tapRect(reader, SecurityFixtures.LINK_DESTINATION_RECTS[pageIndex])
        reader.awaitMotionSettled()
        val landed = reader.readState().page ?: -1

        val expectedLabel = string(R.string.back_to_page, pageIndex + 1)
        Snackbars.awaitText(expectedLabel)
        tapSnackbarMessage()
        reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)

        return landed
    }

    private fun tapSnackbarMessage() {
        val bounds = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivity()
                ?: throw AssertionError("No resumed activity while taking the jump back snackbar")
            val message = activity.findViewById<android.widget.TextView>(
                com.google.android.material.R.id.snackbar_text
            ) ?: throw AssertionError(
                "The jump back snackbar shows no message. JumpBackSnackbar puts the jump back on the " +
                    "message view and leaves Done on the action button, which only dismisses"
            )
            val location = IntArray(2)
            message.getLocationOnScreen(location)
            intArrayOf(location[0] + message.width / 2, location[1] + message.height / 2)
        }
        TestEnvironment.uiDevice.click(bounds[0], bounds[1])
        TestEnvironment.uiDevice.waitForIdle()
        TestEnvironment.waitForIdleSync()
    }

    private fun string(resId: Int, vararg formatArgs: Any): String =
        TestEnvironment.targetContext.getString(resId, *formatArgs)

    private companion object {
        const val FIRST_PAGE_INDEX = 0
        const val NEGATIVE_LINK_PAGE_INDEX = 0
        const val MAX_VALUE_LINK_PAGE_INDEX = 1
        const val OFF_TREE_LINK_PAGE_INDEX = 2
    }
}
