// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationJournal
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.hamcrest.Matcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReaderRotationTest : ResilienceTestCase() {

    private val selection = SelectionRobot()

    @Test
    @TestId("RES-01")
    fun rotatingTheReaderKeepsTheSameActivityInstance() {
        val since = ResilienceLogcat.since()
        openReader { file ->
            jumpToPage(ROTATED_PAGE)
            val identityBefore = ReaderProbe.resumedReaderIdentity()
            val handlesBefore = FileDescriptors.countFor(file)
            assertTrue(
                "the reader must hold at least one descriptor on ${file.absolutePath} before the rotation, " +
                    "otherwise the handle assertions below measure nothing",
                handlesBefore > 0,
            )

            rotateReaderToLandscape()
            assertEquals(
                "MainActivity declares configChanges for orientation, so a landscape turn must reconfigure the " +
                    "same instance rather than recreate it",
                identityBefore,
                ReaderProbe.resumedReaderIdentity(),
            )
            assertEquals("the page must survive the landscape turn", ROTATED_PAGE as Int?, state().page)
            assertEquals(
                "rotating must not open a second document handle, ${FileDescriptors.describe(file)}",
                handlesBefore,
                FileDescriptors.countFor(file),
            )

            rotateReaderToPortrait()
            assertEquals(
                "returning to portrait must still be the same instance",
                identityBefore,
                ReaderProbe.resumedReaderIdentity(),
            )
            assertEquals("the page must survive the return to portrait", ROTATED_PAGE as Int?, state().page)
            assertEquals(
                "returning to portrait must not leak a document handle, ${FileDescriptors.describe(file)}",
                handlesBefore,
                FileDescriptors.countFor(file),
            )
        }
        ResilienceLogcat.assertNoMainThreadPdfiumWork(since)
    }

    @Test
    @TestId("RES-03")
    fun rotatingWithThePasswordDialogOpenKeepsIt() {
        val file = fixture(ResilienceFixtures.PROTECTED)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitPasswordDialog()
            onView(withId(R.id.passwordInput)).perform(
                ViewActions.replaceText(PASSWORD_PREFIX),
                ViewActions.closeSoftKeyboard(),
            )

            TestEnvironment.uiDevice.setOrientationLeft()
            TestEnvironment.uiDevice.waitForIdle()

            Await.until("the password dialog is still up after the rotation", Await.DEFAULT_TIMEOUT_MS) {
                passwordFieldCount() >= 1
            }
            assertEquals(
                "rotating must leave exactly one password dialog, not a second stacked copy",
                1,
                passwordFieldCount(),
            )
            assertEquals(
                "the characters typed before the rotation must still be in the field, which is what proves the " +
                    "dialog survived rather than being rebuilt empty",
                PASSWORD_PREFIX,
                passwordFieldText(),
            )

            onView(withId(R.id.passwordInput)).perform(
                ViewActions.replaceText(ResilienceFixtures.PROTECTED_PASSWORD),
                ViewActions.closeSoftKeyboard(),
            )
            onView(withId(android.R.id.button1)).perform(ViewActions.click())

            reader.awaitLoaded()
            val unlocked = reader.awaitState("the unlocked document reports its page count") {
                (it.pageCount ?: 0) > 0
            }
            assertEquals(
                "the unlocked document must report the protected fixture's page count",
                ResilienceFixtures.PROTECTED_PAGE_COUNT as Int?,
                unlocked.pageCount,
            )
        }
    }

    @Test
    @TestId("RES-04")
    @TestDuration(DurationCategory.NORMAL)
    fun rotatingWithUnsavedHighlightsKeepsTheDirtyState() {
        openReader {
            val documentUri = ReaderProbe.documentUriOfReader()
            val journal = ReaderProbe.journalFileFor(documentUri)
            val journalReader = AnnotationJournal(TestEnvironment.targetContext)
            createHighlightOnCurrentPage()
            val editsBefore = journalReader.readAll(documentUri)
            assertEquals("the created highlight must have one completed journal record", 1, editsBefore.size)
            assertTrue("the journal record must add the highlight", editsBefore.single() is AnnotationEdit.Add)
            val journalBytesBefore = journal.readBytes()

            ReaderProbe.awaitVisibility(R.id.saveAnnotationsFab, View.VISIBLE)
            ReaderProbe.awaitVisibility(R.id.discardAnnotationsFab, View.VISIBLE)

            rotateReaderToLandscape()
            settledFullQuality()
            rotateReaderToPortrait()
            settledFullQuality()

            assertEquals(
                "rotation must preserve the journal record count",
                editsBefore.size,
                journalReader.readAll(documentUri).size,
            )
            assertArrayEquals(
                "the annotation journal must be byte for byte what it was before the rotations",
                journalBytesBefore,
                journal.readBytes(),
            )
            assertEquals(
                "the save annotations action must still be offered after rotating",
                View.VISIBLE,
                ReaderProbe.visibility(R.id.saveAnnotationsFab),
            )
            assertEquals(
                "the discard annotations action must still be offered after rotating",
                View.VISIBLE,
                ReaderProbe.visibility(R.id.discardAnnotationsFab),
            )
            assertTrue(
                "the save annotations action must be enabled, not merely visible",
                ReaderProbe.onReaderView(R.id.saveAnnotationsFab) { it.isEnabled },
            )
            assertTrue(
                "the reader must still report unsaved annotations after the rotations",
                state().hasUnsavedAnnotations == true,
            )
            assertNoRestoreUnsavedHighlightsDialog()
        }
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

    private fun awaitPasswordDialog() {
        Await.until("the password prompt for the protected fixture appears", Await.LONG_TIMEOUT_MS) {
            passwordFieldCount() >= 1
        }
    }

    private fun passwordFieldCount(): Int =
        TestEnvironment.uiDevice.findObjects(By.res(TestEnvironment.targetPackage, PASSWORD_FIELD_NAME)).size

    private fun passwordFieldText(): String {
        var text = ""
        onView(withId(R.id.passwordInput)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TextView::class.java)

            override fun getDescription(): String =
                "read the password field text from the widget rather than from the accessibility tree, " +
                    "because a field marked isPassword reports bullets to UIAutomator whatever it holds"

            override fun perform(uiController: UiController, view: View) {
                text = (view as TextView).text.toString()
            }
        })
        return text
    }

    private fun assertNoRestoreUnsavedHighlightsDialog() {
        val title = TestEnvironment.targetContext.getString(R.string.restore_unsaved_highlights_title)
        Await.assertThroughout(
            condition = "no restore unsaved highlights dialog appears, because the session still owns the edit",
            windowMs = SETTLE_WINDOW_MS,
        ) { !TestEnvironment.uiDevice.hasObject(By.text(title)) }
    }

    private companion object {
        const val ROTATED_PAGE = 4
        const val HIGHLIGHTED_LINE = 3
        const val PASSWORD_PREFIX = "mjp"
        const val PASSWORD_FIELD_NAME = "passwordInput"
    }
}
