// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.pdf.SweptHighlight
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationGeometry
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.annotations.HighlightGroup
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.LocaleResetRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.File

abstract class ListsTestCase {

    protected val suite = SuiteRules.standard()
    protected val exports = ExportCleanupRule()
    protected val localeReset = LocaleResetRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(suite).around(localeReset).around(exports)

    protected val reader = ReaderRobot()
    protected val highlights = HighlightsListRobot()
    protected val notes = NotesListRobot()

    @After
    fun releaseAnnotationHooks() {
        runCatching { AnnotationTestHooks.reset() }
    }

    protected fun fixture(name: String): File = ListsFixtures.place(name, suite.testDirectory.directory())

    protected fun fixtureNamed(source: String, newName: String): File =
        ListsFixtures.placeAs(source, suite.testDirectory.directory(), newName)

    protected fun intentFor(file: File, incognito: Boolean = false): Intent =
        ReaderRobot.viewIntent(file).apply {
            if (incognito) putExtra(PDF.incognitoKey, true)
        }

    protected fun openReader(
        name: String = ListsFixtures.RICH,
        incognito: Boolean = false,
        password: String? = null,
        body: (ActivityScenario<MainActivity>, File) -> Unit,
    ) {
        Fixtures.ensureAllFilesAccess()
        val file = fixture(name)
        openReaderOn(file, incognito, password, body)
    }

    protected fun openReaderOn(
        file: File,
        incognito: Boolean = false,
        password: String? = null,
        body: (ActivityScenario<MainActivity>, File) -> Unit,
    ) {
        ActivityScenario.launch<MainActivity>(intentFor(file, incognito)).use { scenario ->
            if (password != null) enterPassword(password)
            awaitReaderReady()
            body(scenario, file)
        }
    }

    protected fun awaitReaderReady(): ReaderState =
        reader.awaitReadyAtFullQuality(Await.LONG_TIMEOUT_MS)

    protected fun enterPassword(password: String) {
        Dialogs.awaitViewOnScreen(R.id.passwordInput, Await.LONG_TIMEOUT_MS)
        onView(withId(R.id.passwordInput)).inRoot(isDialog())
            .perform(replaceText(password), closeSoftKeyboard())
        onView(withText(TestEnvironment.targetContext.getString(R.string.ok))).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    protected fun chooseReaderAction(labelRes: Int) {
        val dialog = ActionsDialogRobot()
        dialog.open()
        val label = readerString(labelRes)
        dialog.awaitTile(label)
        dialog.tap(label)
    }

    protected fun openHighlightsList(): HighlightsListRobot {
        chooseReaderAction(R.string.user_highlights_title)
        highlights.awaitShown()
        return highlights
    }

    protected fun openNotesList(): NotesListRobot {
        chooseReaderAction(R.string.user_notes_title)
        notes.awaitShown()
        return notes
    }

    protected fun openHighlightsListWithoutIdleWait(): HighlightsListRobot {
        val dialog = ActionsDialogRobot()
        dialog.open()
        val label = readerString(R.string.user_highlights_title)
        dialog.awaitTile(label)
        val bounds = dialog.tileBounds(label)
        TestEnvironment.uiDevice.click(bounds.centerX(), bounds.centerY())
        return highlights
    }

    private fun readerString(resId: Int): String = TestEnvironment.onMainThread {
        TestEnvironment.resumedActivity()?.getString(resId)
            ?: TestEnvironment.targetContext.getString(resId)
    }

    protected fun openHighlightsAndScan(): HighlightsListRobot {
        openHighlightsList().awaitScanFinished()
        return highlights
    }

    protected fun openNotesAndScan(): NotesListRobot {
        openNotesList().awaitScanFinished()
        return notes
    }

    protected fun createUnsavedHighlight(pageIndex: Int = 0): HighlightGroup {
        val before = AnnotationDump.highlights(pageIndex).map { it.group }.toSet()
        val selection = SelectionRobot()
        var lastFailure: Throwable? = null
        FREE_TEXT_FRACTIONS.forEach { fraction ->
            val attempt = runCatching {
                selection.longPressInsidePage(SELECTION_HORIZONTAL_FRACTION, fraction, Await.SHORT_TIMEOUT_MS)
                AnnotationCard.awaitShown()
            }
            if (attempt.isSuccess) {
                AnnotationCard.tapSwatch(0)
                reader.awaitState("the reader reports unsaved annotations") { it.hasUnsavedAnnotations == true }
                val added = AnnotationDump.highlights(pageIndex).firstOrNull { it.group !in before }
                if (added != null) return added
                throw AssertionError(
                    "A swatch was tapped but page $pageIndex gained no highlight group. The live dump holds " +
                        AnnotationDump.highlights(pageIndex).map { it.group }
                )
            }
            lastFailure = attempt.exceptionOrNull()
        }
        throw AssertionError(
            "No long press on page $pageIndex produced a fresh selection at any of $FREE_TEXT_FRACTIONS. " +
                "A press landing on text the document already highlights turns into that highlight's card " +
                "instead of a new selection. Last failure was $lastFailure"
        )
    }

    protected fun tapFocusedHighlight(target: SweptHighlight) {
        val selection = SelectionRobot()
        val state = reader.readState()
        require(state.page == target.pageIndex) {
            "the reader is on page ${state.page}, not the target highlight page ${target.pageIndex}"
        }
        val bounds = requireNotNull(target.bounds) { "the list row carries no highlight bounds" }
        val centreInView = AnnotationGeometry.frameOf(state).toViewPoint(bounds.centerX(), bounds.centerY())
        AnnotationGeometry.requireOnScreen(state, centreInView, "the focused highlight")
        val centre = selection.toScreen(centreInView)
        TestEnvironment.uiDevice.click(centre.x.toInt(), centre.y.toInt())
        TestEnvironment.waitForIdleSync()
        AnnotationCard.awaitShown()
    }

    protected fun journalOfOpenDocument(): File = AnnotationJournalFile.fileOfOpenDocument(reader)

    protected fun jumpReaderTo(pageIndex: Int) {
        TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw AssertionError("No reader is resumed while jumping to page index $pageIndex")
            activity.findViewById<com.github.barteksc.pdfviewer.PDFView>(R.id.pdfView).jumpTo(pageIndex, false)
        }
        reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    protected fun awaitBackInReader(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Screens.awaitTopIs(MainActivity::class.java, timeoutMs)
    }

    protected fun awaitReaderSettledOn(pageIndex: Int): ReaderState {
        awaitBackInReader()
        reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)
        return reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    protected companion object {
        const val SELECTION_HORIZONTAL_FRACTION = 0.3f
        val FREE_TEXT_FRACTIONS = listOf(0.35f, 0.45f, 0.55f, 0.65f, 0.25f)
    }
}
