// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SaveGateRefusalTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-61")
    fun applyingASwatchIsRefusedWhileASaveIsRunning() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            findSelectionPointOnNormalPage()
            AnnotationCard.awaitShown()

            AnnotationReaderUi.beginSeededSave()
            AnnotationCard.clickSwatchWithoutTouch(HighlightPalette.YELLOW)

            Snackbars.awaitText(string(R.string.annotation_edit_blocked_while_saving))
            AnnotationJournalFile.assertStaysAbsent(journal)
            assertTrue(
                "a refused edit must leave the page without highlights",
                AnnotationDump.highlights(0).isEmpty(),
            )
        }
    }

    @Test
    @TestId("ANN-62")
    fun deletingAHighlightIsRefusedWhileASaveIsRunning() {
        openReader(AnnotationFixtures.MJ_AUTHORED) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.singleLineRect)
            AnnotationCard.awaitShown()

            AnnotationReaderUi.beginSeededSave()
            AnnotationCard.clickButtonWithoutTouch(R.id.textSelectionDeleteHighlightButton)

            Snackbars.awaitText(string(R.string.annotation_edit_blocked_while_saving))
            AnnotationJournalFile.assertStaysAbsent(journal)
            AnnotationDump.requireHighlightGroup(0, AnnotationFixtures.SINGLE_LINE_GROUP)
            assertTrue(
                "the delete path returns before the card is hidden",
                AnnotationCard.isShown(),
            )
        }
    }

    @Test
    @TestId("ANN-63")
    fun formEntryIsRefusedWhileASaveIsRunning() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()
            val before = AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME).value

            tapPdfRect(AnnotationFixtures.fullNameRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FULL_NAME_ALTERNATE)

            AnnotationReaderUi.beginSeededSave()

            AnnotationDialogs.typeIntoEditor(FULL_NAME_VALUE)
            AnnotationDialogs.pressPositive()

            Snackbars.awaitText(string(R.string.annotation_edit_blocked_while_saving))
            AnnotationJournalFile.assertStaysAbsent(journal)
            assertEquals(
                "a refused entry must leave the field as it was",
                before,
                AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME).value,
            )
        }
    }

    @Test
    @TestId("ANN-64")
    fun bothAnnotationButtonsAreDisabledAndDimmedWhileSaving() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            createHighlightOnNormalPage()
            AnnotationReaderUi.awaitSaveFabVisible()

            AnnotationReaderUi.beginSeededSave()

            val save = AnnotationReaderUi.saveFab()
            val discard = AnnotationReaderUi.discardFab()
            assertTrue("the save button stays on screen while the save runs", save.visible)
            assertFalse("the save button must refuse a second submission", save.enabled)
            assertEquals(
                "the save button must read as busy",
                AnnotationReaderUi.SAVING_ALPHA,
                save.alpha,
                AnnotationReaderUi.ALPHA_TOLERANCE,
            )
            assertTrue("the discard button stays on screen while the save runs", discard.visible)
            assertFalse("the discard button must refuse a submission too", discard.enabled)
            assertEquals(
                "the discard button must read as busy",
                AnnotationReaderUi.SAVING_ALPHA,
                discard.alpha,
                AnnotationReaderUi.ALPHA_TOLERANCE,
            )
            assertTrue("the progress bar must be shown", AnnotationReaderUi.isProgressShown())
            assertTrue("the progress bar must be indeterminate", AnnotationReaderUi.isProgressIndeterminate())
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val FULL_NAME_VALUE = "Ada Lovelace"
    }
}
