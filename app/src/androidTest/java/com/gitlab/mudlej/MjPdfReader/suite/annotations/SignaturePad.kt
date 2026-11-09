// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.RawInput
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File

object SignaturePad {

    const val INK_BLACK = 0xFF1A1A1A.toInt()
    const val INK_BLUE = 0xFF1E4FC2.toInt()

    const val STROKE_START_X_FRACTION = 0.2f
    const val STROKE_START_Y_FRACTION = 0.7f
    const val STROKE_END_X_FRACTION = 0.8f
    const val STROKE_END_Y_FRACTION = 0.3f

    fun storedSignatureFile(): File =
        File(File(TestEnvironment.targetContext.filesDir, "signature"), "signature.json")

    fun openFromReaderActions() {
        tapToolbarActions()
        AnnotationDialogs.awaitTextShown(AnnotationDialogs.string(R.string.add_signature))
        onView(withContentDescription(AnnotationDialogs.string(R.string.add_signature)))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(ViewActions.scrollTo(), ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitDialog(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        AnnotationDialogs.awaitTitle(AnnotationDialogs.string(R.string.signature_dialog_title), timeoutMs)
    }

    fun isDialogShown(): Boolean =
        AnnotationDialogs.isTitleShown(AnnotationDialogs.string(R.string.signature_dialog_title))

    fun assertDialogNeverShown() {
        AnnotationDialogs.assertTitleNeverShown(AnnotationDialogs.string(R.string.signature_dialog_title))
    }

    fun padBoundsOnScreen(): Rect = AnnotationDialogs.capture(padMatcher()) { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException("The signature pad has no size on screen, so it cannot be drawn on")
        }
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun drawOneStroke() {
        val bounds = padBoundsOnScreen()
        RawInput.dragAlongLine(
            PointF(
                bounds.left + bounds.width() * STROKE_START_X_FRACTION,
                bounds.top + bounds.height() * STROKE_START_Y_FRACTION,
            ),
            PointF(
                bounds.left + bounds.width() * STROKE_END_X_FRACTION,
                bounds.top + bounds.height() * STROKE_END_Y_FRACTION,
            ),
        )
        TestEnvironment.waitForIdleSync()
    }

    fun pickInk(colorDescriptionRes: Int) {
        onView(withContentDescription(AnnotationDialogs.string(colorDescriptionRes)))
            .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .perform(ViewActions.click())
        TestEnvironment.waitForIdleSync()
    }

    fun pressPlace() = AnnotationDialogs.pressPositive()

    fun pressClear() = AnnotationDialogs.pressNeutral()

    fun isPlaceEnabled(): Boolean = AnnotationDialogs.isPositiveEnabled()

    fun awaitPlaceEnabled(enabled: Boolean) = AnnotationDialogs.awaitPositiveEnabled(enabled)

    private fun padMatcher() = org.hamcrest.Matchers.allOf(withId(R.id.signatureView), isDisplayed())

    private fun tapToolbarActions() {
        val point = TestEnvironment.onMainThread {
            val activity = TestEnvironment.resumedActivityOfType(MainActivity::class.java)
                ?: throw ConditionTimeoutException("No reader is resumed to open the actions dialog from")
            val view: View = activity.window.decorView.findViewById(R.id.readerActionsOption)
                ?: throw ConditionTimeoutException("The reader toolbar carries no actions item")
            if (!view.isShown) {
                throw ConditionTimeoutException("The reader toolbar actions item is not on screen")
            }
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            PointF(location[0] + view.width / 2f, location[1] + view.height / 2f)
        }
        TestEnvironment.uiDevice.click(point.x.toInt(), point.y.toInt())
        TestEnvironment.waitForIdleSync()
    }
}
