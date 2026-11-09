// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModePrivacyTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-50")
    fun incognitoTextModeSavesNothing() {
        assertNightModeOff()

        ActivityScenario.launch<MainActivity>(incognitoIntent()).use {
            text.awaitReaderSettled()
            val hash = reader.awaitFileHashResolved()
            Records.assertNoRowFor(hash)

            text.openFromReaderMenu()
            val joined = text.pageText(0)

            text.scrollUntilCounter(INCOGNITO_PAGE, TextFixtures.NORMAL_PAGES)
            text.awaitPageCounter(text.counterFor(INCOGNITO_PAGE, TextFixtures.NORMAL_PAGES))

            text.openTypographySheet()
            text.setSheetSwitch(R.id.joinParagraphsSwitch, false)
            text.closeTypographySheet()

            val incognitoSurface = text.onActivity { activity ->
                ContextCompat.getColor(activity, R.color.incognito_surface)
            }
            assertEquals(
                "text mode must paint in the incognito surface colour",
                incognitoSurface,
                text.rootBackgroundColor(),
            )

            text.goToPage(1)
            text.awaitPageCounter(text.counterFor(1, TextFixtures.NORMAL_PAGES))
            val unjoined = text.pageText(0)
            assertNotEquals(
                "turning Join Paragraphs off must still change what is on screen in incognito",
                joined,
                unjoined,
            )

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()
            Screens.awaitTopIs(MainActivity::class.java)

            Records.assertNoRowFor(hash)
            assertNull("incognito text mode must write no record at all", Records.byHash(hash))
        }
    }

    @Test
    @TestId("TXT-51")
    fun historyDisabledStopsTextModePageSaves() {
        val hash: String
        ActivityScenario.launch<MainActivity>(normalIntent()).use {
            text.awaitReaderSettled()
            hash = reader.awaitFileHashResolved()
            Records.awaitStoredPage(hash, 0)
        }

        SettingsSeeder.seed(Preferences.historyEnabledKey, false)

        ActivityScenario.launch<MainActivity>(normalIntent()).use {
            text.awaitReaderSettled()

            text.openFromReaderMenu()
            text.awaitPageText(0)
            text.scrollUntilCounter(HISTORY_OFF_PAGE, TextFixtures.NORMAL_PAGES)
            text.awaitPageCounter(text.counterFor(HISTORY_OFF_PAGE, TextFixtures.NORMAL_PAGES))

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()

            reader.awaitPageIndex(HISTORY_OFF_PAGE - 1)
            val returnedPageIndex: Int? = HISTORY_OFF_PAGE - 1
            assertEquals(
                "the result extra is independent of history, so the reader still follows text mode",
                returnedPageIndex,
                reader.readState().page,
            )
            Records.assertStoredPageStays(hash, 0)
            assertEquals(
                "the stored page must not move with Save Reading History off",
                0,
                Records.byHash(hash)?.pageNumber,
            )
        }
    }

    private fun assertNightModeOff() {
        val nightMode = TestEnvironment.targetContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        assertEquals(
            "night mode must be off before this test runs, because the incognito overlay colour resolves " +
                "against it",
            Configuration.UI_MODE_NIGHT_NO,
            nightMode,
        )
    }

    private fun normalIntent(): Intent = ReaderRobot.viewIntent(TextFixtures.uri(TextFixtures.NORMAL))

    private fun incognitoIntent(): Intent = normalIntent().putExtra(PDF.incognitoKey, true)

    private companion object {
        const val INCOGNITO_PAGE = 5
        const val HISTORY_OFF_PAGE = 6
    }
}
