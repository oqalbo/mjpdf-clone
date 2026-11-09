// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import android.view.View
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.PackageDisableRule
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeWebSearchTest : SelectionTestCase() {

    @get:Rule
    val packages = PackageDisableRule()

    private val text = TextModeRobot()

    @Test
    @TestId("SEL-46")
    fun textModeWebSearchWithNoBrowserReportsItInsteadOfCrashing() {
        packages.disableHandlersOf(webSearchProbe())
        packages.disableHandlersOf(browserProbe())
        assertTrue(
            "a search app still answers ACTION_WEB_SEARCH",
            packages.handlerPackagesOf(webSearchProbe()).isEmpty(),
        )
        assertTrue(
            "a browser still answers an https VIEW",
            packages.handlerPackagesOf(browserProbe()).isEmpty(),
        )

        openReader(SelectionFixtures.LATIN) { _, _ ->
            text.openFromReaderMenu()
            val pageText = text.pageText(0)
            assertTrue("page one must have extracted some text to select", pageText.isNotBlank())

            longPressPageTextCentre()
            Await.until("the platform selection action mode is showing") { isActionModeItemShown(copyLabel()) }

            text.tapActionModeItem(text.string(R.string.search_web))

            Snackbars.awaitText(text.string(R.string.no_app_to_open_link))
            Screens.assertTopIs(TextModeActivity::class.java)
            assertEquals("the extracted page one text must still be displayed", pageText, text.pageText(0))
            assertFalse(
                "the action mode closes after Search Web even when no app can handle it",
                isActionModeItemShown(copyLabel()) || isActionModeItemShown(text.string(R.string.search_web)),
            )
        }
    }

    private fun longPressPageTextCentre() {
        val centre = text.onItem(0) { item ->
            val view = item.findViewById<View?>(R.id.pageText)
                ?: throw ConditionTimeoutException("The text mode item at position 0 carries no page text view")
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
        }
        RawInput.pressAndHold(centre, ViewConfiguration.getLongPressTimeout().toLong() + LONG_PRESS_MARGIN_MS)
        TestEnvironment.waitForIdleSync()
    }

    private fun isActionModeItemShown(label: String): Boolean =
        TestEnvironment.uiDevice.findObject(By.text(label)) != null ||
            TestEnvironment.uiDevice.findObject(By.desc(label)) != null

    private fun copyLabel(): String = TestEnvironment.targetContext.getString(android.R.string.copy)

    private fun webSearchProbe(): Intent = Intent(Intent.ACTION_WEB_SEARCH)

    private fun browserProbe(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL))

    private companion object {
        const val PROBE_URL = "https://www.google.com/search?q=probe"
        const val LONG_PRESS_MARGIN_MS = 250L
    }
}
