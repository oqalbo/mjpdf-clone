// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.selection

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.HarnessHostile
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@HarnessHostile(
    bug = "BENCH-ACTION-HANDLERS",
    damage = "The bench resolves ACTION_PROCESS_TEXT to TalkBack, so the no-handler cases leave the app and " +
        "strand the instrumentation outside the state the assertions control",
)
@TestDuration(DurationCategory.SHORT)
class DictionaryLookupTest : SelectionTestCase() {

    @get:Rule
    val intents = IntentsRule()

    @Test
    @TestId("SEL-31")
    @TestDuration(DurationCategory.NORMAL)
    fun theDefinitionSheetAppearsForAnInstalledSingleWord() {
        SelectionDictionary.seed()

        openLatin { _, page ->
            driver.longPressWord(page, SelectionDictionary.FOX)
            SelectionCard.awaitShown()

            SelectionCard.tap(R.id.textSelectionTranslateButton)
            DictionarySheet.awaitShown()

            assertEquals("the sheet must be titled with the looked up word", SelectionDictionary.FOX, DictionarySheet.title())

            val senses = DictionarySheet.senses()
            assertEquals(
                "the sheet must hold one row per seeded sense",
                SelectionDictionary.FOX_SENSES.size,
                senses.size,
            )
            val noun = SelectionViews.string(R.string.dictionary_pos_noun)
            val verb = SelectionViews.string(R.string.dictionary_pos_verb)
            assertEquals(
                "the rows must run nouns before verbs, numbered inside each part of speech",
                listOf("$noun 1", "$noun 2", "$verb 1"),
                senses.map { it.label },
            )
            assertEquals(
                "the second noun row must show the seeded synonyms",
                SelectionViews.string(R.string.dictionary_synonyms, SelectionDictionary.FOX_SYNONYMS),
                senses[1].synonyms,
            )
            assertEquals(
                "the first noun row must show the seeded example",
                SelectionDictionary.FOX_EXAMPLE,
                senses.first().example,
            )

            SelectionIntents.assertNone(Intent.ACTION_PROCESS_TEXT)
            SelectionIntents.assertNone(Intent.ACTION_VIEW)
        }
    }

    @Test
    @TestId("SEL-32")
    fun translateInsteadHandsTheWordToTheTranslator() {
        SelectionDictionary.seed()
        seedWebTranslation(engine = "google")
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openLatin { _, page ->
            driver.longPressWord(page, SelectionDictionary.FOX)
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionTranslateButton)
            DictionarySheet.awaitShown()

            DictionarySheet.tapTranslateInstead()
            DictionarySheet.awaitGone()

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            assertTrue(
                "the translator url must carry the looked up word, it reads ${SelectionIntents.dataString(launched)}",
                SelectionIntents.dataString(launched).contains("text=${SelectionDictionary.FOX}"),
            )
            assertEquals(
                "the google engine must be the one launched",
                "translate.google.com",
                launched.data?.host,
            )
        }
    }

    @Test
    @TestId("SEL-33")
    fun aWordMissingFromTheDictionaryFallsStraightThroughToTheTranslator() {
        SelectionDictionary.seed()
        seedWebTranslation(engine = "google")
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openLatin { _, page ->
            driver.longPressWord(page, SelectionDictionary.ABSENT)
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            assertEquals(
                "the translator must receive the word that has no entry",
                SelectionDictionary.ABSENT,
                SelectionIntents.decodedQuery(launched, "text"),
            )
            DictionarySheet.assertNeverShown()
        }
    }

    @Test
    @TestId("SEL-34")
    fun defineSingleWordsOffSkipsTheDictionary() {
        SelectionDictionary.seed()
        SettingsSeeder.seed(Preferences.dictionaryDefineWordsKey, false)
        seedWebTranslation(engine = "google")
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openLatin { _, page ->
            driver.longPressWord(page, SelectionDictionary.FOX)
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            assertEquals(
                "every translate press must reach the translator when define single words is off",
                SelectionDictionary.FOX,
                SelectionIntents.decodedQuery(launched, "text"),
            )
            DictionarySheet.assertNeverShown()
        }
    }

    @Test
    @TestId("SEL-35")
    fun withNoDictionaryInstalledTranslateGoesToTheTranslator() {
        seedWebTranslation(engine = "google")
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openLatin { _, page ->
            assertFalse(
                "a clean install must carry no dictionary file at ${SelectionDictionary.file().absolutePath}",
                SelectionDictionary.file().exists(),
            )

            driver.longPressWord(page, SelectionDictionary.FOX)
            SelectionCard.awaitShown()
            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            assertEquals(
                "an absent dictionary must never block the translator",
                SelectionDictionary.FOX,
                SelectionIntents.decodedQuery(launched, "text"),
            )
            DictionarySheet.assertNeverShown()
        }
    }

    @Test
    @TestId("SEL-36")
    fun aMultiWordSelectionSkipsTheDictionary() {
        SelectionDictionary.seed()
        seedWebTranslation(engine = "google")
        IntentStubs.stubOkFor(Intent.ACTION_VIEW)

        openLatin { _, page ->
            driver.longPressWord(page, "brown")
            SelectionCard.awaitShown()
            val projection = driver.projection(page.pageIndex)
            driver.dragCaretTo(
                SelectionHandle.END,
                SelectionTargets.caretJustAfter(page, page.rangeOf("fox"), projection),
            )

            SelectionCard.tap(R.id.textSelectionTranslateButton)

            val launched = SelectionIntents.awaitSingle(Intent.ACTION_VIEW)
            assertEquals(
                "a phrase must go to the translator whole",
                "brown fox",
                SelectionIntents.decodedQuery(launched, "text"),
            )
            DictionarySheet.assertNeverShown()
        }
    }
}
