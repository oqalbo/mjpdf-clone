// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.RenderActivity
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class CropMarginsTest : ViewTestCase() {

    @Test
    @TestId("VIEW-25")
    fun cropMarginsDetectsAndCropsAWideMarginDocument() {
        openReader(ViewFixtures.MARGINS)
        val fileHash = reader.awaitFileHashResolved()

        val outcome = cropByHand()

        assertEquals("detection on a wide margin document must end in an applied crop", ReaderTestHooks.CROP_APPLIED, outcome)
        val row = ViewCrop.awaitStoredCrop(fileHash)
        assertEquals(
            "the stored crop must carry the current storage version",
            ViewCrop.STORAGE_VERSION,
            row.cropMarginsVersion,
        )
        assertFalse(
            "a wide margin document must produce a crop that is not the full page, but the row holds " +
                "${row.cropMargins}",
            ViewCrop.parse(row.cropMargins!!).isFullPage,
        )
        ViewActionsDialog.assertTileTitle(
            "after cropping the tile offers to show the margins again",
            presentRes = R.string.show_margins,
            absentRes = R.string.crop_margins,
        )

        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-26")
    @TestDuration(DurationCategory.SHORT)
    fun cropMarginsOnAFullBleedDocumentStoresAFullPageResultAndChangesNothing() {
        openReader(ViewFixtures.FULL_BLEED)
        val fileHash = reader.awaitFileHashResolved()
        val before = settledFullQuality()

        val outcome = cropByHand()

        assertEquals(
            "detection on an edge to edge document must report a full page result",
            ReaderTestHooks.CROP_FULL_PAGE,
            outcome,
        )
        val row = ViewCrop.awaitStoredCrop(fileHash)
        assertEquals("the stored crop must carry the current storage version", ViewCrop.STORAGE_VERSION, row.cropMarginsVersion)
        assertTrue(
            "a full bleed document must produce a full page crop, but the row holds ${row.cropMargins}",
            ViewCrop.parse(row.cropMargins!!).isFullPage,
        )

        val after = settledFullQuality()
        assertEquals("a full page result must not move the page", before.page, after.page)
        assertEquals("a full page result must not resize the page", before.pageWidth, after.pageWidth)
        assertEquals("a full page result must not resize the page", before.pageHeight, after.pageHeight)
    }

    @Test
    @TestId("VIEW-27")
    fun cancellingDetectionTurnsTheCropSettingOff() {
        openReader(ViewFixtures.LARGE)
        val fileHash = reader.awaitFileHashResolved()

        ViewActionsDialog.openAndTapWithoutAwaitingClose(R.string.crop_margins)
        ViewCrop.awaitDetectionRunning(Await.LONG_TIMEOUT_MS)
        ViewUi.awaitVisibility(R.id.cropDetectionStatusCard, View.VISIBLE, Await.LONG_TIMEOUT_MS)

        ViewUi.onView(R.id.cropDetectionCancelButton) { it.performClick() }

        ViewActionsDialog.awaitClosed()
        ViewUi.awaitVisibility(R.id.cropDetectionStatusCard, View.GONE, Await.LONG_TIMEOUT_MS)
        assertProgressCardStaysGoneUntilRenderIdle()
        ViewActionsDialog.assertTileTitle(
            "a cancelled analysis leaves the crop setting off",
            presentRes = R.string.crop_margins,
            absentRes = R.string.show_margins,
        )
        ViewCrop.assertNoStoredCrop(fileHash)
    }

    @Test
    @TestId("VIEW-28")
    fun aCachedCropIsReusedOnReopenWithNoDetectionPass() {
        val file = openReader(ViewFixtures.MARGINS)
        val fileHash = reader.awaitFileHashResolved()
        cropByHand()
        val storedBefore = ViewCrop.awaitStoredCrop(fileHash).cropMargins

        finishReader()
        ViewCrop.resetHooks()
        launchAt(file)

        assertEquals(
            "reopening a cropped document must not run detection again",
            0,
            ViewCrop.runsSoFar(),
        )
        assertEquals(
            "the reopen must take the cached branch, which reports itself as skipped",
            ReaderTestHooks.CROP_SKIPPED,
            ViewCrop.outcome(),
        )
        assertProgressCardStaysGoneUntilRenderIdle()
        assertEquals(
            "the cached crop must be reused byte for byte",
            storedBefore,
            Records.byHash(fileHash)?.cropMargins,
        )
        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-29")
    @TestDuration(DurationCategory.SHORT)
    fun alwaysHideMarginsCropsFromTheMomentTheDocumentOpens() {
        SettingsSeeder.seed(Preferences.alwaysHideMarginsKey, true)
        val runsBefore = ViewCrop.runsSoFar()

        openReader(ViewFixtures.MARGINS)
        val fileHash = reader.awaitFileHashResolved()

        assertEquals(
            "the automatic pass must end in an applied crop",
            ReaderTestHooks.CROP_APPLIED,
            ViewCrop.awaitDetectionFinished(runsBefore),
        )
        ViewCrop.awaitStatusCardGone()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        ViewActionsDialog.assertTileTitle(
            "the crop is on from the moment the document opens, with no manual toggle",
            presentRes = R.string.show_margins,
            absentRes = R.string.crop_margins,
        )

        val row = ViewCrop.awaitStoredCrop(fileHash)
        assertFalse(
            "the automatic pass must store a crop that is not the full page",
            ViewCrop.parse(row.cropMargins!!).isFullPage,
        )
        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-30")
    fun turningAlwaysHideMarginsOnInSettingsAppliesItToTheDocumentAlreadyOpen() {
        openReader(ViewFixtures.MARGINS)
        val fileHash = reader.awaitFileHashResolved()
        ViewActionsDialog.assertTileTitle(
            "the document starts uncropped",
            presentRes = R.string.crop_margins,
            absentRes = R.string.show_margins,
        )
        val runsBefore = ViewCrop.runsSoFar()

        ViewSettings.openFromReader()
        ViewSettings.openPage(SettingsPage.APPEARANCE.titleRes)
        ViewSettings.tapRow(R.string.always_hide_margins)
        Await.untilPreference(Preferences.alwaysHideMarginsKey) { it == true }
        ViewSettings.returnToReader()

        assertEquals(
            "the settings row must have written its key",
            true,
            SettingsSeeder.preferences.all[Preferences.alwaysHideMarginsKey],
        )
        assertEquals(
            "the change must reach the document already open",
            ReaderTestHooks.CROP_APPLIED,
            ViewCrop.awaitDetectionFinished(runsBefore),
        )
        ViewCrop.awaitStatusCardGone()
        ViewActionsDialog.assertTileTitle(
            "the open document is cropped after the return",
            presentRes = R.string.show_margins,
            absentRes = R.string.crop_margins,
        )
        val row = ViewCrop.awaitStoredCrop(fileHash)
        assertFalse("the applied crop must not be the full page", ViewCrop.parse(row.cropMargins!!).isFullPage)
        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-31")
    fun turningAlwaysHideMarginsOffInSettingsUncropsTheDocumentAlreadyOpen() {
        SettingsSeeder.seed(Preferences.alwaysHideMarginsKey, true)
        val runsBefore = ViewCrop.runsSoFar()

        openReader(ViewFixtures.MARGINS)
        ViewCrop.awaitDetectionFinished(runsBefore)
        ViewCrop.awaitStatusCardGone()
        settledFullQuality()

        ViewSettings.openFromReader()
        ViewSettings.openPage(SettingsPage.APPEARANCE.titleRes)
        ViewSettings.tapRow(R.string.always_hide_margins)
        Await.untilPreference(Preferences.alwaysHideMarginsKey) { it == false }
        ViewSettings.returnToReader()

        assertEquals(
            "the settings row must have written its key",
            false,
            SettingsSeeder.preferences.all[Preferences.alwaysHideMarginsKey],
        )
        ViewActionsDialog.assertTileTitle(
            "the setting is symmetric and must uncrop the open document",
            presentRes = R.string.crop_margins,
            absentRes = R.string.show_margins,
        )
        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-32")
    fun aManualCropToggleSurvivesAnUnrelatedSettingsVisit() {
        openReader(ViewFixtures.MARGINS)
        cropByHand()

        ViewSettings.openFromReader()
        ViewSettings.openPage(SettingsPage.READING.titleRes)
        ViewSettings.tapRow(R.string.keep_screen_on)
        Await.untilPreference(Preferences.screenOnKey) { it == true }
        ViewSettings.returnToReader()
        settledFullQuality()

        ViewActionsDialog.assertTileTitle(
            "a per document crop must survive a settings visit for an unrelated reason",
            presentRes = R.string.show_margins,
            absentRes = R.string.crop_margins,
        )
        assertEquals(
            "the unrelated visit must not turn the always hide setting on",
            false,
            SettingsSeeder.preferences.all[Preferences.alwaysHideMarginsKey] ?: false,
        )
        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-34")
    fun turningCropMarginsOffRestoresTheFullPage() {
        openReader(ViewFixtures.MARGINS)
        val fileHash = reader.awaitFileHashResolved()
        cropByHand()
        val storedBefore = ViewCrop.awaitStoredCrop(fileHash).cropMargins
        val identityBefore = readerIdentity()

        ViewActionsDialog.openAndTap(R.string.show_margins)

        Await.until("the reader is recreated by the crop toggle", Await.LONG_TIMEOUT_MS) {
            runCatching { readerIdentity() != identityBefore }.getOrDefault(false)
        }
        awaitReaderReady()

        ViewActionsDialog.assertTileTitle(
            "turning the crop off must be reversible in place",
            presentRes = R.string.crop_margins,
            absentRes = R.string.show_margins,
        )
        assertNotNull("turning the view off must not discard the analysis", Records.byHash(fileHash)?.cropMargins)
        assertEquals(
            "the stored analysis must be untouched by turning the view off",
            storedBefore,
            Records.byHash(fileHash)?.cropMargins,
        )
        enterFullscreen()
        settledFullQuality()
    }

    @Test
    @TestId("VIEW-59")
    fun historyDisabledDoesNotCacheCropMarginsOrAutoScrollSpeed() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)
        SettingsSeeder.seed(Preferences.scrollSpeedKey, GLOBAL_SCROLL_SPEED)
        seedLongOverlayDelay()
        val file = openReader(ViewFixtures.MARGINS)
        val fileHash = reader.awaitFileHashResolved()

        assertEquals(
            "the first manual crop still applies while history is disabled",
            ReaderTestHooks.CROP_APPLIED,
            cropByHand(),
        )
        openAutoScrollControls()
        assertEquals(
            "the first session starts at the global auto scroll speed",
            GLOBAL_SCROLL_SPEED.toString(),
            ViewUi.textOf(R.id.autoScrollSpeedText),
        )
        tapOverlayButton(R.id.incScrollSpeedButton)
        Await.untilValue(
            condition = "the first session's auto scroll speed increases",
            read = { ViewUi.textOf(R.id.autoScrollSpeedText) },
        ) { it == RAISED_SCROLL_SPEED.toString() }
        Records.assertNoRowFor(fileHash)

        finishReader()
        ViewCrop.resetHooks()
        launchAt(file)

        val runsBefore = ViewCrop.runsSoFar()
        assertEquals(
            "reopening without history runs crop detection again instead of restoring a cached crop",
            ReaderTestHooks.CROP_APPLIED,
            cropByHand(),
        )
        assertTrue(
            "the reopened document did not run a fresh crop detection pass",
            ViewCrop.runsSoFar() > runsBefore,
        )
        openAutoScrollControls()
        assertEquals(
            "the reopened document uses the global speed instead of the first session's speed",
            GLOBAL_SCROLL_SPEED.toString(),
            ViewUi.textOf(R.id.autoScrollSpeedText),
        )
        Records.assertNoRowFor(fileHash)
    }

    private fun cropByHand(): String {
        val runsBefore = ViewCrop.runsSoFar()
        ViewActionsDialog.openAndTap(R.string.crop_margins)
        val outcome = ViewCrop.awaitDetectionFinished(runsBefore)
        ViewCrop.awaitStatusCardGone()
        settledFullQuality()
        return outcome
    }

    private fun openAutoScrollControls() {
        enterFullscreenWithOverlay()
        tapOverlayButton(R.id.autoScrollButton)
        ViewUi.awaitVisibility(R.id.autoScrollLayout, View.VISIBLE)
    }

    private fun assertProgressCardStaysGoneUntilRenderIdle() {
        Await.until(
            "the reader reaches an idle render with the progress card never observed visible again",
            ViewCrop.DETECTION_TIMEOUT_MS,
        ) {
            if (ViewUi.visibility(R.id.cropDetectionStatusCard) == View.VISIBLE) {
                throw ConditionTimeoutException(
                    "The crop detection progress card became visible again, so the analysis restarted itself"
                )
            }
            state().render == RenderActivity.IDLE
        }
        Await.assertThroughout(
            condition = "the progress card stays gone once rendering is idle",
            windowMs = STABLE_WINDOW_MS,
        ) { ViewUi.visibility(R.id.cropDetectionStatusCard) == View.GONE }
    }

    private companion object {
        const val GLOBAL_SCROLL_SPEED = 7
        const val RAISED_SCROLL_SPEED = GLOBAL_SCROLL_SPEED + 1
    }
}
