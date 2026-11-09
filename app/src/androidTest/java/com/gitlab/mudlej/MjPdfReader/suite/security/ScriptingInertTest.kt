// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.annotations.FormFieldSnapshot
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.ProducedPdf
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.privacy.PrivacyStores
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ScriptingInertTest {
    private val suite = SuiteRules.standard()
    private val server = SecurityServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(server)

    private val reader = ReaderRobot()

    @Test
    @TestId("SEC-19")
    fun documentLevelJavaScriptDoesNotExecuteOnOpen() {
        val marker = File(SecurityFixtures.OPENACTION_MARKER_FILE)
        marker.delete()
        val traffic = NetworkWatch.sample()
        server.clearRequestLog()
        val fixture = SecurityStaging.stageWithPort(
            SecurityFixtures.OPENACTION_JS,
            server.port,
            suite.testDirectory.directory(),
        )

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()

            server.holdRequestLogEmpty("opening a document carrying an /OpenAction JavaScript entry")
            SecurityReader.holdForegroundPackage(TestEnvironment.targetPackage)

            assertTrue(
                "the page must render normally, so the document is usable and the script is simply inert",
                reader.readState().isFullyRendered,
            )
        }

        NetworkWatch.assertUnchangedSince(traffic, "opening a document carrying document level JavaScript")
        assertFalse(
            "the marker the embedded script would write must not exist on shared storage",
            marker.exists(),
        )
        assertTrue(
            "no marker file may appear anywhere under the app's own data directory either",
            FileTree.snapshot(FileTree.dataDirectory).entries.keys.none {
                it.contains(MARKER_NAME_FRAGMENT)
            },
        )
    }

    @Test
    @TestId("SEC-20")
    @TestDuration(DurationCategory.SHORT)
    fun theShippedPdfiumBinaryContainsNoJavaScriptEngine() {
        val nativeDirectory = File(TestEnvironment.targetContext.applicationInfo.nativeLibraryDir)
        val library = File(nativeDirectory, PDFIUM_LIBRARY)
        assertTrue(
            "the shipped pdfium binary must exist at ${library.absolutePath}, otherwise an absent file would " +
                "pass this test by accident. The native library directory holds " +
                "${nativeDirectory.list()?.toList()}",
            library.isFile && library.length() > 0,
        )

        val bytes = library.readBytes()
        val present = ENGINE_FRAGMENTS.filter { fragment ->
            containsAscii(bytes, fragment.toByteArray(Charsets.US_ASCII))
        }
        assertEquals(
            "the pdfium build sets pdf_enable_v8 false and pdf_enable_xfa false, which is what makes SEC-19 " +
                "hold structurally. These engine symbols were found in the shipped binary: $present",
            emptyList<String>(),
            present,
        )
    }

    @Test
    @TestId("SEC-21")
    fun fieldLevelJavaScriptDoesNotExecuteWhenAFormFieldIsEdited() {
        val traffic = NetworkWatch.sample()
        server.clearRequestLog()
        val fixture = SecurityStaging.stageWithPort(
            SecurityFixtures.FIELD_JS,
            server.port,
            suite.testDirectory.directory(),
        )
        val target = formFieldTarget(fixture, FIELD_NAME)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()
            val documentUri = reader.readState().documentUri
                ?: throw AssertionError("The reader reports no document URI, so the journal cannot be located")
            val fieldBefore = AnnotationDump.awaitFormFieldNamed(0, FIELD_NAME) { true }
            assertTrue("$FIELD_NAME must be an editable text field", fieldBefore.isTextField && !fieldBefore.readOnly)
            assertEquals("$FIELD_NAME must start empty", "", fieldBefore.value)
            assertFalse(
                "$FIELD_NAME must start without an existing unsaved edit",
                reader.readState().hasUnsavedAnnotations == true,
            )
            val journal = journalFor(documentUri)
            val linesBefore = journalLines(journal)

            openFormFieldDialog(target)
            SecurityReader.typeIntoDialog(TYPED_VALUE)
            IntakeDialogs.tap(string(R.string.ok))

            val edited = AnnotationDump.awaitFormFieldNamed(0, FIELD_NAME, Await.LONG_TIMEOUT_MS) {
                it.value == TYPED_VALUE
            }
            assertEquals("the edited field must remain $FIELD_NAME", fieldBefore.index, edited.index)
            reader.awaitState("editing $FIELD_NAME marks the document dirty") {
                it.hasUnsavedAnnotations == true
            }

            val lines = Await.untilValue(
                "the journal records the field edit",
                Await.LONG_TIMEOUT_MS,
                read = { journalLines(journal) },
            ) { it.size == linesBefore.size + 1 }
            val line = lines.last()

            assertTrue(
                "the journal must record one SetFieldText carrying exactly what was typed, it read $line",
                line.contains(OP_SET_FIELD_TEXT) && line.contains("\"$TYPED_VALUE\""),
            )
            assertFalse(
                "no format action may rewrite the value the reader stored, the journal read $line",
                line.contains(SecurityFixtures.FIELD_JS_TAMPER_VALUE),
            )

            server.holdRequestLogEmpty("confirming a form field carrying /AA JavaScript actions")
        }

        NetworkWatch.assertUnchangedSince(traffic, "editing a form field carrying JavaScript actions")
    }

    @Test
    @TestId("SEC-22")
    fun aReadOnlyOrHiddenFormFieldCannotBeEdited() {
        val fixture = SecurityFixtures.file(SecurityFixtures.FIELD_JS_READONLY)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
            reader.awaitReady()
            reader.awaitFullQualityRender()
            val documentUri = reader.readState().documentUri
                ?: throw AssertionError("The reader reports no document URI, so the journal cannot be located")
            val fieldsBefore = Await.untilValue(
                "the fixture's form fields are available to the debug probe",
                Await.LONG_TIMEOUT_MS,
                read = { AnnotationDump.formFields(0) },
            ) { it.isNotEmpty() }
            val journal = journalFor(documentUri)
            assertFalse("the read-only fixture must start without an annotation journal", journal.exists())
            assertFalse(
                "the read-only fixture must start without unsaved annotations",
                reader.readState().hasUnsavedAnnotations == true,
            )

            for (rect in listOf(SecurityFixtures.FIELD_READONLY_RECT, SecurityFixtures.FIELD_HIDDEN_RECT)) {
                SecurityReader.tapRect(reader, rect)
                assertNoFormEdit(fieldsBefore, journal)
            }
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        FileDescriptors.awaitNoneFor(fixture)
    }

    private fun formFieldTarget(file: File, name: String): FormFieldTarget {
        val size = ProducedPdf.pageSizes(file).firstOrNull()
            ?: throw AssertionError("${file.name} has no page on which to find $name")
        val candidates = ProducedPdf.formFieldRects(file, 0).mapNotNull { rect ->
            val left = minOf(rect[0], rect[2])
            val bottom = minOf(rect[1], rect[3])
            val right = maxOf(rect[0], rect[2])
            val top = maxOf(rect[1], rect[3])
            val field = ProducedPdf.formFieldAt(file, 0, (left + right) / 2f, (bottom + top) / 2f)
            if (field?.name == name) RectPoints(left, bottom, right, top) else null
        }
        if (candidates.isEmpty()) {
            throw AssertionError(
                "${file.name} exposes no form-field rectangle belonging to $name; its rectangles were " +
                    ProducedPdf.formFieldRects(file, 0).map { it.contentToString() }
            )
        }
        return FormFieldTarget(candidates, size.widthPoints.toFloat(), size.heightPoints.toFloat())
    }

    private fun openFormFieldDialog(target: FormFieldTarget) {
        target.candidates.forEach { rect ->
            SecurityReader.tapRect(reader, rect, target.pageWidthPoints, target.pageHeightPoints)
            val opened = runCatching {
                Await.until("the $FIELD_NAME edit dialog opens from fixture rectangle $rect", Await.SHORT_TIMEOUT_MS) {
                    isFormFieldDialogShowing()
                }
            }.isSuccess
            if (opened) return
        }
        throw AssertionError(
            "None of the fixture-derived $FIELD_NAME rectangles opened its edit dialog: ${target.candidates}"
        )
    }

    private fun assertNoFormEdit(fieldsBefore: List<FormFieldSnapshot>, journal: File) {
        Await.assertThroughout(
            condition = "the refused field tap opens no editor",
            windowMs = SecurityReader.QUIET_WINDOW_MS,
        ) { !isFormFieldDialogShowing() }
        assertEquals("a refused field tap must not change any form field", fieldsBefore, AnnotationDump.formFields(0))
        assertFalse(
            "a refused field tap must not mark the document dirty",
            reader.readState().hasUnsavedAnnotations == true,
        )
        assertFalse("a refused field tap must not create an annotation journal", journal.exists())
    }

    private fun isFormFieldDialogShowing(): Boolean =
        IntakeDialogs.isShowing(string(R.string.ok)) &&
            TestEnvironment.uiDevice.hasObject(By.clazz(EditText::class.java))

    private fun journalFor(documentUri: String): File = PrivacyStores.journalFor(Uri.parse(documentUri))

    private fun journalLines(journal: File): List<String> =
        if (journal.isFile) journal.readLines().filter { it.isNotBlank() } else emptyList()

    private fun containsAscii(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (offset in 0..haystack.size - needle.size) {
            for (position in needle.indices) {
                if (haystack[offset + position] != needle[position]) continue@outer
            }
            return true
        }
        return false
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val PDFIUM_LIBRARY = "libmodpdfium.so"
        const val OP_SET_FIELD_TEXT = "setFieldText"
        const val TYPED_VALUE = "test"
        const val FIELD_NAME = "jsfield"
        const val MARKER_NAME_FRAGMENT = "mjsec19-marker"

        val ENGINE_FRAGMENTS = listOf("v8::", "CJS_", "FXJS_", "IJS_", "snapshot_blob")
    }

    private data class FormFieldTarget(
        val candidates: List<RectPoints>,
        val pageWidthPoints: Float,
        val pageHeightPoints: Float,
    )
}
