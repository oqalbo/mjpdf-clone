// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.PdfRenderOracle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment

object PageCapture {
    fun pdfViewDigest(): String = TestEnvironment.onMainThread {
        val activity = SecurityReader.activityOrNull()
            ?: throw ConditionTimeoutException("No reader is resumed while capturing the page")
        val view: View = activity.findViewById(R.id.pdfView)
            ?: throw ConditionTimeoutException("The reader has no pdfView while capturing the page")
        if (view.width <= 0 || view.height <= 0) {
            throw ConditionTimeoutException("The pdfView has no size, so there is nothing to capture")
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        PdfRenderOracle.digest(bitmap).also { bitmap.recycle() }
    }
}
