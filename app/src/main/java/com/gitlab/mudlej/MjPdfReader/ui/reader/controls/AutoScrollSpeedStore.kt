// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls

import com.gitlab.mudlej.MjPdfReader.ui.reader.DocumentState
import com.gitlab.mudlej.MjPdfReader.data.HistoryPolicy
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoScrollSpeedStore(
    private val pdf: DocumentState,
    private val pdfRepository: PdfRepository,
    private val historyPolicy: HistoryPolicy,
    private val scope: CoroutineScope,
    private val backgroundScope: CoroutineScope,
) {

    private data class PendingSave(val fileHash: String, val speed: Int)

    private var saveJob: Job? = null
    private var pendingSave: PendingSave? = null

    fun onSpeedChanged(speed: Int) {
        pdf.autoScrollSpeed = speed
        val fileHash = pdf.fileHash ?: return
        val pending = PendingSave(fileHash, speed)

        pendingSave = pending
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY)
            savePending(pending)
        }
    }

    fun flushPendingSave() {
        val pending = pendingSave ?: return
        saveJob?.cancel()
        saveJob = null
        pendingSave = null
        backgroundScope.launch {
            if (historyPolicy.canRecord()) {
                pdfRepository.setAutoScrollSpeed(pending.fileHash, pending.speed)
            }
        }
    }

    private suspend fun savePending(pending: PendingSave) {
        if (pendingSave != pending) {
            return
        }

        if (historyPolicy.canRecord()) {
            pdfRepository.setAutoScrollSpeed(pending.fileHash, pending.speed)
        }
        if (pendingSave == pending) {
            pendingSave = null
        }
    }

    private companion object {
        const val SAVE_DELAY = 300L
    }
}
