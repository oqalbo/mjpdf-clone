// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteCardTheme
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteImageRenderer
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.ShareQuoteDebug
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class QuoteCardArtifactTest : ArtifactTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @After
    fun releaseTheHeldCard() {
        runCatching { ShareQuoteDebug.clear() }
    }

    @Test
    @TestId("ART-47")
    @TestDuration(DurationCategory.SHORT)
    fun aSharedQuoteCardIsASquareOpaquePngInTheShareCache() {
        IntentStubs.stubChooser()
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, CARD_NAME)

        openReader(document).use {
            reader.selectWordOnCurrentPage()
            reader.openShareQuote()
            QuoteCardFlow.awaitShown()

            val card = QuoteCardFlow.share()

            assertEquals(
                "the share cache must hold exactly the one card this share produced and nothing else",
                listOf(card.name),
                AppDirectories.namesIn(AppDirectories.shareCache),
            )
            assertTrue(
                "the card must be named MJ_quote_<timestamp>.png, it reads ${card.name}",
                QuoteCardFlow.namePattern.matches(card.name),
            )
            assertTrue(
                "the produced file must open with the eight byte PNG signature",
                ArtifactBytes.startsWith(card, ArtifactBytes.PNG_MAGIC),
            )

            val bounds = QuoteCardFlow.boundsOf(card)
            assertEquals("the card must be $CARD_SIZE pixels wide", CARD_SIZE, bounds.width)
            assertEquals("the card must be $CARD_SIZE pixels tall", CARD_SIZE, bounds.height)
            assertEquals(
                "the card must declare an alpha channel in its PNG header, colour type 6, so the file the user " +
                    "sends carries the same pixel format the renderer produced",
                QuoteCardFlow.PNG_COLOUR_TYPE_RGBA,
                QuoteCardFlow.declaredColourType(card),
            )
            assertTrue(
                "every pixel of the card must be fully opaque, a card with transparent pixels renders as a hole " +
                    "in whichever app it is sent to",
                QuoteCardFlow.isFullyOpaque(QuoteCardFlow.decode(card)),
            )
        }
    }

    @Test
    @TestId("ART-48")
    @TestDuration(DurationCategory.LONG)
    fun eachQuoteCardThemePaintsItsOwnExactBackgroundColour() {
        IntentStubs.stubChooser()
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, THEME_NAME)
        val sampled = mutableMapOf<QuoteCardTheme, Int>()

        openReader(document).use {
            val activity = reader.requireActivity()
            QuoteCardTheme.entries.forEach { theme ->
                QuoteCardFlow.open(activity, SHORT_QUOTE, BOOK_NAME, AUTHOR)
                QuoteCardFlow.selectTheme(theme)
                val card = QuoteCardFlow.share()

                val bounds = QuoteCardFlow.boundsOf(card)
                assertEquals("the $theme card must be $CARD_SIZE pixels wide", CARD_SIZE, bounds.width)
                assertEquals("the $theme card must be $CARD_SIZE pixels tall", CARD_SIZE, bounds.height)

                val bitmap = QuoteCardFlow.decode(card)
                sampled[theme] = bitmap.getPixel(CORNER_SAMPLE_X, CORNER_SAMPLE_Y)
                card.delete()
                QuoteCardFlow.awaitGone()
            }
        }

        QuoteCardTheme.entries.forEach { theme ->
            assertEquals(
                "the empty bottom left corner of the $theme card must carry that theme's background colour " +
                    "exactly, so the theme the user picked is the theme the file carries",
                theme.backgroundColor,
                sampled[theme],
            )
        }
        assertEquals(
            "the six themes must produce six different background colours, otherwise a theme choice is not " +
                "visible in the produced file at all. The samples read $sampled",
            QuoteCardTheme.entries.size,
            sampled.values.distinct().size,
        )
    }

    @Test
    @TestId("ART-49")
    fun quoteDirectionDecidesWhichSideTheDecorativeMarkIsDrawnOn() {
        IntentStubs.stubChooser()
        val arabicDocument = copyFixtureToDownloads(ArtifactFixtures.HIGHLIGHTS_ARABIC, ARABIC_NAME)
        val latinDocument = copyFixtureToDownloads(ArtifactFixtures.SMALL, MIXED_NAME)
        val background = QuoteCardTheme.LIGHT.backgroundColor

        val arabicCard = openReader(arabicDocument).use { renderAndDecode(ARABIC_QUOTE) }
        leaveReader()
        val mixedCard = openReader(latinDocument).use { renderAndDecode(MOSTLY_ENGLISH_QUOTE) }

        val arabicLeft = QuoteCardFlow.nonBackgroundPixels(
            arabicCard, background, 0, MARK_BAND_TOP, MARK_BAND_WIDTH, MARK_BAND_BOTTOM,
        )
        val arabicRight = QuoteCardFlow.nonBackgroundPixels(
            arabicCard, background, CARD_SIZE - MARK_BAND_WIDTH, MARK_BAND_TOP, CARD_SIZE, MARK_BAND_BOTTOM,
        )
        val mixedLeft = QuoteCardFlow.nonBackgroundPixels(
            mixedCard, background, 0, MARK_BAND_TOP, MARK_BAND_WIDTH, MARK_BAND_BOTTOM,
        )
        val mixedRight = QuoteCardFlow.nonBackgroundPixels(
            mixedCard, background, CARD_SIZE - MARK_BAND_WIDTH, MARK_BAND_TOP, CARD_SIZE, MARK_BAND_BOTTOM,
        )

        assertTrue(
            "an Arabic quote must draw its decorative mark in the right band of the produced file, that band " +
                "holds $arabicRight non background pixels against $arabicLeft on the left",
            arabicRight > MARK_PIXEL_FLOOR,
        )
        assertEquals(
            "an Arabic card must carry no mark on the left, a mark on both sides means the direction was never " +
                "resolved",
            0,
            arabicLeft,
        )
        assertTrue(
            "a quote whose first strong character is Latin must draw its mark in the left band even when it " +
                "carries an Arabic word, that band holds $mixedLeft non background pixels against $mixedRight " +
                "on the right",
            mixedLeft > MARK_PIXEL_FLOOR,
        )
        assertEquals(
            "a mostly English card must carry no mark on the right",
            0,
            mixedRight,
        )
    }

    @Test
    @TestId("ART-50")
    fun aVeryLongQuoteIsShortenedAndStaysInsideTheCard() {
        IntentStubs.stubChooser()
        val document = copyFixtureToDownloads(ArtifactFixtures.SMALL, LONG_NAME)
        val longQuote = longQuote()
        val background = QuoteCardTheme.LIGHT.backgroundColor

        assertTrue(
            "the fixture quote must be well past the ${QuoteImageRenderer.MAX_QUOTE_CHARS} character limit for " +
                "the shortening path to run at all, it is ${longQuote.length} characters",
            longQuote.length > QuoteImageRenderer.MAX_QUOTE_CHARS,
        )

        openReader(document).use {
            val activity = reader.requireActivity()

            QuoteCardFlow.open(activity, longQuote, BOOK_NAME, AUTHOR)
            QuoteCardFlow.selectTheme(QuoteCardTheme.LIGHT)
            val noticeShown = QuoteCardFlow.isShortenedNoticeVisible()
            val shortenedFile = QuoteCardFlow.share()
            val shortened = QuoteCardFlow.decode(shortenedFile)
            shortenedFile.delete()
            QuoteCardFlow.awaitGone()

            assertTrue(
                "the dialog must tell the user the quote was shortened before they send it",
                noticeShown,
            )
            assertEquals("the shortened card must still be $CARD_SIZE wide", CARD_SIZE, shortened.width)
            assertEquals("the shortened card must still be $CARD_SIZE tall", CARD_SIZE, shortened.height)
            assertEquals(
                "the band between the bottom of the quote area and the book name baseline must hold no pixel " +
                    "at all, any pixel there is the quote running out of its area instead of being shortened",
                0,
                QuoteCardFlow.nonBackgroundPixels(
                    shortened, background, 0, QUOTE_AREA_BOTTOM, CARD_SIZE, BOOK_NAME_TOP,
                ),
            )
            assertEquals(
                "the band above the decorative mark must hold no pixel at all, any pixel there is the quote " +
                    "running off the top of the card",
                0,
                QuoteCardFlow.nonBackgroundPixels(shortened, background, 0, 0, CARD_SIZE, TOP_BAND_BOTTOM),
            )

            QuoteCardFlow.open(activity, QuoteCardFlow.shortenedForm(longQuote), BOOK_NAME, AUTHOR)
            QuoteCardFlow.selectTheme(QuoteCardTheme.LIGHT)
            val controlFile = QuoteCardFlow.share()
            val control = QuoteCardFlow.decode(controlFile)
            controlFile.delete()
            QuoteCardFlow.awaitGone()

            assertTrue(
                "the control quote must already end with the ellipsis, otherwise the comparison below proves " +
                    "nothing about what the app wrote",
                QuoteCardFlow.shortenedForm(longQuote).endsWith(QuoteCardFlow.ELLIPSIS),
            )
            assertEquals(
                "the card the app produced from the long quote must be the same image as a card rendered from " +
                    "the first ${QuoteImageRenderer.MAX_QUOTE_CHARS} characters followed by an ellipsis, which " +
                    "is what proves the rendered text really was cut and marked rather than merely clipped",
                0,
                QuoteCardFlow.differingPixels(shortened, control),
            )
        }
    }

    private fun renderAndDecode(quote: String): Bitmap {
        val activity = reader.requireActivity()
        QuoteCardFlow.open(activity, quote, BOOK_NAME, AUTHOR)
        QuoteCardFlow.selectTheme(QuoteCardTheme.LIGHT)
        val card = QuoteCardFlow.share()
        val bitmap = QuoteCardFlow.decode(card)
        deleteQuietly(card)
        QuoteCardFlow.awaitGone()
        return bitmap
    }

    private fun deleteQuietly(file: File) {
        runCatching { file.delete() }
    }

    private fun longQuote(): String {
        val builder = StringBuilder()
        while (builder.length < MINIMUM_LONG_QUOTE) {
            builder.append(LONG_QUOTE_SENTENCE).append(' ')
        }
        return builder.substring(0, MINIMUM_LONG_QUOTE)
    }

    private companion object {
        const val CARD_NAME = "art47-quote-card.pdf"
        const val THEME_NAME = "art48-quote-themes.pdf"
        const val ARABIC_NAME = "art49-quote-arabic.pdf"
        const val MIXED_NAME = "art49-quote-mixed.pdf"
        const val LONG_NAME = "art50-quote-long.pdf"

        const val CARD_SIZE = QuoteCardFlow.CARD_SIZE
        const val CORNER_SAMPLE_X = 4
        const val CORNER_SAMPLE_Y = 1076
        const val MARK_BAND_TOP = 100
        const val MARK_BAND_BOTTOM = 300
        const val MARK_BAND_WIDTH = 200
        const val MARK_PIXEL_FLOOR = 500
        const val QUOTE_AREA_BOTTOM = 806
        const val BOOK_NAME_TOP = 865
        const val TOP_BAND_BOTTOM = 60
        const val MINIMUM_LONG_QUOTE = 2000

        const val BOOK_NAME = "The Memory Model"
        const val AUTHOR = "A. Writer"
        const val SHORT_QUOTE = "A short quote that fits the card at its starting text size."
        const val ARABIC_QUOTE = "الكتاب الذي بين يديك ليس كتابا عاديا بل هو رحلة طويلة في الذاكرة."
        const val MOSTLY_ENGLISH_QUOTE = "The word كتاب means book, and the rest of this sentence is English."
        const val LONG_QUOTE_SENTENCE =
            "The archive keeps every draft of every page, and the reader who opens it years later finds the " +
                "same sentences waiting in the same order."
    }
}
