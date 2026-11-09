// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.graphics.PointF
import android.graphics.Rect
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.SignatureView

object SignatureFlow {

    fun seedAsPrimaryToolbarAction() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.ADD_SIGNATURE.id)
    }

    fun drawAndPlace(reader: ArtifactReader) {
        onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
        TestEnvironment.waitForIdleSync()
        awaitDialog()

        drawStroke()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
        Await.until("the signature dialog closes", Await.LONG_TIMEOUT_MS) { !isDialogShowing() }

        commitPlacement(reader)
    }

    private fun commitPlacement(reader: ArtifactReader) {
        val selection = SelectionRobot()
        val pageRect = reader.state().pageRect
            ?: throw ConditionTimeoutException("The reader reports no page rectangle to place a stamp on")
        val point = selection.toScreen(
            PointF(
                pageRect.left + pageRect.width() * PLACEMENT_X,
                pageRect.top + pageRect.height() * PLACEMENT_Y,
            )
        )
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
        Await.until("the placed signature is recorded as an unsaved edit", Await.LONG_TIMEOUT_MS) {
            reader.hasUnsavedEdits()
        }
    }

    private fun drawStroke() {
        val bounds = Await.untilNotNull("the signature dialog offers a drawing surface") { signatureBounds() }
        RawInput.dragWithoutFling(
            from = PointF(bounds.left + bounds.width() * STROKE_START, bounds.exactCenterY()),
            to = PointF(bounds.left + bounds.width() * STROKE_END, bounds.exactCenterY()),
        )
        TestEnvironment.waitForIdleSync()
    }

    private fun signatureBounds(): Rect? = runCatching {
        if (!isDialogShowing()) return@runCatching null
        val root = Dialogs.dialogRoot()
        TestEnvironment.onMainThread {
            Dialogs.firstDescendant(root, SignatureView::class.java)?.let { view ->
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            }
        }
    }.getOrNull()

    private fun isDialogShowing(): Boolean =
        TestEnvironment.uiDevice.hasObject(By.res(ANDROID, POSITIVE_BUTTON))

    private fun awaitDialog() {
        Await.until("the signature dialog opens", Await.LONG_TIMEOUT_MS) { isDialogShowing() }
    }

    private const val PLACEMENT_X = 0.5f
    private const val PLACEMENT_Y = 0.7f
    private const val STROKE_START = 0.2f
    private const val STROKE_END = 0.8f
    private const val ANDROID = "android"
    private const val POSITIVE_BUTTON = "button1"
}
