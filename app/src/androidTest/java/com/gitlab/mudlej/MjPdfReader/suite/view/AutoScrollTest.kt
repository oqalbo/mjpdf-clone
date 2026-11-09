// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AutoScrollTest : ViewTestCase() {

    @Test
    @TestId("VIEW-38")
    fun autoScrollMovesTheDocumentAndThePlayButtonBecomesPause() {
        seedLongOverlayDelay()
        openReader()
        openAutoScrollControls()

        val before = state()
        startAutoScroll()

        val travelled = ViewMotion.awaitMonotonic(
            condition = "the vertical offset advances while auto scroll runs",
            steps = MOTION_SAMPLES,
            increasing = false,
        ) { state().offsetY!! }

        assertTrue(
            "the offset must advance across every sample, read $travelled",
            travelled.zipWithNext().all { (first, second) -> second < first },
        )
        assertTrue(
            "the document scroll ratio must not go backwards, from ${before.positionOffset} to " +
                "${state().positionOffset}",
            state().positionOffset!! >= before.positionOffset!!,
        )
        ViewUi.awaitIcon(R.id.toggleAutoScrollButton, R.drawable.ic_pause)
        assertEquals(
            "the readout must show the default global speed",
            DEFAULT_SPEED_TEXT,
            ViewUi.textOf(R.id.autoScrollSpeedText),
        )
    }

    @Test
    @TestId("VIEW-39")
    @TestDuration(DurationCategory.LONG)
    fun pausingAutoScrollStopsTheMovement() {
        seedLongOverlayDelay()
        SettingsSeeder.seed(Preferences.scrollSpeedKey, BRISK_SPEED)
        openReader()
        openAutoScrollControls()
        startAutoScroll()
        awaitForwardMovement()

        tapOverlayButton(R.id.toggleAutoScrollButton)

        Await.until("the reader stops reporting auto scrolling") { state().autoScrolling == false }
        ViewMotion.assertStable(
            condition = "the vertical offset stops moving once auto scroll is paused",
            windowMs = STABLE_WINDOW_MS,
        ) { state().offsetY!! }
        ViewMotion.assertStable(
            condition = "the document scroll ratio stops moving once auto scroll is paused",
            windowMs = STABLE_WINDOW_MS,
        ) { state().positionOffset!! }
        ViewUi.awaitIcon(R.id.toggleAutoScrollButton, R.drawable.ic_play_arrow)
    }

    @Test
    @TestId("VIEW-40")
    fun changingTheSpeedUpdatesTheReadoutAndIsStoredPerDocument() {
        seedLongOverlayDelay()
        openReader()
        val fileHash = reader.awaitFileHashResolved()
        openAutoScrollControls()

        assertEquals("the readout starts at the global default", DEFAULT_SPEED_TEXT, ViewUi.textOf(R.id.autoScrollSpeedText))

        tapOverlayButton(R.id.incScrollSpeedButton)
        tapOverlayButton(R.id.incScrollSpeedButton)

        awaitSpeedText("5")
        Records.awaitRow(fileHash, "the record stores the raised auto scroll speed") { it?.autoScrollSpeed == 5 }
        val globalSpeed = SettingsSeeder.preferences.all[Preferences.scrollSpeedKey]
        assertTrue(
            "a per document speed must not overwrite the global default, but the store holds $globalSpeed",
            globalSpeed == null || globalSpeed == Preferences.scrollSpeedDefault,
        )
    }

    @Test
    @TestId("VIEW-41")
    @TestDuration(DurationCategory.NORMAL)
    fun theSpeedCannotBeDrivenBelowOne() {
        seedLongOverlayDelay()
        openReader()
        val fileHash = reader.awaitFileHashResolved()
        openAutoScrollControls()

        repeat(DECREASE_TAPS) { tapOverlayButton(R.id.decScrollSpeedButton) }

        awaitSpeedText("1")
        Records.awaitRow(fileHash, "the record floors the auto scroll speed at one") { it?.autoScrollSpeed == 1 }

        startAutoScroll()
        ViewMotion.awaitMonotonic(
            condition = "the slowest speed still moves the document",
            steps = MOTION_SAMPLES,
            timeoutMs = SLOW_MOVEMENT_TIMEOUT_MS,
            increasing = false,
        ) { state().offsetY!! }
    }

    @Test
    @TestId("VIEW-42")
    @TestDuration(DurationCategory.LONG)
    fun reversingTheDirectionScrollsBackUp() {
        seedLongOverlayDelay()
        openReader()
        openAutoScrollControls()
        placeViewportInTheMiddle()

        startAutoScroll()
        awaitForwardMovement()
        val speedBefore = ViewUi.textOf(R.id.autoScrollSpeedText)

        tapOverlayButton(R.id.reverseScrollDirectionButton)

        val travelled = ViewMotion.awaitMonotonic(
            condition = "the vertical offset goes back up after the reverse",
            steps = MOTION_SAMPLES,
            increasing = true,
        ) { state().offsetY!! }
        assertTrue(
            "the offset must rise across every sample, read $travelled",
            travelled.zipWithNext().all { (first, second) -> second > first },
        )
        assertEquals(
            "reversing must not change the speed",
            speedBefore,
            ViewUi.textOf(R.id.autoScrollSpeedText),
        )
    }

    @Test
    @TestId("VIEW-43")
    fun aManualTouchPausesAutoScrollAndLiftingResumesIt() {
        seedLongOverlayDelay()
        SettingsSeeder.seed(Preferences.scrollSpeedKey, BRISK_SPEED)
        openReader()
        openAutoScrollControls()
        startAutoScroll()
        awaitForwardMovement()
        ViewUi.awaitIcon(R.id.toggleAutoScrollButton, R.drawable.ic_pause)

        val point = ViewUi.canvasCentre()
        val downTime = RawInput.down(point)
        ViewMotion.assertStable(
            condition = "the document holds still while a finger is down",
            windowMs = TOUCH_HOLD_WINDOW_MS,
        ) { state().offsetY!! }
        assertTrue(
            "a touch pause is not a stop, so the toggle must still show pause",
            ViewUi.iconMatches(R.id.toggleAutoScrollButton, R.drawable.ic_pause),
        )
        RawInput.up(downTime, point)

        ViewMotion.awaitMonotonic(
            condition = "the document resumes on its own once the finger lifts",
            steps = MOTION_SAMPLES,
            increasing = false,
        ) { state().offsetY!! }
        assertTrue(
            "the toggle must still show pause after the finger lifts",
            ViewUi.iconMatches(R.id.toggleAutoScrollButton, R.drawable.ic_pause),
        )
    }

    @Test
    @TestId("VIEW-44")
    @TestDuration(DurationCategory.LONG)
    fun leavingTheReaderStopsAutoScroll() {
        seedLongOverlayDelay()
        SettingsSeeder.seed(Preferences.scrollSpeedKey, BRISK_SPEED)
        val file = openReader()
        val fileHash = reader.awaitFileHashResolved()
        openAutoScrollControls()

        tapOverlayButton(R.id.incScrollSpeedButton)
        awaitSpeedText(RAISED_BRISK_SPEED_TEXT)

        startAutoScroll()
        awaitForwardMovement()

        backgroundAndReturn(file)
        settledFullQuality()

        ViewMotion.assertStable(
            condition = "the document does not scroll itself while the reader is away",
            windowMs = STABLE_WINDOW_MS,
        ) { state().offsetY!! }
        ViewMotion.assertStable(
            condition = "the document scroll ratio holds still after the return",
            windowMs = STABLE_WINDOW_MS,
        ) { state().positionOffset!! }
        assertEquals("the reader must not report auto scrolling", false as Boolean?, state().autoScrolling)
        ViewUi.awaitIcon(R.id.toggleAutoScrollButton, R.drawable.ic_play_arrow)
        Records.awaitRow(fileHash, "the pending speed save is flushed on stop") {
            it?.autoScrollSpeed == RAISED_BRISK_SPEED_TEXT.toInt()
        }
    }

    @Test
    @TestId("VIEW-45")
    @TestDuration(DurationCategory.NORMAL)
    fun autoScrollStopsOnItsOwnAtTheEndOfTheDocument() {
        seedLongOverlayDelay()
        SettingsSeeder.seed(Preferences.scrollSpeedKey, FAST_SPEED)
        openReader(ViewFixtures.THREE_PAGE)
        openAutoScrollControls()

        val startPosition = state().positionOffset!!
        tapOverlayButton(R.id.toggleAutoScrollButton)
        reader.awaitState("auto scroll advances the document from its starting position") {
            it.positionOffset != null && it.positionOffset!! > startPosition
        }

        reader.awaitState("the document reaches its end", END_OF_DOCUMENT_TIMEOUT_MS) {
            it.positionOffset != null && it.positionOffset!! >= END_OF_DOCUMENT
        }

        ViewUi.awaitIcon(R.id.toggleAutoScrollButton, R.drawable.ic_play_arrow)
        assertEquals("auto scroll must stop itself at the end", false as Boolean?, state().autoScrolling)
        ViewMotion.assertStable(
            condition = "the document scroll ratio stops changing at the end",
            windowMs = STABLE_WINDOW_MS,
        ) { state().positionOffset!! }
        assertEquals(
            "the reader must come to rest on the last page, zero based",
            (ViewFixtures.THREE_PAGE_PAGE_COUNT - 1) as Int?,
            state().page,
        )
    }

    @Test
    @TestId("VIEW-46")
    @TestDuration(DurationCategory.NORMAL)
    fun theStoredSpeedIsRestoredPerDocumentAndDoesNotBleedBetweenDocuments() {
        seedLongOverlayDelay()
        val normal = openReader()
        val normalHash = reader.awaitFileHashResolved()
        openAutoScrollControls()
        repeat(RAISE_TO_SIX_TAPS) { tapOverlayButton(R.id.incScrollSpeedButton) }
        awaitSpeedText("6")
        Records.awaitRow(normalHash, "the first document stores its raised speed") { it?.autoScrollSpeed == 6 }

        finishReader()
        val threePage = fixture(ViewFixtures.THREE_PAGE)
        launchAt(threePage)
        val threePageHash = reader.awaitFileHashResolved()
        openAutoScrollControls()

        assertEquals(
            "a second document must start at the global default, not at the first document's speed",
            DEFAULT_SPEED_TEXT,
            ViewUi.textOf(R.id.autoScrollSpeedText),
        )
        Records.awaitRowExists(threePageHash)
        assertNull(
            "an untouched document must store no speed of its own",
            Records.byHash(threePageHash)?.autoScrollSpeed,
        )

        finishReader()
        launchAt(normal)
        openAutoScrollControls()

        assertEquals(
            "returning to the first document must restore its own speed",
            "6",
            ViewUi.textOf(R.id.autoScrollSpeedText),
        )
    }

    @Test
    @TestId("VIEW-47")
    fun autoScrollMovesAlongTheHorizontalAxisInAHorizontalLayout() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)
        SettingsSeeder.seed(Preferences.scrollSpeedKey, FAST_SPEED)
        seedLongOverlayDelay()
        openReader()
        openAutoScrollControls()

        val before = state()
        assertEquals("the resolved layout must swipe horizontally", false as Boolean?, before.swipeVertical)

        startAutoScroll()

        val travelled = ViewMotion.awaitMonotonic(
            condition = "the horizontal offset advances while auto scroll runs",
            steps = MOTION_SAMPLES,
            increasing = false,
        ) { state().offsetX!! }
        assertTrue(
            "the horizontal offset must advance across every sample, read $travelled",
            travelled.zipWithNext().all { (first, second) -> second < first },
        )
        assertTrue(
            "the vertical offset must stay put in a horizontal layout, moving from ${before.offsetY} to " +
                "${state().offsetY}",
            abs(state().offsetY!! - before.offsetY!!) <= VERTICAL_TOLERANCE_PX,
        )
        reader.awaitState("the page advances past the first one", PAGE_ADVANCE_TIMEOUT_MS) { (it.page ?: 0) > 0 }
    }

    private fun openAutoScrollControls() {
        enterFullscreenWithOverlay()
        tapOverlayButton(R.id.autoScrollButton)
        ViewUi.awaitVisibility(R.id.autoScrollLayout, View.VISIBLE)
    }

    private fun startAutoScroll() {
        tapOverlayButton(R.id.toggleAutoScrollButton)
        Await.until("the reader reports auto scrolling") { state().autoScrolling == true }
    }

    private fun awaitForwardMovement() {
        ViewMotion.awaitMonotonic(
            condition = "the document is moving before the next step",
            steps = 1,
            increasing = false,
        ) { state().offsetY!! }
    }

    private fun awaitSpeedText(expected: String) {
        Await.untilValue(
            condition = "the auto scroll readout reaches \"$expected\"",
            read = { ViewUi.textOf(R.id.autoScrollSpeedText) },
        ) { it == expected }
    }

    private fun placeViewportInTheMiddle() {
        val current = state()
        val target = -(current.documentLength!! * MIDDLE_OF_DOCUMENT)
        setViewport(current.zoom!!, current.offsetX!!, target)
        settledFullQuality()
    }

    private companion object {
        const val DEFAULT_SPEED_TEXT = "3"
        const val BRISK_SPEED = 60
        const val FAST_SPEED = 120
        const val RAISED_BRISK_SPEED_TEXT = "61"
        const val DECREASE_TAPS = 6
        const val RAISE_TO_SIX_TAPS = 3
        const val MIDDLE_OF_DOCUMENT = 0.4f
        const val VERTICAL_TOLERANCE_PX = 1f
        const val TOUCH_HOLD_WINDOW_MS = 300L
        const val SLOW_MOVEMENT_TIMEOUT_MS = 30_000L
        const val END_OF_DOCUMENT = 0.999f
        const val END_OF_DOCUMENT_TIMEOUT_MS = 60_000L
        const val PAGE_ADVANCE_TIMEOUT_MS = 30_000L
    }
}
