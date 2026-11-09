// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReadingDirectionTest : ViewTestCase() {

    @Test
    @TestId("VIEW-48")
    fun anArabicDocumentIsDetectedRightToLeftInAHorizontalLayout() {
        seedHorizontalLayout()

        openReader(ViewFixtures.ARABIC)
        val fileHash = reader.awaitFileHashResolved()

        val row = Records.awaitRow(
            fileHash,
            "the record stores the detected right to left direction",
            Await.LONG_TIMEOUT_MS,
        ) { it?.detectedReadingDirection == ReadingDirection.RIGHT_TO_LEFT.id }!!

        assertNull("no override may be stored by detection alone", row.readingDirectionOverride)
        Await.until("the viewer applies right to left page order") { state().readingDirectionRtl == true }
        assertEquals(
            "the viewer must report right to left page order",
            true as Boolean?,
            state().readingDirectionRtl,
        )
    }

    @Test
    @TestId("VIEW-49")
    fun detectionDoesNotRunAndRtlIsNotAppliedInAVerticalLayout() {
        openReader(ViewFixtures.ARABIC)
        val fileHash = reader.awaitFileHashResolved()
        Records.awaitRowExists(fileHash)

        Await.assertThroughout(
            condition = "no reading direction is detected while the layout is vertical",
            windowMs = Records.STABLE_ABSENCE_MS,
        ) { Records.byHash(fileHash)?.detectedReadingDirection == null }

        assertEquals(
            "a vertical layout has no left or right page order to get wrong",
            false as Boolean?,
            state().readingDirectionRtl,
        )
        ViewActionsDialog.assertTileAbsent(
            "the reading direction action is gated on the horizontal layout",
            R.string.reading_direction,
        )
    }

    @Test
    @TestId("VIEW-50")
    fun overridingTheReadingDirectionReloadsTheDocumentInTheChosenOrder() {
        seedHorizontalLayout()
        openReader(ViewFixtures.ARABIC)
        val fileHash = reader.awaitFileHashResolved()
        Records.awaitRow(fileHash, "the document is detected right to left first", Await.LONG_TIMEOUT_MS) {
            it?.detectedReadingDirection == ReadingDirection.RIGHT_TO_LEFT.id
        }
        val identityBefore = readerIdentity()

        ViewActionsDialog.openAndTap(R.string.reading_direction)
        awaitDialogWithTitle(R.string.reading_direction)

        assertTrue(
            "the automatic item must report the detected direction",
            TestEnvironment.uiDevice.hasObject(By.text(ViewUi.string(R.string.reading_direction_auto_rtl))),
        )
        chooseDirectionItem(R.string.reading_direction_ltr)
        tapDialogPositive()

        awaitReaderRecreated(identityBefore)

        val row = Records.awaitRow(fileHash, "the record stores the left to right override", Await.LONG_TIMEOUT_MS) {
            it?.readingDirectionOverride == ReadingDirection.LEFT_TO_RIGHT.id
        }!!
        assertEquals(
            "the automatic result must be kept alongside the override",
            ReadingDirection.RIGHT_TO_LEFT.id,
            row.detectedReadingDirection,
        )
        assertEquals(
            "the viewer must apply the chosen left to right order",
            false as Boolean?,
            state().readingDirectionRtl,
        )
    }

    @Test
    @TestId("VIEW-51")
    @TestDuration(DurationCategory.NORMAL)
    fun aReadingDirectionOverrideSurvivesReopeningTheDocument() {
        seedHorizontalLayout()
        val file = openReader(ViewFixtures.ARABIC)
        val fileHash = reader.awaitFileHashResolved()
        Records.awaitRow(fileHash, "the document is detected right to left first", Await.LONG_TIMEOUT_MS) {
            it?.detectedReadingDirection == ReadingDirection.RIGHT_TO_LEFT.id
        }
        val identityBefore = readerIdentity()

        ViewActionsDialog.openAndTap(R.string.reading_direction)
        awaitDialogWithTitle(R.string.reading_direction)
        chooseDirectionItem(R.string.reading_direction_ltr)
        tapDialogPositive()
        awaitReaderRecreated(identityBefore)
        Records.awaitRow(fileHash, "the override is stored before the reopen", Await.LONG_TIMEOUT_MS) {
            it?.readingDirectionOverride == ReadingDirection.LEFT_TO_RIGHT.id
        }

        finishReader()
        launchAt(file)

        assertEquals(
            "the override must be applied at load rather than the detected direction",
            false as Boolean?,
            state().readingDirectionRtl,
        )

        ViewActionsDialog.openAndTap(R.string.reading_direction)
        awaitDialogWithTitle(R.string.reading_direction)
        val ltrItem = directionItem(R.string.reading_direction_ltr)
        assertTrue(
            "the dialog must open with the stored left to right choice already selected",
            ltrItem.isChecked,
        )
        tapDialogNegative()
    }

    @Test
    @TestId("VIEW-60")
    fun aReloadAppliesTheReadingDirectionAnotherWindowStored() {
        seedHorizontalLayout()
        openReader(ViewFixtures.ARABIC)
        val fileHash = reader.awaitFileHashResolved()
        Records.awaitRow(fileHash, "the document is detected right to left first", Await.LONG_TIMEOUT_MS) {
            it?.detectedReadingDirection == ReadingDirection.RIGHT_TO_LEFT.id
        }
        assertEquals("the detected right to left order is applied", true as Boolean?, state().readingDirectionRtl)
        val identityBefore = readerIdentity()

        Records.storeReadingDirectionOverride(fileHash, ReadingDirection.LEFT_TO_RIGHT.id)
        ReaderProbe.onReader { it.reloadPdf() }
        awaitReaderRecreated(identityBefore)

        assertEquals(
            "the reload must apply the left to right override stored by another window",
            false as Boolean?,
            state().readingDirectionRtl,
        )
        Records.assertHolds("the reload must not write the stale direction back over the stored override") {
            Records.byHash(fileHash)?.readingDirectionOverride == ReadingDirection.LEFT_TO_RIGHT.id
        }
    }

    private fun seedHorizontalLayout() {
        SettingsSeeder.seedReadingMode(singlePage = false, horizontalScroll = true, dualPage = false)
    }

    private fun directionItem(titleRes: Int): UiObject2 = Await.untilNotNull(
        "the reading direction dialog lists \"${ViewUi.string(titleRes)}\"",
    ) { TestEnvironment.uiDevice.findObject(By.text(ViewUi.string(titleRes))) }

    private fun chooseDirectionItem(titleRes: Int) {
        directionItem(titleRes).click()
        Await.until("the chosen reading direction item reports checked") {
            directionItem(titleRes).isChecked
        }
    }

    private fun awaitReaderRecreated(identityBefore: Int) {
        Await.until("the reader is recreated by the reading direction change", Await.LONG_TIMEOUT_MS) {
            runCatching { readerIdentity() != identityBefore }.getOrDefault(false)
        }
        awaitReaderReady()
    }
}
