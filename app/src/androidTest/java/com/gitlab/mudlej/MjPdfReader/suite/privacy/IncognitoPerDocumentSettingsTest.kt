// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class IncognitoPerDocumentSettingsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = PrivacyReader()
    private val home = PrivacyHome()
    private val text = PrivacyTextMode(reader)

    @After
    fun leaveNoTaskBehind() {
        reader.finishTask()
        home.picker.dismissIfShown()
    }

    @Test
    @TestId("PRIV-09")
    fun incognitoPageTurnsPersistNoReadingPosition() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)
        home.listByFileName()

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.jumpToPageIndex(NORMAL_STOP_INDEX)
            Records.awaitStoredPage(hash, NORMAL_STOP_INDEX)
        }

        val before = Records.awaitRowExists(hash)
        assertEquals("the normal session must store page index $NORMAL_STOP_INDEX", NORMAL_STOP_INDEX, before.pageNumber)

        home.launch()
        home.openInIncognitoFromOptions(PrivacyFixtures.NORMAL_TITLE)
        reader.awaitReady()
        reader.awaitIncognitoIcon(visible = true)
        reader.awaitIdentity()
        reader.goToPage(INCOGNITO_STOP_ONE_BASED)

        Records.assertStoredPageStays(hash, NORMAL_STOP_INDEX)
        reader.finishTask()

        val after = Records.awaitRowExists(hash)
        assertEquals(
            "reading ahead in incognito must not move the saved position",
            NORMAL_STOP_INDEX,
            after.pageNumber,
        )
        assertEquals(
            "reading ahead in incognito must not move the last opened date",
            before.lastOpened,
            after.lastOpened,
        )

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.reader.awaitPageIndex(NORMAL_STOP_INDEX, Await.LONG_TIMEOUT_MS)
            assertEquals(
                "reopening normally must land back on the page the normal session left",
                NORMAL_STOP_INDEX as Int?,
                reader.reader.readState().page,
            )
        }
    }

    @Test
    @TestId("PRIV-10")
    @TestDuration(DurationCategory.SHORT)
    fun incognitoAutoScrollSpeedChangeIsNotStored() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_OVERLAY_DELAY_MS)
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            Records.awaitRowExists(hash)
        }
        assertNull(
            "the normal session must leave the auto scroll speed unset",
            Records.byHash(hash)?.autoScrollSpeed,
        )

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use { scenario ->
            reader.awaitReady()
            reader.awaitIdentity()
            reader.startAutoScrollAndRaiseSpeed(SPEED_STEPS)

            assertEquals(
                "the speed readout must reach $RAISED_SPEED_TEXT, otherwise no speed change was ever requested",
                RAISED_SPEED_TEXT,
                reader.autoScrollSpeedText(),
            )

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            reader.awaitReady()
        }

        Records.assertRowKeeps(hash, "the auto scroll speed stays unset for $hash") { it?.autoScrollSpeed == null }
        assertNull(
            "a speed chosen during an incognito session must not be stored",
            Records.byHash(hash)?.autoScrollSpeed,
        )
    }

    @Test
    @TestId("PRIV-11")
    @TestDuration(DurationCategory.SHORT)
    fun incognitoCropMarginsDetectionStoresNoMargins() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.SCANNED)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.SCANNED)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            Records.awaitRowExists(hash)
        }
        assertNull("the normal session must leave the crop margins unset", Records.byHash(hash)?.cropMargins)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.SCANNED)).use {
            reader.awaitReady()
            reader.awaitIdentity()

            val runsBefore = ReaderTestHooks.cropDetectionRuns
            reader.triggerCropMargins()

            Await.until("crop margin detection completes a run", Await.VERY_LONG_TIMEOUT_MS) {
                ReaderTestHooks.cropDetectionRuns > runsBefore && !ReaderTestHooks.cropDetectionRunning
            }
            reader.reader.awaitFullQualityRender(Await.VERY_LONG_TIMEOUT_MS)

            assertTrue(
                "detection must reach a real outcome, it reported \"${ReaderTestHooks.cropDetectionRunOutcome}\"",
                ReaderTestHooks.cropDetectionRunOutcome == ReaderTestHooks.CROP_APPLIED ||
                    ReaderTestHooks.cropDetectionRunOutcome == ReaderTestHooks.CROP_FULL_PAGE,
            )
        }

        Records.assertRowKeeps(hash, "the crop margins for $hash stay unset") {
            it?.cropMargins == null && it?.cropMarginsVersion == 0
        }
        val row = Records.awaitRowExists(hash)
        assertNull("a margin result computed in incognito must not be cached", row.cropMargins)
        assertEquals("the crop margins version must stay at zero", 0, row.cropMarginsVersion)
    }

    @Test
    @TestId("PRIV-12")
    fun incognitoReadingDirectionOverrideIsNotStored() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)
        val fixture = PrivacyFixtures.file(PrivacyFixtures.ARABIC)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.ARABIC)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            Records.awaitRowExists(hash)
        }
        val seeded = Records.awaitRowExists(hash)
        assertNull("the normal session must leave the override unset", seeded.readingDirectionOverride)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.ARABIC)).use { scenario ->
            reader.awaitReady()
            reader.awaitIdentity()

            assertEquals(
                "the Arabic fixture must start out reading right to left, otherwise the chosen direction " +
                    "below is not a change at all",
                true,
                reader.reader.readState().readingDirectionRtl,
            )
            reader.chooseReadingDirection(R.string.reading_direction_ltr)

            reader.awaitReady()
            reader.reader.awaitState(
                "the resolved reading direction flips to left to right",
                Await.LONG_TIMEOUT_MS,
            ) { it.readingDirectionRtl == false }

            scenario.recreate()
            reader.awaitReady()
            reader.reader.awaitState("the session direction survives another recreation") {
                it.readingDirectionRtl == false
            }

            reader.awaitIncognitoIcon(visible = true)
            assertTrue(
                "the session must still be incognito after the direction change recreates the reader",
                reader.isIncognitoIconVisible(),
            )
        }

        Records.assertRowKeeps(hash, "both reading direction columns for $hash remain unchanged") {
            it?.readingDirectionOverride == seeded.readingDirectionOverride &&
                it?.detectedReadingDirection == seeded.detectedReadingDirection
        }
        val row = Records.awaitRowExists(hash)
        assertNull("a direction chosen in incognito must not follow the document", row.readingDirectionOverride)
        assertEquals("incognito must preserve the earlier detection", seeded.detectedReadingDirection, row.detectedReadingDirection)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.ARABIC)).use {
            reader.awaitReady()
            reader.reader.awaitState("a fresh session uses the document's detected direction") {
                it.readingDirectionRtl == true
            }
        }
    }

    @Test
    fun incognitoAutomaticDirectionCanReplaceASavedOverrideWithoutChangingIt() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)
        val hash = PrivacyFixtures.identityOf(PrivacyFixtures.file(PrivacyFixtures.ARABIC))
        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.ARABIC)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            reader.chooseReadingDirection(R.string.reading_direction_ltr)
            reader.reader.awaitState("normal reading applies the saved LTR override") {
                it.readingDirectionRtl == false
            }
            Records.awaitRow(hash, "normal reading saves the override", Await.LONG_TIMEOUT_MS) {
                it?.readingDirectionOverride == ReadingDirection.LEFT_TO_RIGHT.id
            }
        }
        val saved = Records.awaitRowExists(hash)

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.ARABIC)).use { scenario ->
            reader.awaitReady()
            reader.reader.awaitState("incognito initially respects the saved override") {
                it.readingDirectionRtl == false
            }
            reader.chooseReadingDirection(R.string.reading_direction_auto_ltr)
            reader.reader.awaitState("Automatic uses the detected RTL direction in this session", Await.LONG_TIMEOUT_MS) {
                it.readingDirectionRtl == true
            }
            scenario.recreate()
            reader.awaitReady()
            reader.reader.awaitState("Automatic survives recreation despite the stored LTR override") {
                it.readingDirectionRtl == true
            }
            reader.awaitIncognitoIcon(visible = true)

            val nextUri = PrivacyFixtures.uri(PrivacyFixtures.NORMAL)
            scenario.onActivity { it.displayFromUri(nextUri) }
            reader.reader.awaitState("a different document does not inherit this session's RTL direction", Await.LONG_TIMEOUT_MS) {
                it.documentUri == nextUri.toString() && it.readingDirectionRtl == false && it.loadState == DocumentLoadState.LOADED
            }
        }

        Records.assertRowKeeps(hash, "incognito leaves the stored override and detection unchanged") {
            it?.readingDirectionOverride == saved.readingDirectionOverride &&
                it?.detectedReadingDirection == saved.detectedReadingDirection
        }
        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.ARABIC)).use {
            reader.awaitReady()
            reader.reader.awaitState("reopening normally still uses the saved LTR override") {
                it.readingDirectionRtl == false
            }
        }
    }

    @Test
    @TestId("PRIV-15")
    fun incognitoTextModeSavesNoPagePositionAndNoReflowOverrides() {
        val fixture = PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val hash = PrivacyFixtures.identityOf(fixture)

        ActivityScenario.launch<MainActivity>(PrivacyReader.viewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()
            Records.awaitStoredPage(hash, 0)
        }

        ActivityScenario.launch<MainActivity>(PrivacyReader.incognitoViewIntent(PrivacyFixtures.NORMAL)).use {
            reader.awaitReady()
            reader.awaitIdentity()

            text.openFromReader()
            text.awaitContent()

            assertTrue(
                "text mode must carry the incognito extra, ${text.describeChrome()}",
                text.isIncognitoChrome(),
            )

            text.goToPage(TEXT_MODE_STOP_ONE_BASED, PrivacyFixtures.NORMAL_PAGES)
            text.toggleJoinParagraphs()

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()
            reader.awaitReady()
        }

        Records.assertStoredPageStays(hash, 0)
        val row = Records.awaitRowExists(hash)
        assertEquals("text mode must not move the stored page in incognito", 0, row.pageNumber)
        assertNull("the join paragraphs override must not be stored", row.textModeJoinParagraphs)
        assertNull("the detect headings override must not be stored", row.textModeDetectHeadings)
        assertNull("the code blocks override must not be stored", row.textModeCodeBlocks)
    }

    private companion object {
        const val NORMAL_STOP_INDEX = 1
        const val INCOGNITO_STOP_ONE_BASED = 10
        const val TEXT_MODE_STOP_ONE_BASED = 5
        const val SPEED_STEPS = 3
        const val RAISED_SPEED_TEXT = "6"
        const val LONG_OVERLAY_DELAY_MS = 60_000
    }
}
