// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.localization.LocaleRule
import com.gitlab.mudlej.MjPdfReader.suite.robot.LocaleRobot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ArabicExportTest : ListsTestCase() {

    @get:Rule
    val appLocale = LocaleRule()

    @Test
    @TestId("LIST-36")
    fun arabicQuotesAndAnArabicLocaleSurviveTheExportRoundTrip() {
        val name = exports.claim(
            "${ListsFixtures.ARABIC} - ${arabicString(R.string.user_highlights_title)}.txt"
        )
        appLocale.set(LocaleRobot.ARABIC_EGYPT)

        openReader(ListsFixtures.ARABIC) { _, _ ->
            val list = openHighlightsAndScan()
            list.awaitItemCount(ListsFixtures.arabicQuotes.size)

            list.export()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(arabicString(R.string.export_done))

            val exported = ListExport.awaitWritten(name)
            ListExport.requireNoVariantBeside(name)

            ListsFixtures.arabicQuotes.forEach { quote ->
                assertTrue(
                    "the exported file does not carry the quote \"$quote\" character for character",
                    exported.text.contains(quote),
                )
            }
            assertTrue(
                "the exported file carries the Arabic note",
                exported.text.contains(ListsFixtures.arabicNote),
            )
            assertFalse(
                "the exported bytes do not decode cleanly as UTF-8, they carry a replacement character",
                exported.text.contains(REPLACEMENT_CHARACTER),
            )

            val arabicQuoteLabel = arabicString(R.string.export_quote_label)
            val arabicNoteLabel = arabicString(R.string.note)
            assertTrue(
                "the quote label is not the Arabic string, the file reads:\n${exported.text}",
                exported.lines.contains("--- $arabicQuoteLabel Start") &&
                    exported.lines.contains("--- $arabicQuoteLabel End"),
            )
            assertTrue(
                "the note label is not the Arabic string, the file reads:\n${exported.text}",
                exported.lines.contains("--- $arabicNoteLabel Start") &&
                    exported.lines.contains("--- $arabicNoteLabel End"),
            )
            assertFalse(
                "the English quote label survived into an Arabic export",
                exported.lines.contains("--- Quote Start"),
            )

            assertEquals(
                "one separator line per Arabic highlight group",
                ListsFixtures.arabicQuotes.size,
                exported.separatorLineCount(),
            )
        }
    }

    private fun arabicString(resId: Int): String = arabicContext().getString(resId)

    private fun arabicContext(): Context {
        val base = TestEnvironment.targetContext
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale(ARABIC_LANGUAGE))
        return base.createConfigurationContext(configuration)
    }

    private companion object {
        const val ARABIC_LANGUAGE = "ar"
        const val REPLACEMENT_CHARACTER = "�"
    }
}
