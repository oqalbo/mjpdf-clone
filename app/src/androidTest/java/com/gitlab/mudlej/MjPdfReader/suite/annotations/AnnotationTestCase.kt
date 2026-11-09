// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.content.Intent
import android.graphics.PointF
import android.view.ViewConfiguration
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Rule
import java.io.File

abstract class AnnotationTestCase {

    protected val suite = SuiteRules.standard()

    @get:Rule
    val rules: SuiteRules = suite

    protected val reader = ReaderRobot()
    protected val selection = SelectionRobot()

    @After
    fun releaseAnnotationState() {
        runCatching { AnnotationReaderUi.reset() }
    }

    protected fun fixture(name: String): File = AnnotationFixtures.place(name, suite.testDirectory.directory())

    protected fun intentFor(file: File, incognito: Boolean = false): Intent =
        AnnotationReading.intentFor(file, incognito)

    protected fun launch(file: File, incognito: Boolean = false): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(intentFor(file, incognito))
        awaitReaderReady()
        return scenario
    }

    protected fun openReader(
        name: String = AnnotationFixtures.NORMAL,
        incognito: Boolean = false,
        body: (ActivityScenario<MainActivity>, File) -> Unit,
    ) {
        val file = fixture(name)
        launch(file, incognito).use { scenario -> body(scenario, file) }
    }

    protected fun awaitReaderReady(): ReaderState = AnnotationReading.awaitReaderReady(reader)

    protected fun journal(): File = AnnotationJournalFile.fileOfOpenDocument(reader)

    protected fun frame(): PageFrame = AnnotationGeometry.frameOf(reader.readState())

    protected fun viewPointOf(rect: PdfRect, horizontalFraction: Float = 0.5f): PointF =
        AnnotationReading.viewPointOf(reader, rect, horizontalFraction)

    protected fun tapPdfRect(rect: PdfRect, horizontalFraction: Float = 0.5f) {
        val point = viewPointOf(rect, horizontalFraction)
        val origin = selection.readerViewOriginOnScreen()
        TestEnvironment.uiDevice.click((origin.x + point.x).toInt(), (origin.y + point.y).toInt())
        TestEnvironment.waitForIdleSync()
    }

    protected fun tapBlankSpot() {
        val state = reader.readState()
        val page = AnnotationGeometry.frameOf(state)
        val point = page.toViewPoint(
            AnnotationFixtures.PAGE_WIDTH_POINTS / 2f,
            BLANK_SPOT_PDF_Y,
        )
        AnnotationGeometry.requireOnScreen(state, point, "the blank spot below the body text")
        val origin = selection.readerViewOriginOnScreen()
        TestEnvironment.uiDevice.click((origin.x + point.x).toInt(), (origin.y + point.y).toInt())
        TestEnvironment.waitForIdleSync()
    }

    protected fun longPressPdfRect(rect: PdfRect, horizontalFraction: Float = 0.35f): ReaderState =
        selection.longPressAtViewPoint(viewPointOf(rect, horizontalFraction))

    protected fun longPressNormalBodyLine(lineIndex: Int, horizontalFraction: Float = 0.35f): ReaderState =
        longPressPdfRect(AnnotationFixtures.normalBodyLineRect(lineIndex), horizontalFraction)

    protected fun findSelectionPoint(
        candidates: List<PdfRect>,
        horizontalFraction: Float = SELECTION_FRACTION,
        what: String = "the document body",
    ): PointF = AnnotationReading.findSelectionPoint(reader, selection, candidates, horizontalFraction, what)

    protected fun findSelectionPointOnNormalPage(): PointF = findSelectionPoint(
        NORMAL_SELECTION_LINES.map(AnnotationFixtures::normalBodyLineRect),
        what = "the body of ${AnnotationFixtures.NORMAL}",
    )

    protected fun findSelectionPointOnLargePage(): PointF = findSelectionPoint(
        LARGE_SELECTION_LINES.map(AnnotationFixtures::largeBodyLineRect),
        what = "the body of ${AnnotationFixtures.LARGE}",
    )

    protected fun startSelectionOnNormalPage(): ReaderState {
        findSelectionPointOnNormalPage()
        return reader.readState()
    }

    protected fun longPressViewPoint(point: PointF) {
        RawInput.pressAndHold(
            selection.toScreen(point),
            ViewConfiguration.getLongPressTimeout().toLong() + LONG_PRESS_MARGIN_MS,
        )
        TestEnvironment.waitForIdleSync()
    }

    protected fun tapViewPoint(point: PointF) {
        val screen = selection.toScreen(point)
        TestEnvironment.uiDevice.click(screen.x.toInt(), screen.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    protected fun createHighlightOnNormalPage(): Pair<String, PointF> {
        val point = findSelectionPointOnNormalPage()
        AnnotationCard.tapSwatch(0)
        awaitDirty(true)
        return AnnotationDump.awaitHighlightGroups(0, 1).first().group to point
    }

    protected fun awaitDirty(expected: Boolean) {
        reader.awaitState("the reader reports unsaved annotations $expected") {
            it.hasUnsavedAnnotations == expected
        }
    }

    protected companion object {
        const val BLANK_SPOT_PDF_Y = 40f
        const val SELECTION_FRACTION = 0.3f
        const val LONG_PRESS_MARGIN_MS = 250L
        val NORMAL_SELECTION_LINES = listOf(2, 4, 6, 8, 10)
        val LARGE_SELECTION_LINES = listOf(4, 8, 12, 16, 20)
    }
}
