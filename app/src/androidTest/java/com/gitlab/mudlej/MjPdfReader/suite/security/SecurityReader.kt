// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.actions.ActionsDialogRobot
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.state.ReaderState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity

object SecurityReader {
    fun viewIntent(uri: Uri, mimeType: String? = PDF.FILE_TYPE): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            if (mimeType == null) data = uri else setDataAndType(uri, mimeType)
            setClass(TestEnvironment.targetContext, MainActivity::class.java)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun fixtureViewIntent(name: String): Intent = ReaderRobot.viewIntent(SecurityFixtures.file(name))

    fun startFromOutside(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        TestEnvironment.targetContext.startActivity(intent)
    }

    fun activityOrNull(): MainActivity? =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity

    fun activity(): MainActivity = activityOrNull()
        ?: throw ConditionTimeoutException("No resumed reader activity")

    fun jumpToPage(reader: ReaderRobot, pageIndex: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ReaderState {
        TestEnvironment.onMainThread {
            val pdfView = activity().findViewById<PDFView>(R.id.pdfView)
                ?: throw ConditionTimeoutException("The reader has no pdfView while jumping to page index $pageIndex")
            pdfView.jumpTo(pageIndex, false)
        }
        reader.awaitPageIndex(pageIndex, timeoutMs)
        return reader.awaitMotionSettled(timeoutMs)
    }

    fun tapRect(
        reader: ReaderRobot,
        rect: RectPoints,
        pageWidthPoints: Float = SecurityFixtures.PAGE_WIDTH_POINTS,
        pageHeightPoints: Float = SecurityFixtures.PAGE_HEIGHT_POINTS,
    ) {
        reader.tapAtPageFraction(
            rect.centreXFraction(pageWidthPoints),
            rect.centreYFraction(pageHeightPoints),
        )
    }

    fun openAction(@StringRes labelRes: Int) {
        val dialog = ActionsDialogRobot()
        dialog.open()
        dialog.awaitTile(string(labelRes))
        dialog.tap(string(labelRes))
    }

    fun actionTileLabels(): List<String> {
        val dialog = ActionsDialogRobot()
        dialog.open()
        val labels = dialog.tileLabels()
        dialog.dismiss()
        return labels
    }

    fun typeIntoDialog(value: String) {
        val input = TestEnvironment.uiDevice.wait(
            Until.findObject(By.clazz(EditText::class.java)),
            Await.DEFAULT_TIMEOUT_MS,
        ) ?: throw AssertionError("The form field dialog raised no editable input")
        input.text = value
        TestEnvironment.uiDevice.waitForIdle()
    }

    fun documentUri(reader: ReaderRobot): String? = reader.readStateOrNull()?.documentUri

    fun awaitForegroundPackage(expected: String, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.untilValue(
            "the foreground package is $expected",
            timeoutMs,
            read = { TestEnvironment.uiDevice.currentPackageName },
        ) { it == expected }
    }

    fun holdForegroundPackage(expected: String, windowMs: Long = QUIET_WINDOW_MS) {
        Await.assertThroughout(
            condition = "the foreground package stays $expected",
            windowMs = windowMs,
        ) { TestEnvironment.uiDevice.currentPackageName == expected }
    }

    fun string(@StringRes resId: Int): String = TestEnvironment.targetContext.getString(resId)

    const val QUIET_WINDOW_MS = 1_500L
    const val SETTLE_WINDOW_MS = 3_000L
    const val INERT_WINDOW_MS = 5_000L
}
