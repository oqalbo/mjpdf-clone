// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.AnnotationTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.PostSaveAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class RecoveryFlowTest : ResilienceTestCase() {

    private val selection = SelectionRobot()
    private val picker = SafPickerRobot()

    @Test
    @TestId("RES-13")
    fun theQueuedPostSaveActionOutlivesBackgrounding() {
        val file = fixture(ResilienceFixtures.NORMAL)
        rememberSaveDestination(file)
        AnnotationTestHooks.setSaveFaultMode(TestEnvironment.targetContext, AnnotationTestHooks.FAULT_SLOW_WRITE)
        val startedAt = System.currentTimeMillis()

        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        createHighlightOnCurrentPage()

        TestEnvironment.uiDevice.pressBack()
        awaitDialogTitled(R.string.unsaved_highlights)
        tapDialogButtonWithText(R.string.save_highlights)

        Await.until("the held save reaches the parked write", Await.LONG_TIMEOUT_MS) {
            AnnotationTestHooks.isSlowWriteBlocked()
        }
        val queued = ReaderProbe.viewModel().pendingPostSaveAction
        assertEquals(
            "choosing Save when leaving must queue the leave action for after the write",
            PostSaveAction.LEAVE_READER,
            queued,
        )

        backgroundReader()
        returnToReaderTask(file)

        assertEquals(
            "the queued action must still be pending after the reader was backgrounded and resumed",
            PostSaveAction.LEAVE_READER,
            ReaderProbe.viewModel().pendingPostSaveAction,
        )

        AnnotationTestHooks.releaseSlowWrite()

        Await.until("no reader remains in the foreground once the queued leave action runs", Await.LONG_TIMEOUT_MS) {
            !Screens.isInStage(MainActivity::class.java, androidx.test.runner.lifecycle.Stage.RESUMED) &&
                !Screens.isInStage(MainActivity::class.java, androidx.test.runner.lifecycle.Stage.STARTED)
        }
        Await.until("the destination document is rewritten by the completed save", Await.LONG_TIMEOUT_MS) {
            file.lastModified() >= startedAt
        }
        assertTrue(
            "the saved document must not be empty",
            file.length() > 0,
        )
    }

    @Test
    @TestId("RES-14")
    fun backingOutOfTheLocatePickerExitsTheReader() {
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)
        val file = fixture(ResilienceFixtures.NORMAL)
        val uri = Uri.fromFile(file)

        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val hash = reader.awaitFileHashResolved()
        Records.awaitRowExists(hash, Await.LONG_TIMEOUT_MS)
        ReaderProbe.finishAndRemoveReaderTasks()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        deleteFixtureCopy(file)

        ReaderProbe.startReader(ReaderRobot.viewIntent(uri))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        awaitDialogTitled(R.string.home_relocate_title)

        val readerActivity = ReaderProbe.requireResumedReader()
        tapDialogButtonWithText(R.string.home_relocate_action)
        picker.awaitShown(Await.LONG_TIMEOUT_MS)

        val pending = ReaderProbe.viewModel(readerActivity).pendingRelocate
        assertNotNull(
            "the locate picker must be running against a pending relocate, which is the state that has to " +
                "survive anything the system does behind the picker",
            pending,
        )
        assertEquals(
            "the pending relocate must name the record whose file went missing",
            hash,
            pending?.hash,
        )

        TestEnvironment.uiDevice.pressBack()

        Await.until("no reader stays in the foreground once the picker is dismissed", Await.LONG_TIMEOUT_MS) {
            ReaderProbe.resumedReaderOrNull() == null
        }
        Await.until("the foreground leaves the app entirely", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.currentPackageName != TestEnvironment.targetPackage
        }
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun rememberSaveDestination(file: File) {
        val uri = Uri.fromFile(file)
        Records.database.pdfAnnotationSaveDestinationDao().upsert(
            PdfAnnotationSaveDestination(
                sourceKey = SourceKey.of(uri),
                destinationUri = uri.toString(),
                lastSavedHash = null,
                lastSavedAt = LocalDateTime.now(),
            )
        )
    }

    private fun createHighlightOnCurrentPage() {
        selection.longPressInsidePage(
            BodyTextTargets.horizontalFraction(0),
            BodyTextTargets.normalLine(HIGHLIGHTED_LINE),
        )
        ReaderProbe.awaitVisibility(R.id.textSelectionActionCard, View.VISIBLE)
        ReaderProbe.tapFirstHighlightSwatch()
        reader.awaitState("the reader reports an unsaved annotation", Await.LONG_TIMEOUT_MS) {
            it.hasUnsavedAnnotations == true
        }
    }

    private fun awaitDialogTitled(titleRes: Int) {
        val title = TestEnvironment.targetContext.getString(titleRes)
        Await.until("a dialog titled \"$title\" appears", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(By.text(title))
        }
    }

    private fun tapDialogButtonWithText(textRes: Int) {
        val label = TestEnvironment.targetContext.getString(textRes)
        val button = TestEnvironment.uiDevice.findObject(By.text(label))
            ?: TestEnvironment.uiDevice.findObject(By.text(label.uppercase()))
        assertNotNull("the dialog must offer a \"$label\" action", button)
        button!!.click()
        TestEnvironment.waitForIdleSync()
    }

    private companion object {
        const val HIGHLIGHTED_LINE = 3
    }
}
