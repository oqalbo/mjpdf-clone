// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.graphics.PointF
import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDump
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.robot.SelectionRobot
import java.io.File

data class ProducedFormField(
    val type: Int,
    val name: String,
    val text: String,
    val checked: Boolean,
)

object FormFields {

    fun readAll(file: File, pageIndex: Int, password: String? = null): List<ProducedFormField> =
        rectCentres(file, pageIndex, password).mapNotNull { centre ->
            ProducedPdf.formFieldAt(file, pageIndex, centre.x, centre.y, password)?.let { field ->
                ProducedFormField(
                    type = field.type,
                    name = field.name.orEmpty(),
                    text = field.value.orEmpty(),
                    checked = field.isChecked,
                )
            }
        }

    fun tapFirstTextField(reader: ArtifactReader): ProducedFormField {
        val opened = tapGrid(reader, TEXT_TYPE) { isFieldDialogShowing() }
            ?: throw ConditionTimeoutException(
                "No tap on the page opened a form field dialog. The forms fixture places its fields so " +
                    "all three are visible at fit width, so a miss here means the page is not laid out as " +
                    "the fixture README records."
            )
        return opened
    }

    fun tapFirstCheckbox(reader: ArtifactReader): ProducedFormField {
        val before = AnnotationDump.formFields(PAGE_INDEX)
            .filter { it.isCheckbox }
            .associate { it.index to it.checked }
        val tapped = tapGrid(reader, CHECKBOX_TYPE) {
            val changed = AnnotationDump.formFields(PAGE_INDEX)
                .filter { it.isCheckbox }
                .any { field -> before[field.index]?.let { it != field.checked } == true }
            changed && reader.hasUnsavedEdits()
        }
        if (tapped == null) {
            throw ConditionTimeoutException(
                "No checkbox rectangle in the fixture produced a live checked-state change and an unsaved edit"
            )
        }
        val changed = AnnotationDump.formFields(PAGE_INDEX)
            .filter { it.isCheckbox }
            .firstOrNull { field -> before[field.index]?.let { it != field.checked } == true }
            ?: throw ConditionTimeoutException("The changed checkbox disappeared from the live form-field dump")
        return ProducedFormField(
            type = changed.type,
            name = changed.name,
            text = changed.value,
            checked = changed.checked,
        )
    }

    fun typeAndConfirm(value: String) {
        val field = Await.untilNotNull("the form field dialog offers a text field") { dialogTextField() }
        TestEnvironment.onMainThread { field.setText(value) }
        TestEnvironment.waitForIdleSync()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
        Await.until("the form field dialog closes") { !isFieldDialogShowing() }
    }

    fun isFieldDialogShowing(): Boolean =
        TestEnvironment.uiDevice.hasObject(By.res(ANDROID, POSITIVE_BUTTON))

    private fun tapGrid(
        reader: ArtifactReader,
        fieldType: Int,
        satisfied: (ProducedFormField) -> Boolean,
    ): ProducedFormField? {
        val selection = SelectionRobot()
        val pageRect = reader.state().pageRect
            ?: throw ConditionTimeoutException("The reader reports no page rectangle to aim at")
        val source = ArtifactFixtures.require(ArtifactFixtures.FORMS)
        val pageSize = ProducedPdf.pageSizes(source).firstOrNull()
            ?: throw ConditionTimeoutException("The forms fixture reports no page size to map field rectangles through")
        val targets = rectCentres(source, PAGE_INDEX, null).mapNotNull { centre ->
            val field = ProducedPdf.formFieldAt(source, PAGE_INDEX, centre.x, centre.y, null)
                ?: return@mapNotNull null
            if (field.type != fieldType) return@mapNotNull null
            centre to ProducedFormField(
                type = field.type,
                name = field.name.orEmpty(),
                text = field.value.orEmpty(),
                checked = field.isChecked,
            )
        }
        if (targets.isEmpty()) {
            throw ConditionTimeoutException(
                "The forms fixture declares no form field rectangle on page 1, so no tap can reach a field. " +
                    "A fixture with widget annotations but no /AcroForm reads this way to pdfium."
            )
        }
        targets.forEach { (centre, field) ->
            val point = PointF(
                pageRect.left + pageRect.width() * centre.x / pageSize.widthPoints,
                pageRect.top + pageRect.height() * (pageSize.heightPoints - centre.y) / pageSize.heightPoints,
            )
            val screen = selection.toScreen(point)
            TestEnvironment.uiDevice.click(screen.x.toInt(), screen.y.toInt())
            TestEnvironment.waitForIdleSync()
            val changed = runCatching {
                Await.until(
                    "the tapped ${field.name.ifBlank { "checkbox" }} changes live state",
                    Await.SHORT_TIMEOUT_MS,
                ) { satisfied(field) }
                true
            }.getOrDefault(false)
            if (changed) return field
        }
        return null
    }

    private fun rectCentres(file: File, pageIndex: Int, password: String?): List<PointF> =
        ProducedPdf.formFieldRects(file, pageIndex, password).map { rect ->
            PointF((rect[0] + rect[2]) / 2f, (rect[1] + rect[3]) / 2f)
        }

    private fun dialogTextField(): EditText? = runCatching {
        if (!isFieldDialogShowing()) return@runCatching null
        val root = Dialogs.dialogRoot()
        TestEnvironment.onMainThread { Dialogs.firstDescendant(root, EditText::class.java) }
    }.getOrNull()

    const val TEXT_TYPE = com.shockwave.pdfium.PdfDocument.FormField.TYPE_TEXT_FIELD
    const val CHECKBOX_TYPE = com.shockwave.pdfium.PdfDocument.FormField.TYPE_CHECKBOX

    private const val PAGE_INDEX = 0
    private const val ANDROID = "android"
    private const val POSITIVE_BUTTON = "button1"
}
