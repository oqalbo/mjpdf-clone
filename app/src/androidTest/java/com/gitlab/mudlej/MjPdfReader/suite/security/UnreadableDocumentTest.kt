// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.view.View
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfiumCore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class UnreadableDocumentTest {
    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("SEC-17")
    fun anUnsupportedSecurityHandlerReportsAnErrorInsteadOfAskingForAPassword() {
        val fixture = SecurityFixtures.file(SecurityFixtures.UNSUPPORTED_CRYPT)
        val descriptor = ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
        val core = PdfiumCore(TestEnvironment.targetContext)
        val attempt = runCatching { core.newDocument(descriptor) }
        try {
            assertTrue("the unsupported handler is rejected by the document constructor", attempt.isFailure)
            assertFalse("a failed document constructor closes its owned descriptor immediately", descriptor.fileDescriptor.valid())
        } finally {
            attempt.getOrNull()?.let(core::closeDocument)
            descriptor.close()
        }

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitShown()
            val state = reader.awaitState(
                "the document load reports failed",
                Await.LONG_TIMEOUT_MS,
            ) { it.loadState == DocumentLoadState.FAILED }
            ReaderProbe.awaitVisibility(R.id.progressBar, View.GONE)
            ReaderProbe.assertVisibilityHolds(R.id.progressBar, View.GONE)
            assertEquals(DocumentLoadState.FAILED, state.loadState)

            Snackbars.awaitText(string(R.string.file_opening_error))
            IntakeDialogs.holdAbsent(string(R.string.protected_pdf))
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    @Test
    @TestId("SEC-18")
    fun anExecutableDressedAsADocumentIsRefusedAndNeverRun() {
        val markerFile = File(SecurityFixtures.SHELL_MARKER_FILE)
        markerFile.delete()
        val childrenBefore = ProcessTree.childrenOfAppProcess()

        for (name in VARIANTS) {
            val fixture = stagedExecutable(name)

            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                reader.awaitState(
                    "$name reports a failed load",
                    Await.LONG_TIMEOUT_MS,
                ) { it.loadState == DocumentLoadState.FAILED }
                Snackbars.awaitText(string(R.string.file_opening_error))
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            FileDescriptors.awaitNoneFor(fixture)
        }

        assertFalse(
            "the shell script's marker file must not exist, so nothing executed it",
            markerFile.exists(),
        )
        val runningMarker = ProcessTree.commandLinesContaining(SecurityFixtures.SHELL_MARKER)
        assertTrue(
            "no process may carry the shell script's marker string, ps reported $runningMarker",
            runningMarker.isEmpty(),
        )
        val childrenAfter = ProcessTree.childrenOfAppProcess()
        assertEquals(
            "opening three executables must not fork a child from the app process. Before: $childrenBefore. " +
                "After: $childrenAfter",
            childrenBefore.size,
            childrenAfter.size,
        )
    }

    private fun stagedExecutable(name: String): File {
        val source = SecurityFixtures.file(name)
        val directory = File(TestEnvironment.targetContext.cacheDir, STAGING_DIRECTORY).apply { mkdirs() }
        val target = File(directory, name)
        target.writeBytes(source.readBytes())
        runCatching { target.setExecutable(true, false) }
        return target
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val STAGING_DIRECTORY = "mjpdf-security-executables"

        val VARIANTS = listOf(
            SecurityFixtures.NOT_A_PDF,
            SecurityFixtures.NOT_A_PDF_ELF,
            SecurityFixtures.NOT_A_PDF_ZIP,
        )
    }
}
