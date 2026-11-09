// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsHighlights
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot

object SecuritySaveFaults {
    const val DESTINATION_PREFIX = "mjpdf-security-destination"
    const val DESTINATION_NAME = "$DESTINATION_PREFIX.pdf"

    fun createHighlightAndSaveAsNewCopy(reader: ReaderRobot) {
        ActionsHighlights.createOne(reader, SelectionRobot())
        saveAsNewCopy()
    }

    private fun saveAsNewCopy() {
        tapSaveFab()

        IntakeDialogs.awaitShowing(string(R.string.save_highlights), Await.LONG_TIMEOUT_MS)
        IntakeDialogs.tap(string(R.string.save_as_new_copy))

        val picker = SafPickerRobot()
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        picker.navigateToDownloads()
        picker.typeFileNameAndSave(DESTINATION_NAME)
        picker.awaitPickerDismissed(Await.LONG_TIMEOUT_MS)
    }

    private fun tapSaveFab() {
        val clicked = TestEnvironment.onMainThread {
            val activity = SecurityReader.activityOrNull()
                ?: throw ConditionTimeoutException("No reader is resumed while saving")
            val fab: View = activity.findViewById(R.id.saveAnnotationsFab)
                ?: throw ConditionTimeoutException("The reader carries no save annotations button")
            if (!fab.isShown) {
                throw ConditionTimeoutException(
                    "The save annotations button is hidden, so there is no unsaved edit to save"
                )
            }
            fab.performClick()
        }
        if (!clicked) throw ConditionTimeoutException("The visible save annotations button refused its click")
        TestEnvironment.waitForIdleSync()
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)
}
