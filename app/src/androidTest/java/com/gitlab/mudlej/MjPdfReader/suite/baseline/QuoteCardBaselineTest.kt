// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.espresso.intent.rule.IntentsRule
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.By
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.QuoteCardFlow
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TouchActions
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.selection.SelectionIntents
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteCardTheme
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.ShareQuoteDebug
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class QuoteCardBaselineTest : BaselineTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @Before
    fun interceptTheSystemChooser() {
        IntentStubs.stubChooser()
    }

    @After
    fun releaseHeldBitmap() {
        runCatching { ShareQuoteDebug.clear() }
    }

    @Test
    @TestId("VIS-43")
    @TestDuration(DurationCategory.LONG)
    fun everyQuoteCardThemePaintsItsOwnColoursAtTheSameLayout() {
        openReaderForCards()

        val recordings = mutableListOf<BaselineRecorded>()
        QuoteCardTheme.entries.forEach { theme ->
            try {
                val card = renderCard(SHORT_QUOTE, theme)

                assertEquals("the card width for $theme", CARD_SIZE, card.width)
                assertEquals("the card height for $theme", CARD_SIZE, card.height)
                assertEquals(
                    "the corner pixel of the $theme card carries that theme's background colour exactly",
                    theme.backgroundColor,
                    card.getPixel(CORNER_SAMPLE, CORNER_SAMPLE),
                )

                assertBitmapMatches(
                    goldenName = "VIS-43-quote-card-${theme.name.lowercase()}.png",
                    actual = card,
                    expectation = producedCardExpectation("quote card in the $theme theme"),
                )
            } catch (recorded: BaselineRecorded) {
                recordings += recorded
            }
        }
        if (recordings.isNotEmpty()) {
            throw BaselineRecorded(recordings.joinToString("\n\n") { it.message.orEmpty() })
        }
    }

    @Test
    @TestId("VIS-44")
    fun anArabicQuoteRendersRightToLeftWithTheMarkOnTheRight() {
        openReaderForCards()

        val card = renderCard(ARABIC_QUOTE, QuoteCardTheme.LIGHT)

        assertBitmapMatches(
            goldenName = "VIS-44-quote-card-rtl.png",
            actual = card,
            expectation = producedCardExpectation("quote card for a right to left Arabic quote"),
        )
        assertDecorativeMarkSide(card, QuoteCardTheme.LIGHT, expectRightHalf = true)
    }

    @Test
    @TestId("VIS-45")
    fun aMostlyEnglishQuoteStaysLeftToRightDespiteAnArabicWord() {
        openReaderForCards()

        val card = renderCard(BaselineFixtures.MIXED_SCRIPT_LTR_QUOTE, QuoteCardTheme.LIGHT)

        assertBitmapMatches(
            goldenName = "VIS-45-quote-card-mixed-ltr.png",
            actual = card,
            expectation = producedCardExpectation("quote card for a mostly English quote with one Arabic word"),
        )
        assertDecorativeMarkSide(card, QuoteCardTheme.LIGHT, expectRightHalf = false)

        val rtlCard = renderCard(ARABIC_QUOTE, QuoteCardTheme.LIGHT)
        assertTrue(
            "a quote whose first strong character is Latin must not lay out like an Arabic one, so the two " +
                "cards cannot be the same image",
            !PixelComparison.areByteIdentical(card, rtlCard),
        )
    }

    @Test
    @TestId("VIS-46")
    fun theLongestAllowedQuoteIsShortenedAndStaysInsideTheCard() {
        openReaderForCards()

        val quote = longestQuote()
        assertEquals(
            "the quote handed to the card is at the documented maximum length",
            BaselineFixtures.MAX_QUOTE_CHARACTERS,
            quote.length,
        )

        val card = renderCard(quote, QuoteCardTheme.LIGHT)

        assertBitmapMatches(
            goldenName = "VIS-46-quote-card-max-length.png",
            actual = card,
            expectation = producedCardExpectation("quote card for a quote at the maximum length"),
        )

        val background = QuoteCardTheme.LIGHT.backgroundColor
        val gap = Rect(0, QUOTE_AREA_BOTTOM, CARD_SIZE, BOOK_NAME_BASELINE)
        val intruding = PixelRegions.countMatching(card, gap) { !PixelRegions.isNear(it, background) }
        assertEquals(
            "the band between the bottom of the quote area and the book name baseline must be empty, any " +
                "pixel there is the quote overflowing its area instead of ellipsising",
            0,
            intruding,
        )
    }

    private fun openReaderForCards() {
        baselineFixture(BaselineFixtures.GOLDEN_BOOK).let { launchAt(it) }
    }

    private fun renderCard(quote: String, theme: QuoteCardTheme): Bitmap {
        ShareQuoteDebug.clear()
        val activity = ViewUi.reader()
        TestEnvironment.onMainThread {
            ShareQuoteDebug.show(activity, quote, BOOK_NAME, AUTHOR)
        }
        TestEnvironment.waitForIdleSync()
        awaitDialog()
        selectTheme(theme)
        val intentMark = SelectionIntents.mark()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(TouchActions.click())
        TestEnvironment.waitForIdleSync()

        val bitmap = Await.untilNotNull(
            "the share quote dialog hands over the rendered card for $theme",
            Await.LONG_TIMEOUT_MS,
        ) { ShareQuoteDebug.sharedBitmap() }
        Await.untilValue(
            "exactly one chooser request is recorded for the rendered $theme card",
            Await.LONG_TIMEOUT_MS,
            read = { SelectionIntents.withActionSince(Intent.ACTION_CHOOSER, intentMark) },
        ) { it.size == 1 }
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        return BitmapFiles.toArgb(bitmap)
    }

    private fun selectTheme(theme: QuoteCardTheme) {
        QuoteCardFlow.selectTheme(theme)
    }

    private fun awaitDialog() {
        Await.until("the share quote dialog is showing", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.hasObject(By.res(ANDROID_PACKAGE, POSITIVE_BUTTON)) &&
                TestEnvironment.uiDevice.hasObject(
                    By.res(TestEnvironment.targetPackage, SHARE_PREVIEW)
                )
        }
    }

    private fun assertDecorativeMarkSide(card: Bitmap, theme: QuoteCardTheme, expectRightHalf: Boolean) {
        val background = theme.backgroundColor
        val left = Rect(0, MARK_BAND_TOP, MARK_BAND_WIDTH, MARK_BAND_BOTTOM)
        val right = Rect(CARD_SIZE - MARK_BAND_WIDTH, MARK_BAND_TOP, CARD_SIZE, MARK_BAND_BOTTOM)
        val leftPixels = PixelRegions.countMatching(card, left) { !PixelRegions.isNear(it, background) }
        val rightPixels = PixelRegions.countMatching(card, right) { !PixelRegions.isNear(it, background) }

        val marked = if (expectRightHalf) rightPixels else leftPixels
        val empty = if (expectRightHalf) leftPixels else rightPixels
        val side = if (expectRightHalf) "right" else "left"
        assertTrue(
            "the decorative quote mark must be drawn in the $side half, but that band holds $marked non " +
                "background pixels against $empty on the other side",
            marked > MARK_PIXEL_FLOOR,
        )
        assertEquals(
            "the opposite band must hold no mark at all, a mark on both sides means the direction was not " +
                "resolved",
            0,
            empty,
        )
    }

    private fun longestQuote(): String {
        val source = BaselineFixtures.MIXED_SCRIPT_LTR_QUOTE.replace(Regex("[^\\x20-\\x7E]"), "x")
        val builder = StringBuilder()
        while (builder.length < BaselineFixtures.MAX_QUOTE_CHARACTERS) {
            builder.append(source).append(' ')
        }
        return builder.substring(0, BaselineFixtures.MAX_QUOTE_CHARACTERS)
    }

    private fun producedCardExpectation(contentKey: String): GateExpectation =
        GateExpectation.forProducedImage(contentKey)

    private companion object {
        const val CARD_SIZE = BaselineFixtures.QUOTE_CARD_SIZE
        const val CORNER_SAMPLE = 5
        const val QUOTE_AREA_BOTTOM = 806
        const val BOOK_NAME_BASELINE = 865
        const val MARK_BAND_TOP = 40
        const val MARK_BAND_BOTTOM = 190
        const val MARK_BAND_WIDTH = 200
        const val MARK_PIXEL_FLOOR = 500
        const val BOOK_NAME = "The Memory Model"
        const val AUTHOR = "A. Writer"
        const val SHORT_QUOTE = "A short quote that fits the card at its starting text size."
        const val ARABIC_QUOTE = "الكتاب الذي بين يديك ليس كتابا عاديا بل هو رحلة طويلة في الذاكرة."
        const val ANDROID_PACKAGE = "android"
        const val POSITIVE_BUTTON = "button1"
        const val SHARE_PREVIEW = "sharePreview"
    }
}
