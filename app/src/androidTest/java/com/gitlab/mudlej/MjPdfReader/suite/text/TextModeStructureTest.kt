// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeStructureTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val text = TextModeRobot()

    @Test
    @TestId("TXT-11")
    fun detectHeadingsRendersTheHeadingBoldAndLarger() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)

            val withHeadings = text.pageSpanned(TextFixtures.STRUCTURED_HEADING_PAGE)
            val range = headingRange(withHeadings)
            val spanned = withHeadings as? Spanned
                ?: throw AssertionError("The page text is not a Spanned, so it carries no heading spans")

            val bold = spanned.getSpans(range.first, range.last, StyleSpan::class.java)
                .filter { spanned.getSpanStart(it) <= range.first && spanned.getSpanEnd(it) >= range.last }
            assertTrue(
                "the heading range must carry a bold StyleSpan, it carries ${describeSpans(spanned, range)}",
                bold.any { it.style == Typeface.BOLD },
            )

            val sizes = spanned.getSpans(range.first, range.last, RelativeSizeSpan::class.java)
                .filter { spanned.getSpanStart(it) <= range.first && spanned.getSpanEnd(it) >= range.last }
                .map { it.sizeChange }
            assertTrue(
                "the heading range must carry a heading RelativeSizeSpan, it carries $sizes",
                sizes.any { HEADING_SIZES.contains(it) },
            )

            text.openTypographySheet()
            text.setSheetSwitch(R.id.detectHeadingsSwitch, false)
            text.closeTypographySheet()

            val withoutHeadings = awaitReplacedPageText(TextFixtures.STRUCTURED_HEADING_PAGE, withHeadings)
            val plainRange = headingRange(withoutHeadings)
            val plain = withoutHeadings as? Spanned
            if (plain != null) {
                assertEquals(
                    "no StyleSpan may cover the heading once Detect Headings is off",
                    0,
                    plain.getSpans(plainRange.first, plainRange.last, StyleSpan::class.java)
                        .count { plain.getSpanStart(it) <= plainRange.first && plain.getSpanEnd(it) >= plainRange.last },
                )
                assertEquals(
                    "no RelativeSizeSpan may cover the heading once Detect Headings is off",
                    0,
                    plain.getSpans(plainRange.first, plainRange.last, RelativeSizeSpan::class.java)
                        .count { plain.getSpanStart(it) <= plainRange.first && plain.getSpanEnd(it) >= plainRange.last },
                )
            }
            assertTrue(
                "the plain text must still open with the heading, it reads: " +
                    CapturedReflow.literal(withoutHeadings.toString().take(HEADING_PREVIEW)),
                withoutHeadings.toString().trimStart().startsWith(TextFixtures.STRUCTURED_HEADING),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-12")
    fun aCodeBlockKeepsMonospaceAndIndentation() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)
            text.goToPage(TextFixtures.STRUCTURED_LISTS_PAGE + 1)

            val pageTwo = text.pageSpanned(TextFixtures.STRUCTURED_LISTS_PAGE)
            val plain = pageTwo.toString()
            val spanned = pageTwo as? Spanned
                ?: throw AssertionError("The page text is not a Spanned, so it carries no code block span")

            val first = TextFixtures.STRUCTURED_CODE_LINES.first()
            val last = TextFixtures.STRUCTURED_CODE_LINES.last()
            val start = plain.indexOf(first)
            val end = plain.indexOf(last)
            assertTrue(
                "page two must carry the code block, it reads: " + CapturedReflow.literal(plain),
                start >= 0 && end > start,
            )
            val blockEnd = end + last.length
            val block = plain.substring(start, blockEnd)

            val monospace = spanned.getSpans(start, blockEnd, TypefaceSpan::class.java)
                .filter { spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= blockEnd }
                .mapNotNull { it.family }
            assertTrue(
                "the code block must carry a monospace TypefaceSpan, it carries $monospace",
                monospace.contains(MONOSPACE_FAMILY),
            )

            assertEquals(
                "the four code lines must not be joined, the block reads: " + CapturedReflow.literal(block),
                TextFixtures.STRUCTURED_CODE_LINES.size - 1,
                block.count { it == '\n' },
            )

            val deepest = block.split("\n").firstOrNull { it.trim() == TextFixtures.STRUCTURED_DEEPEST_CODE_LINE }
                ?: throw AssertionError(
                    "the block carries no line reading \"${TextFixtures.STRUCTURED_DEEPEST_CODE_LINE}\", it reads: " +
                        CapturedReflow.literal(block)
                )
            assertTrue(
                "the deepest code line must begin with ${TextFixtures.STRUCTURED_CODE_INDENT_SPACES} spaces, it " +
                    "reads: " + CapturedReflow.literal(deepest),
                deepest.startsWith(" ".repeat(TextFixtures.STRUCTURED_CODE_INDENT_SPACES)) &&
                    !deepest.startsWith(" ".repeat(TextFixtures.STRUCTURED_CODE_INDENT_SPACES + 1)),
            )

            text.finish()
        }
    }

    @Test
    @TestId("TXT-13")
    fun listItemsKeepTheirOwnLines() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader(TextFixtures.STRUCTURED_HEADING_PAGE)
            text.goToPage(TextFixtures.STRUCTURED_LISTS_PAGE + 1)

            val pageTwo = text.pageText(TextFixtures.STRUCTURED_LISTS_PAGE)

            TextFixtures.STRUCTURED_BULLETS.forEach { bullet ->
                val at = pageTwo.indexOf(bullet)
                assertTrue(
                    "page two must carry the bullet item ${CapturedReflow.literal(bullet)}, it reads: " +
                        CapturedReflow.literal(pageTwo),
                    at >= 0,
                )
                assertTrue(
                    "the bullet item ${CapturedReflow.literal(bullet)} must start a line, the character before it " +
                        "is ${CapturedReflow.literal(pageTwo.substring((at - 1).coerceAtLeast(0), at))}",
                    at > 0 && pageTwo[at - 1] == '\n',
                )
            }

            TextFixtures.STRUCTURED_NUMBERED.forEach { item ->
                val at = pageTwo.indexOf(item)
                assertTrue(
                    "page two must carry the numbered item ${CapturedReflow.literal(item)}, it reads: " +
                        CapturedReflow.literal(pageTwo),
                    at >= 0,
                )
                assertTrue(
                    "the numbered item ${CapturedReflow.literal(item)} must start a line",
                    at > 0 && pageTwo[at - 1] == '\n',
                )
            }

            text.finish()
        }
    }

    private fun headingRange(page: CharSequence): IntRange {
        val plain = page.toString()
        val start = plain.indexOf(TextFixtures.STRUCTURED_HEADING)
        if (start < 0) {
            throw AssertionError(
                "page one carries no \"${TextFixtures.STRUCTURED_HEADING}\" heading, it reads: " +
                    CapturedReflow.literal(plain.take(HEADING_PREVIEW))
            )
        }
        return start..(start + TextFixtures.STRUCTURED_HEADING.length)
    }

    private fun describeSpans(spanned: Spanned, range: IntRange): String =
        spanned.getSpans(range.first, range.last, Any::class.java)
            .joinToString { "${it::class.java.simpleName}@${spanned.getSpanStart(it)}..${spanned.getSpanEnd(it)}" }

    private fun awaitReplacedPageText(position: Int, previous: CharSequence): CharSequence =
        Await.untilValue(
            condition = "the text mode item at position $position is re extracted",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { text.snapshot(position) },
        ) {
            it != null && it.pageTextVisible && !it.pageText.isNullOrBlank() && it.pageText !== previous
        }!!.pageText!!

    private companion object {
        val HEADING_SIZES = listOf(1.35f, 1.2f, 1.1f)
        const val MONOSPACE_FAMILY = "monospace"
        const val HEADING_PREVIEW = 120
    }
}
