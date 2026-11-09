// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls.readingdirection

import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.resolveReadingLayout
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.ReaderViewModel
import com.gitlab.mudlej.MjPdfReader.ui.reader.load.DocumentLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReadingDirectionController(
    private val activity: MainActivity,
    private val pdf: DocumentState,
    private val vm: ReaderViewModel,
    private val pref: Preferences,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val scope: CoroutineScope,
    private val resolver: ReadingDirectionResolver,
    private val documentLoader: DocumentLoader,
) {

    fun showDialog() {
        var selectedOverride = pdf.readingDirectionOverride
        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.reading_direction)
            .setSingleChoiceItems(
                dialogItems(),
                selectedIndexFor(selectedOverride),
            ) { _, which ->
                selectedOverride = overrideForIndex(which)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                applyOverride(selectedOverride)
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (!resolveReadingLayout(pref).swipeHorizontal) {
            dialogBuilder.setMessage(R.string.reading_direction_message)
        }
        dialogBuilder.show()
    }

    private fun dialogItems(): Array<String> {
        val autoLabel = if (pdf.effectiveReadingDirection.isRightToLeft) {
            R.string.reading_direction_auto_rtl
        } else {
            R.string.reading_direction_auto_ltr
        }
        return arrayOf(
            activity.getString(autoLabel),
            activity.getString(R.string.reading_direction_ltr),
            activity.getString(R.string.reading_direction_rtl),
        )
    }

    private fun selectedIndexFor(direction: ReadingDirection?): Int {
        return when (direction) {
            null -> 0
            ReadingDirection.LEFT_TO_RIGHT -> 1
            ReadingDirection.RIGHT_TO_LEFT -> 2
            ReadingDirection.UNKNOWN -> 0
        }
    }

    private fun overrideForIndex(index: Int): ReadingDirection? {
        return when (index) {
            1 -> ReadingDirection.LEFT_TO_RIGHT
            2 -> ReadingDirection.RIGHT_TO_LEFT
            else -> null
        }
    }

    private fun applyOverride(direction: ReadingDirection?) {
        val loadToken = vm.currentLoadToken
        val documentUri = pdf.uri
        val oldEffectiveDirection = pdf.effectiveReadingDirection
        scope.launch {
            val hash = pdf.fileHash ?: pdfRepository.resolveIdentity(activity, pdf.uri)
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (hash == null) {
                documentLoader.showFailedToComputeHashError()
                return@launch
            }

            pdf.fileHash = hash
            if (historyPolicy.canRecord()) {
                pdfRepository.setReadingDirectionOverride(hash, direction?.id)
            }
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }

            val detectedDirection = if (direction == null && pdf.detectedReadingDirection == null) {
                resolver.detectIfNeeded(documentUri)
            } else {
                pdf.detectedReadingDirection
            }
            if (!vm.isCurrent(loadToken, documentUri)) {
                return@launch
            }
            if (historyPolicy.canRecord()) {
                detectedDirection?.let { pdfRepository.setDetectedReadingDirection(hash, it.id) }
            }

            pdf.readingDirectionOverride = direction
            pdf.detectedReadingDirection = detectedDirection
            pdf.effectiveReadingDirection = ReadingDirection.effective(direction, detectedDirection)
            if (resolveReadingLayout(pref).swipeHorizontal && pdf.effectiveReadingDirection != oldEffectiveDirection) {
                activity.recreate()
            }
        }
    }
}
