// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.annotation

import android.app.Activity
import android.text.InputType
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.annotation.AnnotationEdit
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityMainBinding
import com.gitlab.mudlej.MjPdfReader.core.ui.AppSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.shockwave.pdfium.PdfDocument

class FormFieldController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val onAnnotationEdit: (AnnotationEdit) -> Unit,
    private val canEditDocument: () -> Boolean,
) {

    fun handlePdfTap(event: MotionEvent): Boolean {
        val field = binding.pdfView.findFormFieldAt(event.x, event.y) ?: return false
        if (field.readOnly) {
            return false
        }
        return when (field.type) {
            PdfDocument.FormField.TYPE_TEXT_FIELD -> {
                showTextFieldDialog(field)
                true
            }
            PdfDocument.FormField.TYPE_CHECKBOX -> setChecked(field, !field.checked)
            PdfDocument.FormField.TYPE_RADIO_BUTTON -> setChecked(field, true)
            else -> false
        }
    }

    private fun showTextFieldDialog(field: PDFView.FormField) {
        val input = createFieldInput(field)
        MaterialAlertDialogBuilder(activity)
            .setTitle(fieldTitle(field))
            .setView(createDialogContainer(input))
            .setPositiveButton(R.string.ok) { _, _ -> applyText(field, input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun fieldTitle(field: PDFView.FormField): String {
        field.alternateName.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val leaf = field.name.substringAfterLast('.').replace(FIELD_INDEX_REGEX, "").trim()
        return leaf.ifBlank { activity.getString(R.string.form_field) }
    }

    private fun createFieldInput(field: PDFView.FormField): EditText {
        val input = EditText(activity)
        input.inputType = if (field.multiline) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        input.setText(field.value)
        input.setSelection(field.value.length)
        return input
    }

    private fun createDialogContainer(input: EditText): FrameLayout {
        val container = FrameLayout(activity)
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DIALOG_PADDING_DP,
            activity.resources.displayMetrics,
        ).toInt()
        container.setPadding(padding, padding / 2, padding, 0)
        container.addView(input)
        return container
    }

    private fun applyText(field: PDFView.FormField, text: String) {
        if (text == field.value) {
            return
        }
        if (!canEditDocument()) {
            return
        }
        if (!binding.pdfView.setFormFieldText(field.pageIndex, field.annotationIndex, text)) {
            showUpdateFailed()
            return
        }
        onAnnotationEdit(AnnotationEdit.SetFieldText(field.pageIndex, field.annotationIndex, field.name, text))
    }

    private fun setChecked(field: PDFView.FormField, checked: Boolean): Boolean {
        if (field.checked == checked) {
            return true
        }
        if (!canEditDocument()) {
            return true
        }
        if (!binding.pdfView.setFormFieldChecked(field.pageIndex, field.annotationIndex, checked)) {
            showUpdateFailed()
            return true
        }
        onAnnotationEdit(AnnotationEdit.SetFieldChecked(field.pageIndex, field.annotationIndex, field.name, checked))
        return true
    }

    private fun showUpdateFailed() {
        AppSnackbar.make(binding.root, R.string.form_field_update_failed, Snackbar.LENGTH_SHORT).show()
    }

    private companion object {
        const val DIALOG_PADDING_DP = 24f
        val FIELD_INDEX_REGEX = Regex("\\[[^]]*]")
    }
}
