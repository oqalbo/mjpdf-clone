// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FormFieldTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-39")
    fun fillingASingleLineTextFieldRecordsTheEntry() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()
            val field = AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME)

            tapPdfRect(AnnotationFixtures.fullNameRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FULL_NAME_ALTERNATE)
            assertEquals(
                "the dialog is titled with the field's alternate name",
                AnnotationFixtures.FULL_NAME_ALTERNATE,
                AnnotationDialogs.titleText(),
            )

            AnnotationDialogs.typeIntoEditor(FULL_NAME_VALUE)
            AnnotationDialogs.pressPositive()

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the journalled operation", AnnotationJournalFile.OP_SET_FIELD_TEXT, line.op)
            assertEquals("the journalled page index", 0, line.page)
            assertEquals("the journalled field index", field.index, line.fieldIndex)
            assertEquals("the journalled field name", field.name, line.fieldName)
            assertEquals("the journalled value", FULL_NAME_VALUE, line.text)

            AnnotationDump.awaitFormFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME) {
                it.value == FULL_NAME_VALUE
            }
            AnnotationReaderUi.awaitSaveFabVisible()
        }
    }

    @Test
    @TestId("ANN-40")
    fun aFieldWithNoAlternateNameFallsBackToItsOwnName() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            tapPdfRect(AnnotationFixtures.commentsRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FIELD_COMMENTS)

            val title = AnnotationDialogs.titleText()
            assertEquals("the leaf field name is used as the title", AnnotationFixtures.FIELD_COMMENTS, title)
            assertNotEquals(
                "a field carrying a usable name must not fall back to the generic label",
                string(R.string.form_field),
                title,
            )
        }
    }

    @Test
    @TestId("ANN-41")
    fun aMultilineFieldKeepsItsLineBreaks() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.commentsRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FIELD_COMMENTS)
            AnnotationDialogs.typeIntoEditor(MULTILINE_VALUE)
            AnnotationDialogs.pressPositive()

            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()
            assertEquals("the line break survives into the journal", MULTILINE_VALUE, line.text)
            AnnotationDump.awaitFormFieldNamed(0, AnnotationFixtures.FIELD_COMMENTS) {
                it.value == MULTILINE_VALUE_AS_STORED
            }
        }
    }

    @Test
    @TestId("ANN-42")
    fun cancellingTheTextFieldDialogRecordsNothing() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()
            val before = AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME).value

            tapPdfRect(AnnotationFixtures.fullNameRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FULL_NAME_ALTERNATE)
            AnnotationDialogs.typeIntoEditor(ABANDONED_VALUE)
            AnnotationDialogs.pressNegative()

            AnnotationJournalFile.assertStaysAbsent(journal)
            assertEquals(
                "an abandoned entry must leave the field as it was",
                before,
                AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME).value,
            )
            assertFalse(
                "an abandoned entry must not put the document into an unsaved state",
                AnnotationReaderUi.saveFab().visible,
            )
        }
    }

    @Test
    @TestId("ANN-43")
    @TestDuration(DurationCategory.NORMAL)
    fun confirmingAnUnchangedValueRecordsNothing() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.fullNameRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FULL_NAME_ALTERNATE)
            AnnotationDialogs.typeIntoEditor(FULL_NAME_VALUE)
            AnnotationDialogs.pressPositive()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapPdfRect(AnnotationFixtures.fullNameRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FULL_NAME_ALTERNATE)
            assertEquals(
                "the dialog reopens on the value the field already holds",
                FULL_NAME_VALUE,
                AnnotationDialogs.editorText(),
            )
            AnnotationDialogs.pressPositive()

            AnnotationJournalFile.assertLineCountStays(journal, 1)
        }
    }

    @Test
    @TestId("ANN-44")
    fun aCheckboxCanBeTickedAndUntickedAndEachChangeIsRecorded() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()
            val field = AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_SUBSCRIBE)
            assertFalse("the fixture checkbox starts unchecked", field.checked)

            tapCheckboxAndAwaitRedraw()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapCheckboxAndAwaitRedraw()

            val lines = AnnotationJournalFile.awaitLineCount(journal, 2)
            assertEquals(
                "both edits are checkbox edits",
                listOf(AnnotationJournalFile.OP_SET_FIELD_CHECKED, AnnotationJournalFile.OP_SET_FIELD_CHECKED),
                lines.map { it.op },
            )
            assertEquals(
                "both edits name the same field index",
                listOf(field.index, field.index),
                lines.map { it.fieldIndex },
            )
            assertEquals(
                "both edits name the same field",
                listOf(field.name, field.name),
                lines.map { it.fieldName },
            )
            assertEquals(
                "the checkbox is ticked and then unticked",
                listOf(true, false),
                lines.map { it.checked },
            )
            AnnotationDump.awaitFormFieldNamed(0, AnnotationFixtures.FIELD_SUBSCRIBE) { !it.checked }
        }
    }

    @Test
    @TestId("ANN-45")
    fun aRadioOptionIsSelectedOnceAndNotReRecorded() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.planFirstRect)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapPdfRect(AnnotationFixtures.planFirstRect)
            AnnotationJournalFile.assertLineCountStays(journal, 1)

            val line = AnnotationJournalFile.lines(journal).single()
            assertEquals("the journalled operation", AnnotationJournalFile.OP_SET_FIELD_CHECKED, line.op)
            assertEquals("picking a radio option sets it", true, line.checked)

            val controls = AnnotationDump.radioControls(0, AnnotationFixtures.FIELD_PLAN)
            assertEquals("the fixture radio group carries two controls", 2, controls.size)
            assertEquals(
                "exactly the option that was tapped reads as checked",
                listOf(true, false),
                controls.sortedBy { it.index }.map { it.checked },
            )
        }
    }

    @Test
    @TestId("ANN-46")
    @TestDuration(DurationCategory.NORMAL)
    fun aReadOnlyFieldCannotBeFilledAndTheTapFallsThrough() {
        SettingsSeeder.seed(Preferences.hideDelayKey, LONG_HIDE_DELAY_MS)

        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()
            val before = AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_REFERENCE)
            assertTrue("the fixture reference field is read only", before.readOnly)
            assertEquals(
                "the fixture reference field starts with its committed value",
                AnnotationFixtures.REFERENCE_VALUE,
                before.value,
            )

            AnnotationReaderUi.requireScrollHandleHidden()
            tapPdfRect(AnnotationFixtures.referenceRect)

            AnnotationDialogs.assertTitleNeverShown(AnnotationFixtures.FIELD_REFERENCE)
            AnnotationJournalFile.assertStaysAbsent(journal)
            AnnotationReaderUi.awaitScrollHandleVisible(true)
            assertEquals(
                "a locked field keeps its value",
                AnnotationFixtures.REFERENCE_VALUE,
                AnnotationDump.formFieldNamed(0, AnnotationFixtures.FIELD_REFERENCE).value,
            )
        }
    }

    @Test
    @TestId("ANN-47")
    fun formValuesSurviveAPageTurn() {
        openReader(AnnotationFixtures.FORMS) { _, _ ->
            val journal = journal()

            tapPdfRect(AnnotationFixtures.fullNameRect)
            AnnotationDialogs.awaitTitle(AnnotationFixtures.FULL_NAME_ALTERNATE)
            AnnotationDialogs.typeIntoEditor(FULL_NAME_VALUE)
            AnnotationDialogs.pressPositive()
            AnnotationJournalFile.awaitLineCount(journal, 1)

            AnnotationGeometry.jumpToPage(1)
            reader.awaitPageIndex(1, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            AnnotationGeometry.jumpToPage(0)
            reader.awaitPageIndex(0, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

            AnnotationDump.awaitFormFieldNamed(0, AnnotationFixtures.FIELD_FULL_NAME) {
                it.value == FULL_NAME_VALUE
            }
            AnnotationJournalFile.assertLineCountStays(journal, 1)
        }
    }

    private fun tapCheckboxAndAwaitRedraw() {
        val generationBefore = reader.readState().pageGeneration
        tapPdfRect(AnnotationFixtures.subscribeRect)
        reader.awaitState("the page reports its content changed") {
            (it.pageGeneration ?: 0) > (generationBefore ?: 0)
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val FULL_NAME_VALUE = "Ada Lovelace"
        const val ABANDONED_VALUE = "discard me"
        const val MULTILINE_VALUE = "first line\nsecond line"
        const val MULTILINE_VALUE_AS_STORED = "first line\r\nsecond line"
        const val LONG_HIDE_DELAY_MS = 60_000
    }
}
