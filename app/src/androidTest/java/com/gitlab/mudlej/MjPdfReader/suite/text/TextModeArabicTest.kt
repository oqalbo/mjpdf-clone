// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeArabicTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val text = TextModeRobot()

    @Test
    @TestId("TXT-40")
    fun arabicPresentationFormsAndMarksComeOutNormalized() {
        TextFixtures.launchReader(TextFixtures.ARABIC_RTL).use {
            text.enterFromReader()
            text.goToPage(TextFixtures.ARABIC_SHAPING_PAGE + 1)
            val page = text.pageText(TextFixtures.ARABIC_SHAPING_PAGE)

            page.forEachIndexed { index, character ->
                val code = character.code
                assertFalse(
                    "character $index is U+${code.toString(RADIX).uppercase()}, a presentation form that " +
                        "normalization must have mapped back to a bare Arabic letter",
                    code in PRESENTATION_FORMS_A || code in PRESENTATION_FORMS_B,
                )
                assertFalse(
                    "character $index is U+${code.toString(RADIX).uppercase()}, which must never reach the " +
                        "reflowed view",
                    code == NON_CHARACTER || code == ZERO_WIDTH_SPACE,
                )
            }

            page.forEachIndexed { index, character ->
                if (!isCombiningMark(character.code)) return@forEachIndexed
                assertTrue(
                    "the combining mark at $index has no base letter before it, the page around it reads: " +
                        CapturedReflow.literal(
                            page.substring(
                                (index - MARK_CONTEXT).coerceAtLeast(0),
                                (index + MARK_CONTEXT).coerceAtMost(page.length),
                            )
                        ),
                    index > 0 && isArabicLetter(page[index - 1].code),
                )
            }

            val vocalised = CapturedReflow.require("ARABIC_VOCALISED_WORD", CapturedReflow.ARABIC_VOCALISED_WORD)
            assertTrue(
                "the vocalised word must appear code point for code point, the page reads: " +
                    CapturedReflow.literal(page),
                page.contains(vocalised),
            )

            text.finish()
        }
    }

    private fun isCombiningMark(code: Int): Boolean = code in HARAKAT || code == SUPERSCRIPT_ALEF

    private fun isArabicLetter(code: Int): Boolean = code in ARABIC_LETTERS || code in ARABIC_EXTRA_LETTERS

    private companion object {
        const val PARAGRAPH_SEPARATOR = "\n\n"
        const val MARK_CONTEXT = 24
        const val RADIX = 16
        const val NON_CHARACTER = 0xFFFE
        const val ZERO_WIDTH_SPACE = 0x200B
        const val SUPERSCRIPT_ALEF = 0x0670
        val PRESENTATION_FORMS_A = 0xFB50..0xFDFF
        val PRESENTATION_FORMS_B = 0xFE70..0xFEFF
        val HARAKAT = 0x064B..0x0652
        val ARABIC_LETTERS = 0x0621..0x064A
        val ARABIC_EXTRA_LETTERS = 0x066E..0x06D3
    }
}
