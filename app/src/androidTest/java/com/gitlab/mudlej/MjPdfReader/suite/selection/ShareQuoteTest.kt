// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.view.View
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.ui.reader.share.QuoteImageRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ShareQuoteTest : SelectionTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @Test
    @TestId("SEL-47")
    @TestDuration(DurationCategory.SHORT)
    fun shareQuoteOpensWithTheDocumentTitleAndAuthorFilledIn() {
        openLatin { _, page ->
            selectQuickBrownFox(page)

            SelectionCard.tap(R.id.textSelectionShareButton)
            ShareQuoteRobot.awaitShown()

            assertEquals(
                "the book name must come from the document metadata title",
                SelectionFixtures.LATIN_TITLE,
                ShareQuoteRobot.bookName(),
            )
            assertEquals(
                "the author must come from the document metadata author",
                SelectionFixtures.LATIN_AUTHOR,
                ShareQuoteRobot.author(),
            )
            assertEquals(
                "the preview must render a square card",
                SelectionShare.CARD_SIZE to SelectionShare.CARD_SIZE,
                ShareQuoteRobot.previewSize(),
            )
            assertEquals(
                "a short quote must not be reported as shortened",
                View.GONE,
                ShareQuoteRobot.visibilityOf(R.id.shortenedNotice),
            )
            assertEquals(
                "the customize section must start collapsed",
                View.GONE,
                ShareQuoteRobot.visibilityOf(R.id.customizeSection),
            )

            ShareQuoteRobot.expandCustomize()
            assertEquals(
                "tapping the customize header must open the section",
                View.VISIBLE,
                ShareQuoteRobot.visibilityOf(R.id.customizeSection),
            )
        }
    }

    @Test
    @TestId("SEL-48")
    @TestDuration(DurationCategory.SHORT)
    fun sharingWritesASquarePngAndOffersItToTheShareSheet() {
        IntentStubs.stubChooser()

        openLatin { _, page ->
            assertTrue("the share cache must start empty", SelectionShare.files().isEmpty())

            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionShareButton)
            ShareQuoteRobot.awaitShown()
            ShareQuoteRobot.share()

            val produced = SelectionShare.awaitFileCount(1).single()
            assertTrue(
                "the produced file must be named like a quote card, it is called ${produced.name}",
                produced.name.matches(Regex(SelectionShare.NAME_PATTERN)),
            )
            assertTrue("the produced file must be a png", SelectionShare.hasPngSignature(produced))
            assertEquals(
                "the produced card must be square at the card size",
                SelectionShare.CARD_SIZE to SelectionShare.CARD_SIZE,
                SelectionShare.boundsOf(produced),
            )

            val chooser = SelectionIntents.awaitSingle(Intent.ACTION_CHOOSER)
            val wrapped = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                ?: throw AssertionError("The chooser carries no wrapped intent")
            assertEquals("the chooser must wrap a send", Intent.ACTION_SEND, wrapped.action)
            assertEquals("the shared item must be offered as an image", "image/*", wrapped.type)
            val stream = wrapped.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                ?: throw AssertionError("The send intent carries no stream uri")
            assertEquals(
                "the image must be handed over through the app's file provider",
                "${TestEnvironment.targetPackage}.fileprovider",
                stream.authority,
            )
        }
    }

    @Test
    @TestId("SEL-49")
    @TestDuration(DurationCategory.LONG)
    fun everyQuoteCardThemeRendersItsOwnCard() {
        IntentStubs.stubChooser()

        openLatin { _, page ->
            val lineIndex = page.lineIndexContaining("quick brown fox")
            val produced = mutableMapOf<Int, File>()
            var switchStates: Pair<Boolean, Boolean>? = null

            ShareQuoteRobot.THEME_CHIPS.forEachIndexed { index, chip ->
                SelectionShare.clearCache()
                selectWholeLine(page, lineIndex)
                SelectionCard.tap(R.id.textSelectionShareButton)
                ShareQuoteRobot.awaitShown()
                ShareQuoteRobot.expandCustomize()
                val states = ShareQuoteRobot.isChecked(R.id.madeBySwitch) to
                    ShareQuoteRobot.isChecked(R.id.reflowSwitch)
                if (switchStates == null) {
                    switchStates = states
                } else {
                    assertEquals(
                        "every theme must be shared with the same made by and reflow states, so that any " +
                            "difference between the cards comes from the theme alone",
                        switchStates,
                        states,
                    )
                }
                ShareQuoteRobot.chooseTheme(chip)
                ShareQuoteRobot.share()

                val file = SelectionShare.awaitFileCount(1).single()
                val kept = File(TestEnvironment.targetContext.cacheDir, "theme-$index.png")
                file.copyTo(kept, overwrite = true)
                produced[chip] = kept
                clearSelectionForNextQuote()
            }

            produced.values.forEach { file ->
                assertEquals(
                    "every theme must render a square card",
                    SelectionShare.CARD_SIZE to SelectionShare.CARD_SIZE,
                    SelectionShare.boundsOf(file),
                )
            }

            val files = produced.entries.toList()
            files.indices.forEach { first ->
                (first + 1 until files.size).forEach { second ->
                    val difference = SelectionShare.differingFraction(files[first].value, files[second].value)
                    assertTrue(
                        "the ${SelectionViews.name(files[first].key)} and ${SelectionViews.name(files[second].key)} " +
                            "cards must not be the same image, they differ in only $difference of their pixels",
                        difference > SelectionShare.DIFFERENCE_THRESHOLD,
                    )
                }
            }
            produced.values.forEach { it.delete() }
        }
    }

    @Test
    @TestId("SEL-50")
    fun aMostlyEnglishQuoteStaysLeftToRight() {
        IntentStubs.stubChooser()

        openLatin { _, page ->
            val mixedLine = page.lineIndexContaining("means the book in Arabic")
            selectWholeLine(page, mixedLine)
            SelectionCard.tap(R.id.textSelectionShareButton)
            ShareQuoteRobot.awaitShown()
            ShareQuoteRobot.share()

            val card = SelectionShare.awaitFileCount(1).single()
            assertTrue(
                "a mostly English quote must keep its opening mark on the left",
                SelectionShare.hasInk(card, SelectionShare.TOP_LEFT_DECOR),
            )
            assertTrue(
                "the mirrored top right corner must stay background coloured",
                SelectionShare.isUniformBackground(card, SelectionShare.TOP_RIGHT_DECOR),
            )
        }
    }

    @Test
    @TestId("SEL-51")
    fun anArabicQuoteRendersRightToLeft() {
        IntentStubs.stubChooser()

        openArabic { _, page ->
            selectWholeLine(page, 0)
            SelectionCard.tap(R.id.textSelectionShareButton)
            ShareQuoteRobot.awaitShown()
            ShareQuoteRobot.share()

            val card = SelectionShare.awaitFileCount(1).single()
            assertTrue(
                "an Arabic quote must put its decoration on the right",
                SelectionShare.hasInk(card, SelectionShare.TOP_RIGHT_DECOR),
            )
            assertTrue(
                "the mirrored top left corner must stay background coloured",
                SelectionShare.isUniformBackground(card, SelectionShare.TOP_LEFT_DECOR),
            )
        }
    }

    @Test
    @TestId("SEL-52")
    fun aQuoteOverTheLengthCapIsShortenedWithANotice() {
        IntentStubs.stubChooser()

        openReader(SelectionFixtures.LONG_QUOTE) { _, file ->
            val clipboardBefore = clipboardText()
            val page = pageTextOf(file)
            driver.longPressRange(page, page.rangeOfWordOnLine(0, 0))
            SelectionCard.awaitShown()

            val projection = driver.projection(page.pageIndex)
            val lastLine = page.lastTextLineIndex()
            val target = page.wordRanges(lastLine).last()
            driver.dragCaretTo(SelectionHandle.END, SelectionTargets.caretJustAfter(page, target, projection))

            val selected = driver.selectedRange()
            assertTrue(
                "the drag must select more than ${MINIMUM_LONG_SELECTION} characters to exercise the cap, it " +
                    "selected ${selected.count()}",
                selected.count() >= MINIMUM_LONG_SELECTION,
            )
            assertTrue(
                "the selection must be longer than the ${QuoteImageRenderer.MAX_QUOTE_CHARS} character cap",
                page.normalized(selected).length > QuoteImageRenderer.MAX_QUOTE_CHARS,
            )

            SelectionCard.tap(R.id.textSelectionShareButton)
            ShareQuoteRobot.awaitShown()

            assertEquals(
                "a quote past the cap must be reported as shortened",
                View.VISIBLE,
                ShareQuoteRobot.visibilityOf(R.id.shortenedNotice),
            )

            ShareQuoteRobot.share()

            val card = SelectionShare.awaitFileCount(1).single()
            assertEquals(
                "the shortened card must still be square",
                SelectionShare.CARD_SIZE to SelectionShare.CARD_SIZE,
                SelectionShare.boundsOf(card),
            )
            assertEquals("sharing must not touch the clipboard", clipboardBefore, clipboardText())
        }
    }

    @Test
    @TestId("SEL-53")
    fun reflowAndMadeByTogglesChangeTheRenderedCard() {
        IntentStubs.stubChooser()

        openLatin { _, page ->
            val firstLine = page.lineIndexContaining("The date they settled")
            val defaults = shareThreeLineQuote(page, firstLine, null)
            val reflowed = shareThreeLineQuote(page, firstLine, R.id.reflowSwitch)
            val madeBy = shareThreeLineQuote(page, firstLine, R.id.madeBySwitch)

            listOf(defaults to reflowed, defaults to madeBy, reflowed to madeBy).forEach { (first, second) ->
                val difference = SelectionShare.differingFraction(first, second)
                assertTrue(
                    "${first.name} and ${second.name} must be different images, they differ in only $difference " +
                        "of their pixels",
                    difference > SelectionShare.DIFFERENCE_THRESHOLD,
                )
            }

            val outside = SelectionShare.differingFractionOutside(defaults, madeBy, SelectionShare.MADE_BY_CORNER)
            assertEquals(
                "flipping made by must change nothing outside the bottom right corner, but $outside of the pixels " +
                    "outside ${SelectionShare.MADE_BY_CORNER} differ",
                0f,
                outside,
                0f,
            )
            listOf(defaults, reflowed, madeBy).forEach { it.delete() }
        }
    }

    @Test
    @TestId("SEL-54")
    @TestDuration(DurationCategory.SHORT)
    fun oldShareCacheFilesAreCleanedUp() {
        IntentStubs.stubChooser()

        val directory = SelectionShare.directory().apply { mkdirs() }
        val old = File(directory, "MJ_quote_old.png").apply { writeBytes(ByteArray(16)) }
        val recent = File(directory, "MJ_quote_recent.png").apply { writeBytes(ByteArray(16)) }
        val now = System.currentTimeMillis()
        old.setLastModified(now - OLD_FILE_AGE_MS)
        recent.setLastModified(now - RECENT_FILE_AGE_MS)

        openLatin { _, page ->
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionShareButton)
            ShareQuoteRobot.awaitShown()
            ShareQuoteRobot.share()

            SelectionShare.awaitFileCount(2)
            Await.until("the file older than a day is deleted") { !old.exists() }
            assertTrue("the file from an hour ago must be kept", recent.exists())
            assertEquals(
                "exactly one new quote card must have been written",
                1,
                SelectionShare.pngFiles().count { it.name.matches(Regex(SelectionShare.NAME_PATTERN)) },
            )
        }
    }

    private fun shareThreeLineQuote(page: SelectionPageText, firstLine: Int, toggle: Int?): File {
        SelectionShare.clearCache()
        driver.longPressRange(page, page.rangeOfWordOnLine(firstLine, 0))
        SelectionCard.awaitShown()
        val projection = driver.projection(page.pageIndex)
        driver.dragCaretTo(SelectionHandle.END, SelectionTargets.centreOfLine(page, firstLine + 2, projection))
        assertTrue(
            "the quote must span three lines so that reflow has line breaks to remove",
            page.normalized(driver.selectedRange()).count { it == '\n' } == 2,
        )

        SelectionCard.tap(R.id.textSelectionShareButton)
        ShareQuoteRobot.awaitShown()
        if (toggle != null) {
            ShareQuoteRobot.expandCustomize()
            ShareQuoteRobot.toggle(toggle)
        }
        ShareQuoteRobot.share()

        val produced = SelectionShare.awaitFileCount(1).single()
        val name = if (toggle == null) "default" else SelectionViews.name(toggle)
        val kept = File(TestEnvironment.targetContext.cacheDir, "quote-$name.png")
        produced.copyTo(kept, overwrite = true)
        clearSelectionForNextQuote()
        return kept
    }

    private fun clearSelectionForNextQuote() {
        if (state().hasSelection) {
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionDiscardButton)
            SelectionCard.awaitGone()
        }
        reader.awaitNoSelection()
    }

    private fun selectQuickBrownFox(page: SelectionPageText) {
        driver.longPressWord(page, "quick")
        SelectionCard.awaitShown()
        val projection = driver.projection(page.pageIndex)
        driver.dragCaretTo(
            SelectionHandle.END,
            SelectionTargets.caretJustAfter(page, page.rangeOf("fox"), projection),
        )
    }

    private fun selectWholeLine(page: SelectionPageText, lineIndex: Int) {
        val words = page.wordRanges(lineIndex)
        driver.longPressRange(page, words.first())
        SelectionCard.awaitShown()
        val projection = driver.projection(page.pageIndex)
        val target = if (isRightToLeft(page, lineIndex)) {
            SelectionTargets.caretJustAfterInRtl(page, words.last(), projection)
        } else {
            SelectionTargets.caretJustAfter(page, words.last(), projection)
        }
        driver.dragCaretTo(SelectionHandle.END, target)
    }

    private fun isRightToLeft(page: SelectionPageText, lineIndex: Int): Boolean {
        val range = page.lineRange(lineIndex)
        val first = page.charBoxOf(range.first)
        val last = page.charBoxOf(range.last)
        return last.left < first.left
    }

    private companion object {
        const val OLD_FILE_AGE_MS = 25L * 60 * 60 * 1000
        const val RECENT_FILE_AGE_MS = 60L * 60 * 1000
        const val MINIMUM_LONG_SELECTION = 600
    }
}
