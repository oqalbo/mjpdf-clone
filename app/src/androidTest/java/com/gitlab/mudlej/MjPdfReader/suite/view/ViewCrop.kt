// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.view

import android.view.View
import com.github.barteksc.pdfviewer.model.CropMargins
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.data.Records

object ViewCrop {

    const val DETECTION_TIMEOUT_MS = 120_000L
    const val STORAGE_VERSION = CropMargins.STORAGE_VERSION

    fun runsSoFar(): Int = ReaderTestHooks.cropDetectionRuns

    fun outcome(): String = ReaderTestHooks.cropDetectionOutcome

    fun runOutcome(): String = ReaderTestHooks.cropDetectionRunOutcome

    fun resetHooks() {
        ReaderTestHooks.reset()
    }

    fun awaitDetectionRunning(timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        Await.until("crop detection reports itself running", timeoutMs) { ReaderTestHooks.cropDetectionRunning }
    }

    fun awaitDetectionFinished(runsBefore: Int, timeoutMs: Long = DETECTION_TIMEOUT_MS): String {
        Await.until("crop detection finishes a run past $runsBefore", timeoutMs) {
            ReaderTestHooks.cropDetectionRuns > runsBefore && !ReaderTestHooks.cropDetectionRunning
        }
        return runOutcome()
    }

    fun awaitStatusCardGone(timeoutMs: Long = DETECTION_TIMEOUT_MS) {
        try {
            ViewUi.awaitVisibility(R.id.cropDetectionStatusCard, View.GONE, timeoutMs)
        } catch (timeout: ConditionTimeoutException) {
            throw ConditionTimeoutException(
                "${timeout.message}, after ${runsSoFar()} detection run(s) ending in \"${runOutcome()}\" " +
                    "with the latest hook outcome \"${outcome()}\" and running=${ReaderTestHooks.cropDetectionRunning}"
            )
        }
    }

    fun awaitStoredCrop(fileHash: String, timeoutMs: Long = Await.LONG_TIMEOUT_MS): PdfRecord =
        Records.awaitRow(
            fileHash,
            "the PdfRecord row for $fileHash stores a crop margins result",
            timeoutMs,
        ) { it?.cropMargins != null }!!

    fun parse(storage: String): CropMargins = CropMargins.fromStorageString(storage)
        ?: throw AssertionError("The stored crop margins string \"$storage\" did not parse")

    fun assertNoStoredCrop(fileHash: String, windowMs: Long = Records.STABLE_ABSENCE_MS) {
        Await.assertThroughout(
            condition = "the PdfRecord row for $fileHash keeps no crop margins",
            windowMs = windowMs,
        ) { Records.byHash(fileHash)?.cropMargins == null }
    }
}
