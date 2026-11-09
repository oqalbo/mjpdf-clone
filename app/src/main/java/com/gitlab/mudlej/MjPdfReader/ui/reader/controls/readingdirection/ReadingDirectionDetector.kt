// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.controls.readingdirection

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.github.barteksc.pdfviewer.util.TextDirectionUtil
import com.gitlab.mudlej.MjPdfReader.pdf.ReadingDirection
import com.gitlab.mudlej.MjPdfReader.pdf.PdfExtractor
import com.gitlab.mudlej.MjPdfReader.pdf.createPdfExtractor
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReadingDirectionDetector {

    data class Result(
        val direction: ReadingDirection,
        val cacheable: Boolean,
    )

    private const val TAG = "ReadingDirectionDetector"
    private const val MAX_FIRST_PAGES = 8
    private const val MAX_STRONG_CHARS = 500
    private const val MIN_STRONG_CHARS = 24
    private const val CONFIDENCE_RATIO = 0.6f

    suspend fun detect(activity: Activity, uri: Uri, password: String?): Result {
        return withContext(Dispatchers.IO) {
            val extractor = try {
                createPdfExtractor(activity, uri, password)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Failed to open PDF for reading direction detection", throwable)
                return@withContext Result(ReadingDirection.UNKNOWN, cacheable = false)
            }

            try {
                detect(extractor)
            } finally {
                extractor.close()
            }
        }
    }

    private fun detect(extractor: PdfExtractor): Result {
        val pageCount = try {
            extractor.getPageCount()
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to read page count for reading direction detection", throwable)
            return Result(ReadingDirection.UNKNOWN, cacheable = false)
        }

        var ltrCount = 0
        var rtlCount = 0
        for (pageNumber in samplePages(pageCount)) {
            val pageText = try {
                extractor.getPageText(pageNumber)
            } catch (throwable: Throwable) {
                Log.w(TAG, "Failed to read page $pageNumber for reading direction detection", throwable)
                ""
            }
            val counts = countStrongDirectionChars(pageText)
            ltrCount += counts.leftToRight
            rtlCount += counts.rightToLeft
            if (ltrCount + rtlCount >= MAX_STRONG_CHARS) {
                break
            }
        }

        return Result(resolve(ltrCount, rtlCount), cacheable = true)
    }

    private fun samplePages(pageCount: Int): List<Int> {
        if (pageCount <= 0) {
            return emptyList()
        }
        val firstPages = (1..minOf(pageCount, MAX_FIRST_PAGES)).toList()
        val candidates = firstPages + listOf(pageCount / 2, pageCount)
            .map { it.coerceIn(1, pageCount) }
        return candidates.distinct()
    }

    private fun countStrongDirectionChars(text: String): StrongDirectionCounts {
        var ltrCount = 0
        var rtlCount = 0
        var index = 0
        while (index < text.length && ltrCount + rtlCount < MAX_STRONG_CHARS) {
            val codePoint = Character.codePointAt(text, index)
            if (TextDirectionUtil.isRtl(codePoint)) {
                rtlCount++
            } else if (Character.getDirectionality(codePoint) == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                ltrCount++
            }
            index += Character.charCount(codePoint)
        }
        return StrongDirectionCounts(ltrCount, rtlCount)
    }

    private fun resolve(ltrCount: Int, rtlCount: Int): ReadingDirection {
        val total = ltrCount + rtlCount
        if (total < MIN_STRONG_CHARS) {
            return ReadingDirection.UNKNOWN
        }
        val confidence = max(ltrCount, rtlCount).toFloat() / total
        if (confidence < CONFIDENCE_RATIO) {
            return ReadingDirection.UNKNOWN
        }
        return if (rtlCount > ltrCount) {
            ReadingDirection.RIGHT_TO_LEFT
        } else {
            ReadingDirection.LEFT_TO_RIGHT
        }
    }

    private data class StrongDirectionCounts(
        val leftToRight: Int,
        val rightToLeft: Int,
    )
}
