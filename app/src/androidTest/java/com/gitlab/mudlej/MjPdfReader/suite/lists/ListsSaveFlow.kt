// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import java.io.File
import java.security.MessageDigest

object UnsavedDialog {

    const val ABSENCE_WINDOW_MS = 1_500L

    fun title(): String = string(R.string.unsaved_highlights)

    fun isShown(): Boolean = TestEnvironment.uiDevice.hasObject(By.text(title()))

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(title())), timeoutMs) != true) {
            throw ConditionTimeoutException(
                "No dialog titled \"${title()}\" appeared within ${timeoutMs}ms, so the unsaved highlights gate " +
                    "never ran"
            )
        }
    }

    fun awaitGone(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        if (TestEnvironment.uiDevice.wait(Until.gone(By.text(title())), timeoutMs) != true) {
            throw ConditionTimeoutException("The unsaved highlights dialog is still showing after ${timeoutMs}ms")
        }
    }

    fun tapSaveChanges() = tapText(R.string.save_highlights)

    fun tapDiscard() = tapText(R.string.discard)

    fun tapCancel() = tapText(R.string.cancel)

    private fun tapText(resId: Int) {
        val label = string(resId)
        onView(withText(label)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(label)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}

object DestinationChooser {

    const val ABSENCE_WINDOW_MS = 1_500L

    fun title(): String = string(R.string.save_highlights)

    fun updateExistingLabel(): String = string(R.string.update_existing_file)

    fun saveAsNewCopyLabel(): String = string(R.string.save_as_new_copy)

    fun isShown(): Boolean = TestEnvironment.uiDevice.hasObject(By.text(updateExistingLabel()))

    fun awaitShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(updateExistingLabel())), timeoutMs) != true) {
            throw ConditionTimeoutException(
                "The save destination chooser never appeared. AnnotationSaveController shows it whenever no " +
                    "destination is remembered, which on a clean install is every first save on a document."
            )
        }
    }

    fun labelsInScreenOrder(): List<String> {
        awaitShown()
        return listOf(updateExistingLabel(), saveAsNewCopyLabel())
            .mapNotNull { label ->
                TestEnvironment.uiDevice.findObject(By.text(label))?.let { label to it.visibleBounds.top }
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun chooseUpdateExisting() {
        awaitShown()
        onView(withText(updateExistingLabel())).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun assertNeverShown(windowMs: Long = ABSENCE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the save destination chooser never appears",
            windowMs = windowMs,
        ) { !isShown() }
    }

    fun assertNoSystemPickerWhile(windowMs: Long = ABSENCE_WINDOW_MS) {
        val picker = SafPickerRobot()
        Await.assertThroughout(
            condition = "no system document picker owns the foreground",
            windowMs = windowMs,
        ) { !picker.isShowing() }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}

object NoteDialog {

    fun awaitViewShown(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        if (TestEnvironment.uiDevice.wait(Until.hasObject(By.text(string(R.string.edit))), timeoutMs) != true) {
            throw ConditionTimeoutException(
                "The note view dialog never appeared, so its Edit button could not be pressed"
            )
        }
    }

    fun tapEdit() {
        awaitViewShown()
        onView(withText(string(R.string.edit))).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun replaceNoteText(text: String) {
        Dialogs.awaitEditFieldOnScreen()
        onView(isAssignableFrom(android.widget.EditText::class.java)).inRoot(isDialog())
            .perform(replaceText(text), closeSoftKeyboard())
        TestEnvironment.waitForIdleSync()
    }

    fun tapSave() {
        onView(withText(string(R.string.save))).inRoot(isDialog()).perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}

object FileDigest {

    fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val BUFFER_BYTES = 1 shl 16
}
