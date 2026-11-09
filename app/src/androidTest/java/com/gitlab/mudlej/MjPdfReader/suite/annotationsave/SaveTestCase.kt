// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotationsave

import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReaderUi
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationReading
import com.gitlab.mudlej.MjPdfReader.suite.annotations.PdfRect
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.ControlledPickerDocument
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderStateSource
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Rule
import org.junit.rules.RuleChain
import java.io.File

abstract class SaveTestCase {

    protected val suite = SuiteRules.standard()
    protected val cleanup = SaveCleanupRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(cleanup)

    protected val reader = ReaderRobot()
    protected val selection = SelectionRobot()

    protected fun ownedName(fixtureName: String): String = "${cleanup.prefix()}$fixtureName"

    protected fun place(fixtureName: String, targetName: String = ownedName(fixtureName)): File =
        SaveFixtures.placeInDownloads(fixtureName, targetName)

    protected fun savingSibling(file: File): File =
        File(SaveFixtures.downloads, "${file.name}${SaveFixtures.SAVING_SUFFIX}")

    protected fun grantAllFilesAccess() {
        Fixtures.ensureAllFilesAccess()
        Fixtures.stopSimulatingAccess()
    }

    protected fun withholdAllFilesAccess() {
        Fixtures.simulateNoAllFilesAccess()
    }

    protected fun intentFor(file: File, incognito: Boolean = false): Intent =
        AnnotationReading.intentFor(file, incognito)

    protected fun intentFor(uri: Uri, incognito: Boolean = false): Intent =
        AnnotationReading.intentFor(uri, incognito)

    protected fun launch(file: File, incognito: Boolean = false): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(intentFor(file, incognito))
        awaitReaderReady()
        return scenario
    }

    protected fun launch(uri: Uri, incognito: Boolean = false): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(intentFor(uri, incognito))
        awaitReaderReady()
        return scenario
    }

    protected fun controlledDocument(file: File, documentId: String = file.name): Uri =
        ControlledPickerDocument.writable(file, documentId)

    protected fun openReader(
        fixtureName: String = SaveFixtures.SMALL,
        incognito: Boolean = false,
        body: (ActivityScenario<MainActivity>, File) -> Unit,
    ) {
        val file = place(fixtureName)
        launch(file, incognito).use { scenario -> body(scenario, file) }
    }

    protected fun awaitReaderReady(): ReaderState = AnnotationReading.awaitReaderReady(reader)

    protected fun documentUri(): String = AnnotationJournalFile.documentUriOfOpenDocument(reader)

    protected fun journal(): File = AnnotationJournalFile.fileOfOpenDocument(reader)

    protected fun makeOneHighlight(fixtureName: String = SaveFixtures.SMALL): PointF {
        val point = findSelectionPoint(bodyLinesOf(fixtureName), fixtureName)
        AnnotationCard.tapSwatch(0)
        reader.awaitState("the reader reports unsaved annotations") { it.hasUnsavedAnnotations == true }
        return point
    }

    protected fun findSelectionPoint(candidates: List<PdfRect>, what: String): PointF =
        AnnotationReading.findSelectionPoint(reader, selection, candidates, SELECTION_FRACTION, "the body of $what")

    protected fun viewPointOf(rect: PdfRect, horizontalFraction: Float = SELECTION_FRACTION): PointF =
        AnnotationReading.viewPointOf(reader, rect, horizontalFraction)

    protected fun tapViewPoint(point: PointF) {
        val screen = selection.toScreen(point)
        uiDevice().click(screen.x.toInt(), screen.y.toInt())
        TestEnvironment.waitForIdleSync()
    }

    protected fun bodyLinesOf(fixtureName: String): List<PdfRect> = when (fixtureName) {
        SaveFixtures.SMALL, SaveFixtures.MEDIUM, SaveFixtures.LARGE ->
            DENSE_BODY_LINES.map { line -> denseBodyLineRect(line) }
        SaveFixtures.DECOY -> WIDE_BODY_LINES.map { line -> wideBodyLineRect(line, DECOY_BODY_TOP) }
        else -> WIDE_BODY_LINES.map { line -> wideBodyLineRect(line, NORMAL_BODY_TOP) }
    }

    protected fun denseBodyLineRect(lineIndex: Int): PdfRect {
        val baseline = DENSE_BODY_TOP - DENSE_BODY_LEADING * lineIndex
        return PdfRect(DENSE_BODY_LEFT, baseline - 2f, DENSE_BODY_LEFT + DENSE_BODY_WIDTH, baseline + 8f)
    }

    protected fun wideBodyLineRect(lineIndex: Int, top: Float): PdfRect {
        val baseline = top - WIDE_BODY_LEADING * lineIndex
        return PdfRect(WIDE_BODY_LEFT, baseline - 3f, WIDE_BODY_LEFT + WIDE_BODY_WIDTH, baseline + 10f)
    }

    protected fun tapSaveAndAwaitSheet() {
        AnnotationReaderUi.tapSaveFab()
        SaveSheet.awaitShown()
    }

    protected fun awaitSaveSettled(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        Await.untilValue(
            "no reader on screen still reports a save in flight",
            timeoutMs,
            read = { ReaderStateSource.read()?.savingAnnotations },
        ) { it != true }
    }

    protected fun awaitSaveCompleted(journal: File, timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        awaitSaveSettled(timeoutMs)
        AnnotationJournalFile.awaitAbsent(journal, Await.LONG_TIMEOUT_MS)
        AnnotationReaderUi.awaitSaveFabGone(Await.LONG_TIMEOUT_MS)
    }

    protected fun highlightGroupsOnPage(pageIndex: Int) = AnnotationDump.highlights(pageIndex)

    protected fun openThroughDocumentPicker(fileName: String): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(TestEnvironment.targetContext, MainActivity::class.java)
        )
        val pickerOpenedItself = runCatching { SavePicker.awaitShown(Await.SHORT_TIMEOUT_MS) }.isSuccess
        if (!pickerOpenedItself) {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                    ?: throw ConditionTimeoutException("The reader did not come up to pick a file from")
                val button = activity.findViewById<android.view.View>(com.gitlab.mudlej.MjPdfReader.R.id.pickFileButton)
                    ?: throw ConditionTimeoutException("The empty reader carries no pick file button")
                button.performClick()
            }
        }
        SavePicker.chooseInDownloads(fileName)
        awaitReaderReady()
        return scenario
    }

    protected fun saveOneHighlightAsNewCopy(fixtureName: String): SavedCopy {
        val source = place(fixtureName)
        val copyName = SaveFixtures.annotatedNameOf(source.name)
        var destinationUri = ""
        launch(source).use {
            val journal = journal()
            val sourceUri = documentUri()
            makeOneHighlight(fixtureName)
            AnnotationJournalFile.awaitLineCount(journal, 1)

            tapSaveAndAwaitSheet()
            SaveSheet.chooseSaveAsNewCopy()
            SavePicker.createInDownloads(copyName)

            awaitSaveCompleted(journal)
            destinationUri = SaveDestinations.awaitRowForSource(sourceUri).destinationUri
        }
        return SavedCopy(source, File(SaveFixtures.downloads, copyName), destinationUri)
    }

    data class SavedCopy(val source: File, val copy: File, val destinationUri: String)

    protected fun reopen(file: File, body: () -> Unit) {
        ActivityScenario.launch<MainActivity>(intentFor(file)).use {
            awaitReaderReady()
            body()
        }
    }

    protected fun uiDevice() = TestEnvironment.uiDevice

    protected companion object {
        const val SELECTION_FRACTION = 0.3f

        const val DENSE_BODY_LEFT = 56f
        const val DENSE_BODY_WIDTH = 500f
        const val DENSE_BODY_TOP = 696f
        const val DENSE_BODY_LEADING = 12f

        const val WIDE_BODY_LEFT = 72f
        const val WIDE_BODY_WIDTH = 468f
        const val WIDE_BODY_LEADING = 15f
        const val NORMAL_BODY_TOP = 702f
        const val DECOY_BODY_TOP = 702f

        val DENSE_BODY_LINES = listOf(3, 6, 9, 12, 15)
        val WIDE_BODY_LINES = listOf(2, 4, 6, 8, 10)
    }
}
