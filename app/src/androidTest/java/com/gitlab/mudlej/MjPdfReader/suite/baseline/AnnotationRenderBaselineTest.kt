// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.annotations.SignaturePad
import com.gitlab.mudlej.MjPdfReader.suite.annotations.StampRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationGeometry
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveSheet
import com.gitlab.mudlej.MjPdfReader.suite.annotations.PdfRect
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsHighlights
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionDriver
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionHandle
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionPageText
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionTargets
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class AnnotationRenderBaselineTest : BaselineTestCase() {

    private val selection = SelectionRobot()
    private val selectionDriver = SelectionDriver(reader, selection)
    private val stamp = StampRobot(selection)

    @Test
    @TestId("VIS-33")
    fun everyDefaultPaletteColourRendersAtItsOwnHue() {
        val samples = openPaletteAndSampleHighlights()

        assertEquals(
            "the six palette highlights must sample as six distinct colours, they read $samples",
            BaselineFixtures.PALETTE_COLOURS.size,
            samples.distinct().size,
        )

        assertScreenMatches(
            goldenName = "VIS-33-palette-all-six.png",
            expectation = readerExpectation(
                "palette.pdf page index 0 with six pre existing highlights",
                pageIndex = 0,
            ),
        )
    }

    @Test
    @TestId("VIS-34")
    @TestDuration(DurationCategory.NORMAL)
    fun aFreshlyCreatedHighlightRendersLikeALoadedOne() {
        val file = baselineFixture(BaselineFixtures.GOLDEN_BOOK)
        val page = SelectionPageText.of(file, BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        launchAt(file)
        jumpToPageIndex(BaselineFixtures.BOOK_BODY_PAGE_INDEX)

        val journal = AnnotationJournalFile.fileOfOpenDocument(reader)
        selectionDriver.longPressWord(page, HIGHLIGHT_TARGET)
        ActionsHighlights.awaitActionCard()
        ActionsHighlights.tapFirstSwatch()
        reader.awaitState("the reader reports unsaved annotations") { it.hasUnsavedAnnotations == true }
        val lines = AnnotationJournalFile.awaitLineCount(journal, 1, Await.LONG_TIMEOUT_MS)

        assertEquals("the journal must record one add", AnnotationJournalFile.OP_ADD, lines[0].op)
        assertEquals(
            "the journal entry must be for the golden book body page",
            BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            lines[0].page,
        )

        reader.awaitNoSelection()
        enterFullscreenAndSettle()

        assertScreenMatches(
            goldenName = "VIS-34-highlight-created.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page with one freshly created yellow highlight",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        val freshPage = BaselineCapture.view(R.id.pdfView)

        exitFullscreenFromOverlay()
        AnnotationReaderUi.tapSaveFab()
        SaveSheet.awaitShown()
        SaveSheet.chooseUpdateExisting()
        AnnotationJournalFile.awaitAbsent(journal, Await.VERY_LONG_TIMEOUT_MS)
        reader.awaitState("the highlight is saved", Await.VERY_LONG_TIMEOUT_MS) {
            it.savingAnnotations == false && it.hasUnsavedAnnotations == false
        }
        finishReader()

        launchAt(file)
        jumpToPageIndex(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        enterFullscreenAndSettle()
        Baselines.assertByteIdentical(
            freshPage,
            BaselineCapture.view(R.id.pdfView),
            "the PDF canvas must render identically before saving and after reopening the saved highlight",
        )
    }

    @Test
    @TestId("VIS-35")
    @TestDuration(DurationCategory.NORMAL)
    fun aMultiLineHighlightProducesOneRectanglePerLine() {
        val file = baselineFixture(BaselineFixtures.SPANNING)
        val page = SelectionPageText.of(file)
        val sentenceLines = page.lineRanges.windowed(BaselineFixtures.SPANNING_EXPECTED_RECTANGLES)
            .singleOrNull { ranges ->
                ranges.joinToString(" ") { page.normalized(it).trim() } == BaselineFixtures.SPANNING_SENTENCE
            }
            ?: throw AssertionError(
                "spanning.pdf does not expose ${BaselineFixtures.SPANNING_EXPECTED_RECTANGLES} consecutive " +
                    "lines whose extracted text is the declared spanning sentence. It reads ${page.readable()}"
            )

        launchAt(file)
        selectionDriver.longPressRange(page, sentenceLines.first(), horizontalFraction = 0.15f)
        ActionsHighlights.awaitActionCard()
        val selected = selectionDriver.dragCaretTo(
            SelectionHandle.END,
            SelectionTargets.caretJustAfter(page, sentenceLines.last(), selectionDriver.projection()),
        )
        assertEquals(
            "the selected sentence must cover exactly three rendered line rectangles before it is highlighted",
            BaselineFixtures.SPANNING_EXPECTED_RECTANGLES,
            selectionDriver.selectionRects(selected).size,
        )
        ActionsHighlights.tapFirstSwatch()
        val highlight = AnnotationDump.awaitHighlightGroups(0, 1, Await.LONG_TIMEOUT_MS).single()
        assertEquals(
            "the live highlight must contain exactly one annotation rectangle per rendered line",
            BaselineFixtures.SPANNING_EXPECTED_RECTANGLES,
            highlight.rects,
        )
        reader.awaitNoSelection()
        enterFullscreenAndSettle()

        assertScreenMatches(
            goldenName = "VIS-35-highlight-multiline.png",
            expectation = readerExpectation(
                "spanning.pdf page index 0 with a three line highlight",
                pageIndex = 0,
            ),
        )
    }

    @Test
    @TestId("VIS-36")
    fun highlightsStayDistinctlyColouredThroughTheNightModeOverlay() {
        SettingsSeeder.seed(Preferences.pdfPagesThemeKey, Preferences.themeDark)
        val samples = openPaletteAndSampleHighlights()

        assertEquals(
            "inverted highlights must stay six distinct colours rather than collapsing, they read $samples",
            BaselineFixtures.PALETTE_COLOURS.size,
            samples.distinct().size,
        )

        assertScreenMatches(
            goldenName = "VIS-36-highlight-night-mode.png",
            expectation = readerExpectation(
                "palette.pdf page index 0 inverted with six highlights",
                pageIndex = 0,
            ),
        )
    }

    @Test
    @TestId("VIS-37")
    @TestDuration(DurationCategory.NORMAL)
    fun aCommittedSignatureStampLandsWhereItWasPlaced() {
        BaselineFixtures.seedSignature(SignaturePad.storedSignatureFile().parentFile!!)

        val file = baselineFixture(BaselineFixtures.GOLDEN_BOOK)
        launchAt(file)
        jumpToPageIndex(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        val journal = AnnotationJournalFile.fileOfOpenDocument(reader)

        SignaturePad.openFromReaderActions()
        SignaturePad.awaitDialog()
        SignaturePad.awaitPlaceEnabled(true)
        SignaturePad.pressPlace()

        val committed = moveAndResizeThePendingStamp()

        AnnotationReaderUi.tapSaveFab()
        val lines = AnnotationJournalFile.awaitLineCount(journal, 1, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "the journal must record one signature stamp for the golden book body page",
            AnnotationJournalFile.OP_ADD_SIGNATURE,
            lines[0].op,
        )
        assertEquals("the journalled page index", BaselineFixtures.BOOK_BODY_PAGE_INDEX, lines[0].page)
        assertEquals("the journalled left edge", committed.left, lines[0].rect[0], ONE_PDF_UNIT)
        assertEquals("the journalled top edge", committed.top, lines[0].rect[1], ONE_PDF_UNIT)
        assertEquals("the journalled right edge", committed.right, lines[0].rect[2], ONE_PDF_UNIT)
        assertEquals("the journalled bottom edge", committed.bottom, lines[0].rect[3], ONE_PDF_UNIT)

        TestEnvironment.uiDevice.pressBack()
        AnnotationDump.awaitNoPendingStamp()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
        enterFullscreenAndSettle()

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-37-signature-stamp.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page with a committed signature stamp",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertDiffers(
            capture,
            GOLDEN_BOOK_BODY,
            "a committed signature stamp must reach the page pixels rather than living only in the journal",
        )
    }

    @Test
    @TestId("VIS-38")
    fun savedFormValuesRenderUnderTheIndicatorOverlay() {
        openFullscreenPage(BaselineFixtures.FORM_VALUES)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-38-form-values.png",
            actual = capture,
            expectation = readerExpectation(
                "formvalues.pdf page index 0 with saved field values",
                pageIndex = 0,
            ),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-07-forms-indicators.png",
            "a form with saved values must not render identically to the same form left empty",
        )
    }

    private fun moveAndResizeThePendingStamp(): PdfRect {
        val placed = AnnotationDump.awaitPendingStamp().rect
        val page = AnnotationGeometry.frameOf(state())
        val move = page.viewBounds.width() * STAMP_MOVE_FRACTION
        val grow = page.viewBounds.width() * STAMP_RESIZE_FRACTION

        stamp.dragCentreBy(page, -move, -move)
        val moved = stamp.awaitStampSettled(placed)

        stamp.dragBottomRightBy(page, grow, grow)
        val resized = stamp.awaitStampSettled(moved)

        assertTrue(
            "the corner drag must widen the stamp, otherwise this golden records the placement the app chose " +
                "rather than the one the test asked for. It read $moved then $resized",
            resized.width > moved.width,
        )
        return resized
    }

    private fun openPaletteAndSampleHighlights(): List<Int> {
        openFullscreenPage(BaselineFixtures.PALETTE)
        val capture = captureScreen()
        return BaselineFixtures.PALETTE_RECTANGLES.map { rect ->
            val point = screenPointOf(rect)
            CrossGolden.pixel(capture, point.x.toInt(), point.y.toInt())
        }
    }

    private fun screenPointOf(rect: PdfRect): PointF {
        val frame = AnnotationGeometry.frameOf(state())
        val viewPoint = frame.toViewPoint(rect.left + rect.width / 2f, rect.centerY)
        val origin = IntArray(2)
        ViewUi.onView(R.id.pdfView) { view -> view.getLocationOnScreen(origin) }
        return PointF(origin[0] + viewPoint.x, origin[1] + viewPoint.y)
    }

    private companion object {
        const val GOLDEN_BOOK_BODY = "VIS-01-tlcl-page41.png"
        const val HIGHLIGHT_TARGET = "picture.jpg"
        const val STAMP_MOVE_FRACTION = 0.15f
        const val STAMP_RESIZE_FRACTION = 0.1f
        const val ONE_PDF_UNIT = 1f
    }
}
