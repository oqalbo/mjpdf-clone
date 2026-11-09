// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.actions.ConfigurableAction

data class ImageBounds(val width: Int, val height: Int, val mimeType: String)

object ScreenshotFlow {

    val namePattern = Regex(".+ - \\d{4}_\\d{2}_\\d{2}-\\d{2}_\\d{2}_\\d{2}\\.jpg")

    fun seedAsPrimaryToolbarAction() {
        SettingsSeeder.seed(Preferences.primaryButtonActionKey, ConfigurableAction.SCREENSHOT.id)
    }

    fun take() {
        onView(withId(R.id.toolbarPrimaryActionOption)).perform(click())
        TestEnvironment.waitForIdleSync()
    }

    fun awaitSaved(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Snackbars.awaitText(TestEnvironment.targetContext.getString(R.string.screenshot_saved), timeoutMs)
    }

    fun namePrefixFor(documentName: String): String = documentName.removeSuffix(".pdf") + " - "

    fun boundsOf(bytes: ByteArray): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return ImageBounds(options.outWidth, options.outHeight, options.outMimeType.orEmpty())
    }

    fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw AssertionError("The produced image did not decode as a bitmap")

    fun distinctColourCount(bitmap: Bitmap, limit: Int = COLOUR_SAMPLE_LIMIT): Int {
        val colours = HashSet<Int>()
        val stepX = (bitmap.width / SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (bitmap.height / SAMPLE_GRID).coerceAtLeast(1)
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colours.add(bitmap.getPixel(x, y))
                if (colours.size >= limit) return colours.size
                y += stepY
            }
            x += stepX
        }
        return colours.size
    }

    fun meanLuminance(bitmap: Bitmap): Double {
        var total = 0.0
        var samples = 0
        val stepX = (bitmap.width / SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (bitmap.height / SAMPLE_GRID).coerceAtLeast(1)
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                total += RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
                samples++
                y += stepY
            }
            x += stepX
        }
        return if (samples == 0) 0.0 else total / samples
    }

    private const val SAMPLE_GRID = 64
    private const val COLOUR_SAMPLE_LIMIT = 4096
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
}
