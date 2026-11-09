// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteCardTheme
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteImageRenderer
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.ShareQuoteDebug
import java.io.File

object QuoteCardFlow {

    const val CARD_SIZE = 1080

    val namePattern = Regex("MJ_quote_\\d{4}_\\d{2}_\\d{2}-\\d{2}_\\d{2}_\\d{2}\\.png")

    fun open(activity: Activity, quote: String, bookName: String, author: String) {
        TestEnvironment.onMainThread { ShareQuoteDebug.show(activity, quote, bookName, author) }
        TestEnvironment.waitForIdleSync()
        awaitShown()
    }

    fun awaitShown(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the share quote dialog offers its preview and its share button", timeoutMs) {
            hasNode(TestEnvironment.targetPackage, PREVIEW) && hasNode(ANDROID_PACKAGE, POSITIVE_BUTTON)
        }
    }

    fun awaitGone(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the share quote dialog is dismissed", timeoutMs) {
            !hasNode(TestEnvironment.targetPackage, PREVIEW)
        }
    }

    fun isShortenedNoticeVisible(): Boolean = hasNode(TestEnvironment.targetPackage, SHORTENED_NOTICE)

    fun expandCustomiseSection() {
        if (isCustomiseSectionExpanded()) return
        onView(withId(R.id.customizeHeader)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
        Await.until("the customise section is expanded") { isCustomiseSectionExpanded() }
    }

    fun selectTheme(theme: QuoteCardTheme) {
        expandCustomiseSection()
        onView(withId(chipIdOf(theme))).inRoot(isDialog()).perform(scrollTo(), click())
        TestEnvironment.waitForIdleSync()
        Await.until("the ${theme.name} chip reads checked") {
            TestEnvironment.uiDevice.hasObject(
                By.res(TestEnvironment.targetPackage, chipResourceOf(theme)).checked(true)
            )
        }
    }

    fun share(): File {
        val before = AppDirectories.quoteCards().map { it.name }.toSet()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        TestEnvironment.waitForIdleSync()
        return Await.untilValue(
            "a new quote card PNG appears in ${AppDirectories.shareCache.absolutePath}",
            Await.LONG_TIMEOUT_MS,
            read = { AppDirectories.quoteCards().filter { it.name !in before && it.length() > 0L } },
        ) { it.isNotEmpty() }.maxByOrNull { it.lastModified() }!!
    }

    fun shortenedForm(quote: String): String {
        val trimmed = quote.trim()
        if (trimmed.length <= QuoteImageRenderer.MAX_QUOTE_CHARS) {
            return trimmed
        }
        val cut = trimmed.take(QuoteImageRenderer.MAX_QUOTE_CHARS)
        val boundary = cut.indexOfLast { it.isWhitespace() }
        val kept = if (boundary > QuoteImageRenderer.MAX_QUOTE_CHARS / 2) cut.substring(0, boundary) else cut
        return kept.trimEnd() + ELLIPSIS
    }

    fun decode(file: File): Bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ?: throw AssertionError("The quote card ${file.absolutePath} did not decode as a bitmap")

    fun boundsOf(file: File): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return ImageBounds(options.outWidth, options.outHeight, options.outMimeType.orEmpty())
    }

    fun declaredColourType(file: File): Int {
        val head = ArtifactBytes.firstBytes(file, IHDR_COLOUR_TYPE_OFFSET + 1)
        if (head.size <= IHDR_COLOUR_TYPE_OFFSET) {
            throw AssertionError("The quote card ${file.absolutePath} is too short to carry a PNG header")
        }
        return head[IHDR_COLOUR_TYPE_OFFSET].toInt() and BYTE_MASK
    }

    fun nonBackgroundPixels(bitmap: Bitmap, background: Int, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (x in left until right.coerceAtMost(bitmap.width)) {
            for (y in top until bottom.coerceAtMost(bitmap.height)) {
                if (bitmap.getPixel(x, y) != background) {
                    count++
                }
            }
        }
        return count
    }

    fun isFullyOpaque(bitmap: Bitmap): Boolean {
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                if ((bitmap.getPixel(x, y) ushr ALPHA_SHIFT) != OPAQUE_ALPHA) {
                    return false
                }
                y += OPACITY_STEP
            }
            x += OPACITY_STEP
        }
        return true
    }

    fun differingPixels(first: Bitmap, second: Bitmap): Int {
        if (first.width != second.width || first.height != second.height) {
            throw AssertionError(
                "The two cards have different sizes, ${first.width}x${first.height} against " +
                    "${second.width}x${second.height}"
            )
        }
        var count = 0
        for (x in 0 until first.width) {
            for (y in 0 until first.height) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) {
                    count++
                }
            }
        }
        return count
    }

    private fun isCustomiseSectionExpanded(): Boolean {
        var visibility: Int? = null
        onView(withId(R.id.customizeSection)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            visibility = view.visibility
        }
        return when (visibility) {
            View.VISIBLE -> true
            View.GONE -> false
            else -> throw AssertionError(
                "The customise section reports visibility $visibility rather than View.VISIBLE or View.GONE"
            )
        }
    }

    private fun hasNode(packageName: String, resourceName: String): Boolean =
        TestEnvironment.uiDevice.hasObject(By.res(packageName, resourceName))

    private fun chipIdOf(theme: QuoteCardTheme): Int = when (theme) {
        QuoteCardTheme.LIGHT -> R.id.themeLight
        QuoteCardTheme.DARK -> R.id.themeDark
        QuoteCardTheme.SEPIA -> R.id.themeSepia
        QuoteCardTheme.ROSE -> R.id.themeRose
        QuoteCardTheme.LAVENDER -> R.id.themeLavender
        QuoteCardTheme.MINT -> R.id.themeMint
    }

    private fun chipResourceOf(theme: QuoteCardTheme): String = when (theme) {
        QuoteCardTheme.LIGHT -> THEME_LIGHT
        QuoteCardTheme.DARK -> "themeDark"
        QuoteCardTheme.SEPIA -> "themeSepia"
        QuoteCardTheme.ROSE -> "themeRose"
        QuoteCardTheme.LAVENDER -> "themeLavender"
        QuoteCardTheme.MINT -> "themeMint"
    }

    const val ELLIPSIS = "…"
    const val PNG_COLOUR_TYPE_RGBA = 6

    private const val PREVIEW = "sharePreview"
    private const val SHORTENED_NOTICE = "shortenedNotice"
    private const val THEME_LIGHT = "themeLight"
    private const val ANDROID_PACKAGE = "android"
    private const val POSITIVE_BUTTON = "button1"
    private const val IHDR_COLOUR_TYPE_OFFSET = 25
    private const val BYTE_MASK = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val OPAQUE_ALPHA = 0xFF
    private const val OPACITY_STEP = 4
}
