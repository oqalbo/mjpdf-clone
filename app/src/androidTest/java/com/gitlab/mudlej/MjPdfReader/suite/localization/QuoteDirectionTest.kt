// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.text.TextDirection
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionShare
import com.gitlab.mudlej.MjPdfReader.suite.selection.ShareQuoteRobot
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.ShareQuoteDebug
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class QuoteDirectionTest : LocaleTestCase() {

    @After
    fun releaseQuoteState() {
        runCatching { ShareQuoteDebug.clear() }
    }

    @Test
    @TestId("LOC-41")
    fun aMostlyEnglishQuoteStaysLeftToRight() {
        assertCardDirection(LocaleFixtures.MIXED_LATIN_LEADING_PASSAGE, rightToLeft = false)
    }

    @Test
    @TestId("LOC-42")
    fun anArabicQuoteRendersRightToLeft() {
        assertCardDirection(LocaleFixtures.ARABIC_ONLY_PASSAGE, rightToLeft = true)
    }

    @Test
    @TestId("LOC-43")
    fun aMixedQuoteWhoseArabicOutnumbersLatinFlipsRightToLeft() {
        assertCardDirection(LocaleFixtures.MIXED_ARABIC_LEADING_PASSAGE, rightToLeft = true)
    }

    @Test
    @TestId("LOC-44")
    fun aQuoteStartingWithADigitFollowsTheFirstStrongArabicCharacter() {
        assertCardDirection(LocaleFixtures.DIGIT_LEADING_ARABIC_PASSAGE, rightToLeft = true)
    }

    @Test
    @TestId("LOC-45")
    fun aQuoteWithNoStrongCharactersStaysLeftToRight() {
        assertCardDirection(LocaleFixtures.NUMERIC_ONLY_PASSAGE, rightToLeft = false)
    }

    private fun assertCardDirection(quote: String, rightToLeft: Boolean) {
        assertEquals(
            "TextDirection.isRtlBaseDirection is the rule this card follows: right to left when the first " +
                "strong character is right to left, or when the first strong character is left to right and " +
                "right to left characters outnumber left to right ones. The passage was \"$quote\".",
            rightToLeft,
            TextDirection.isRtlBaseDirection(quote),
        )

        val file = fixture(LocaleFixtures.NORMAL)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use { scenario ->
            awaitReaderReady()

            scenario.onActivity { activity ->
                ShareQuoteDebug.show(activity, quote, BOOK_NAME, AUTHOR)
            }
            ShareQuoteRobot.awaitShown(Await.LONG_TIMEOUT_MS)

            val card = awaitPreviewBitmap()
            assertEquals(
                "the quote card is rendered at ${SelectionShare.CARD_SIZE} square",
                SelectionShare.CARD_SIZE,
                card.width,
            )

            val background = card.getPixel(SelectionShare.BACKGROUND_PROBE_X, SelectionShare.BACKGROUND_PROBE_Y)
            val inkedCorner = if (rightToLeft) SelectionShare.TOP_RIGHT_DECOR else SelectionShare.TOP_LEFT_DECOR
            val clearCorner = if (rightToLeft) SelectionShare.TOP_LEFT_DECOR else SelectionShare.TOP_RIGHT_DECOR
            val side = if (rightToLeft) "right" else "left"

            assertTrue(
                "the decorative quote mark must be drawn in the $side half of the card, but nothing was " +
                    "painted inside $inkedCorner",
                hasInk(card, inkedCorner, background),
            )
            assertFalse(
                "the opposite corner must stay empty, but something was painted inside $clearCorner",
                hasInk(card, clearCorner, background),
            )

            ShareQuoteRobot.cancel()
            ShareQuoteRobot.awaitGone()
        }
    }

    private fun awaitPreviewBitmap(): Bitmap =
        Await.untilNotNull("the share quote dialog renders its card preview", Await.LONG_TIMEOUT_MS) {
            runCatching {
                AnnotationDialogs.capture(withId(R.id.sharePreview)) { view ->
                    ((view as ImageView).drawable as? BitmapDrawable)?.bitmap
                }
            }.getOrNull()
        }

    private fun hasInk(card: Bitmap, area: Rect, background: Int): Boolean {
        if (area.right > card.width || area.bottom > card.height) {
            throw ConditionTimeoutException(
                "The probe area $area falls outside a ${card.width} by ${card.height} card"
            )
        }
        for (y in area.top until area.bottom step PROBE_STEP) {
            for (x in area.left until area.right step PROBE_STEP) {
                if (card.getPixel(x, y) != background) return true
            }
        }
        return false
    }

    private companion object {
        const val BOOK_NAME = "loc quote"
        const val AUTHOR = "loc author"
        const val PROBE_STEP = 2
    }
}
