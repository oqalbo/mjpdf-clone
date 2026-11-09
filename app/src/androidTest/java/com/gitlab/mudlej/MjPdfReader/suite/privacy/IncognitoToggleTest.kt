// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoToggleTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()

    @Test
    @TestId("PRIV-20")
    fun turningIncognitoOnMidReadStopsRecordingFromThatPoint() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(RECORDED_INDEX)
            Records.awaitStoredPage(hash, RECORDED_INDEX)

            reader.toggleIncognito()
            Snackbars.awaitText(string(R.string.incognito_on_message))
            reader.awaitReady()
            reader.awaitIncognitoIcon(visible = true)

            reader.goToPage(PRIVATE_STOP_ONE_BASED)
            Records.assertStoredPageStays(hash, RECORDED_INDEX)
        }

        val row = Records.awaitRowExists(hash)
        assertEquals(
            "turning incognito on must freeze the saved position where it was",
            RECORDED_INDEX,
            row.pageNumber,
        )
        assertEquals(
            "turning incognito on must not delete what was already recorded",
            1,
            PrivacyStores.recordCount(),
        )
    }

    @Test
    @TestId("PRIV-21")
    fun turningIncognitoOffMidReadResumesRecording() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(RECORDED_INDEX)
            PrivacyStores.holdNoRecords()

            reader.toggleIncognito()
            Snackbars.awaitText(string(R.string.incognito_off_message))
            reader.awaitReady()
            reader.awaitIncognitoIcon(visible = false)

            reader.jumpToPageIndex(RESUMED_INDEX)
            Records.awaitStoredPage(hash, RESUMED_INDEX, Await.LONG_TIMEOUT_MS)
        }

        val rows = PrivacyStores.records()
        assertEquals("leaving incognito must record exactly one document", 1, rows.size)
        assertEquals(
            "the resumed session must store the page it reached",
            RESUMED_INDEX,
            rows.single().pageNumber,
        )
        assertEquals(
            "the recorded row must name the document that was open",
            PrivacyFixtures.NORMAL_TITLE,
            rows.single().fileName,
        )
    }

    @Test
    @TestId("PRIV-22")
    fun incognitoChromeIsAppliedAndClearedCorrectly() {
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.appFollowSystemThemeKey to false,
                Preferences.interfaceThemeKey to Preferences.themeLight,
            )
        )
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            assertFalse(
                "a normal session must hide the action bar incognito icon",
                reader.isIncognitoIconVisible(),
            )
            assertEquals(
                "a normal session must offer the Incognito action",
                string(R.string.incognito),
                reader.readIncognitoTileLabel(),
            )
            assertFalse(
                "a normal session under the seeded light theme must not paint the incognito surface, " +
                    PrivacyChrome.describe(reader.activity()),
                PrivacyChrome.isIncognitoChrome(reader.activity()),
            )
            assertEquals(
                "a normal session under the seeded light theme must not be in night mode",
                Configuration.UI_MODE_NIGHT_NO,
                nightModeOf(),
            )
        }

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            assertTrue(
                "an incognito session must show the action bar incognito icon",
                reader.isIncognitoIconVisible(),
            )
            assertEquals(
                "an incognito session must offer the way out rather than the way in",
                string(R.string.incognito_exit),
                reader.readIncognitoTileLabel(),
            )
            assertTrue(
                "the incognito reader must paint the incognito surface despite the seeded light theme, " +
                    PrivacyChrome.describe(reader.activity()),
                PrivacyChrome.isIncognitoChrome(reader.activity()),
            )
            assertEquals(
                "the incognito reader must force night mode on despite the seeded light theme",
                Configuration.UI_MODE_NIGHT_YES,
                nightModeOf(),
            )
        }
    }

    private fun nightModeOf(): Int = TestEnvironment.onMainThread {
        reader.activity().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val RECORDED_INDEX = 2
        const val RESUMED_INDEX = 4
        const val PRIVATE_STOP_ONE_BASED = 10
    }
}
