// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.ui.usernotes.UserNotesActivity
import com.gitlab.mudlej.MjPdfReader.ui.userhighlights.UserHighlightsActivity
import java.io.File

enum class ExportKind(val screen: Class<out Activity>, val recyclerId: Int, val titleRes: Int) {
    HIGHLIGHTS(UserHighlightsActivity::class.java, R.id.userHighlightsRecyclerView, R.string.user_highlights_title),
    NOTES(UserNotesActivity::class.java, R.id.userNotesRecyclerView, R.string.user_notes_title),
}

class ExportFlow(private val kind: ExportKind) {

    private val picker = SafPickerRobot()

    fun launch(document: File, documentName: String = document.name, password: String? = null): ActivityScenario<out Activity> {
        val intent = Intent(TestEnvironment.targetContext, kind.screen).apply {
            putExtra(PDF.filePathKey, android.net.Uri.fromFile(document).toString())
            putExtra(PDF.nameKey, documentName)
            password?.let { putExtra(PDF.passwordKey, it) }
        }
        val scenario = ActivityScenario.launch<Activity>(intent)
        Screens.awaitResumed(kind.screen, Await.LONG_TIMEOUT_MS)
        return scenario
    }

    fun awaitScanFinished(timeoutMs: Long = Await.VERY_LONG_TIMEOUT_MS) {
        Await.until("the ${kind.screen.simpleName} scan progress bar is gone", timeoutMs) {
            !scanProgressVisible() && !indeterminateProgressVisible()
        }
    }

    fun rowCount(): Int = TestEnvironment.onMainThread {
        recycler().adapter?.itemCount ?: 0
    }

    fun awaitRowCount(expected: Int, timeoutMs: Long = Await.LONG_TIMEOUT_MS): Int = Await.untilValue(
        "the ${kind.screen.simpleName} list holds $expected rows",
        timeoutMs,
        read = { runCatching { rowCount() }.getOrDefault(-1) },
    ) { it == expected }

    fun exportInto(directory: File, fileName: String, clearExisting: Boolean = true): File {
        if (clearExisting) {
            deleteAllVariants(directory, fileName)
        }
        startExport()
        picker.awaitShown()
        picker.navigateToDownloads()
        picker.typeFileNameAndSave(fileName)
        Snackbars.awaitText(string(R.string.export_done), Await.LONG_TIMEOUT_MS)
        val written = Await.untilValue(
            "an export named $fileName or a picker renamed variant of it appears in ${directory.absolutePath}",
            Await.LONG_TIMEOUT_MS,
            read = { variantsOf(directory, fileName).filter { it.length() > 0L } },
        ) { it.isNotEmpty() }.maxByOrNull { it.lastModified() }!!
        return written
    }

    fun exportExactlyInto(directory: File, fileName: String): File {
        val written = exportInto(directory, fileName)
        if (written.name != fileName) {
            throw AssertionError(
                "The create document picker saved this export as ${written.name} rather than $fileName, so the " +
                    "bytes read from $fileName would belong to an earlier run rather than to this one"
            )
        }
        return written
    }

    fun exportToStubbedDocument() {
        startExport()
        Snackbars.awaitText(string(R.string.export_done), Await.LONG_TIMEOUT_MS)
    }

    fun expectedFileName(documentTitle: String): String = "$documentTitle - ${string(kind.titleRes)}.txt"

    private fun scanProgressVisible(): Boolean = visibility(R.id.scanProgressBar) == View.VISIBLE

    private fun indeterminateProgressVisible(): Boolean = visibility(R.id.progressBar) == View.VISIBLE

    private fun visibility(viewId: Int): Int = TestEnvironment.onMainThread {
        activity().findViewById<View>(viewId)?.visibility ?: View.GONE
    }

    private fun recycler(): RecyclerView = activity().findViewById(kind.recyclerId)
        ?: throw ConditionTimeoutException("${kind.screen.simpleName} carries no list of annotations")

    private fun activity(): Activity = TestEnvironment.resumedActivityOfType(kind.screen)
        ?: throw ConditionTimeoutException(
            "No resumed ${kind.screen.simpleName}, the resumed screen is ${Screens.resumedName()}"
        )

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun startExport() {
        openActionBarOverflowOrOptionsMenu(TestEnvironment.targetContext)
        TestEnvironment.waitForIdleSync()
        onView(withText(string(R.string.export))).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    companion object {

        const val SUFFIX = ".txt"

        fun variantsOf(directory: File, fileName: String): List<File> {
            val stem = fileName.removeSuffix(SUFFIX)
            return directory.listFiles()?.filter { file ->
                file.name == fileName || (file.name.startsWith("$stem (") && file.name.endsWith(SUFFIX))
            }.orEmpty()
        }

        fun deleteAllVariants(directory: File, fileName: String, tolerateFailures: Boolean = true) {
            variantsOf(directory, fileName).forEach { file ->
                if (tolerateFailures) {
                    runCatching { Fixtures.deleteMediaStoreRowsUnder(file) }
                    runCatching { file.delete() }
                } else {
                    Fixtures.deleteMediaStoreRowsUnder(file)
                    file.delete()
                }
            }
        }
    }
}
