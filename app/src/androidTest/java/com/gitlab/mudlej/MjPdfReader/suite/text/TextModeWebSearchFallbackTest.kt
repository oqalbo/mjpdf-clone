// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.PackageDisableRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeWebSearchFallbackTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val packages = PackageDisableRule()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-44")
    fun webSearchWithNoSearchAppAndNoBrowserShowsANotice() {
        val since = TextLogcat.since()
        packages.disableHandlersOf(webSearchProbe())
        packages.disableHandlersOf(httpsProbe())
        assertTrue(
            "a search app still answers ACTION_WEB_SEARCH, so the fallback branch cannot be reached",
            packages.handlerPackagesOf(webSearchProbe()).isEmpty(),
        )
        assertTrue(
            "a browser still answers an https VIEW, so the fallback branch cannot be reached",
            packages.handlerPackagesOf(httpsProbe()).isEmpty(),
        )

        val word = CapturedReflow.require("SELECTION_WORD", CapturedReflow.SELECTION_WORD)

        ActivityScenario.launch<MainActivity>(
            ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.STRUCTURED))
        ).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitRenderIdle()
            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.hideControls()

            val notices = TextNotices.record()
            try {
                val page = text.pageText(0)
                val at = page.indexOf(word)
                assertTrue(
                    "page one must carry the word ${CapturedReflow.literal(word)}",
                    at >= 0,
                )
                text.longPressCharOffset(0, at + word.length / 2)
                text.awaitActionMode()
                assertEquals("the long press must select exactly one word", word, text.awaitSelection(0))

                text.tapActionModeItem(text.string(R.string.search_web))
                notices.awaitSeen(text.string(R.string.no_app_to_open_link))
            } finally {
                notices.stop()
            }

            Screens.assertTopIs(TextModeActivity::class.java)
            TextLogcat.assertAbsentSince(since, ACTIVITY_NOT_FOUND)

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()
        }
    }

    private fun webSearchProbe(): Intent =
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, PROBE_QUERY)

    private fun httpsProbe(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL))

    private companion object {
        const val PROBE_QUERY = "mjpdf"
        const val PROBE_URL = "https://www.google.com/search?q=mjpdf"
        const val ACTIVITY_NOT_FOUND = "ActivityNotFoundException"
    }
}
