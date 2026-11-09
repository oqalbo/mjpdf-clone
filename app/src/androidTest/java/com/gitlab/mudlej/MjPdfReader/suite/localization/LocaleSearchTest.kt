// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.SearchIdle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchBar
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchFixtures
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchOracle
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchResultsScreen
import com.gitlab.mudlej.MjPdfReader.suite.search.SearchRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import com.gitlab.mudlej.MjPdfReader.ui.search.SearchSessionCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class LocaleSearchTest : LocaleTestCase() {

    private val search = SearchRobot(reader)
    private val results = SearchResultsScreen()

    @After
    fun releaseSearchState() {
        runCatching { SearchIdle.clear() }
    }

    @Test
    @TestId("LOC-09")
    fun theSearchResultPageLabelIsTranslatedUnderRussian() {
        useLocale(RUSSIAN)
        translated(R.string.search_result_page_label)
        val expectedLabels = SearchFixtures.BEACON_PAGE_NUMBERS
            .map { pageNumber -> localized(R.string.search_result_page_label, pageNumber) }
            .toSet()

        openSearchable(SearchFixtures.TEXT) {
            search.search(SearchFixtures.BEACON)
            SearchBar.awaitResultsLabel()
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitSearchFinished()
            val rows = results.awaitAtLeastOneRow()

            results.awaitRowBound(0)
            val label = results.pageLabel(0)
            assertNotNull("the first result row must carry a page label", label)
            assertTrue(
                "the first result row must carry the Russian page label for one of the beacon pages " +
                    "$expectedLabels. It read \"$label\".",
                label in expectedLabels,
            )
            assertFalse(
                "the page label must not read the English \"$ENGLISH_PAGE_LABEL\". It read \"$label\".",
                label!!.startsWith(ENGLISH_PAGE_LABEL),
            )

            val title = results.title()
            val russianTail = translated(R.string.search_results)
            assertTrue(
                "SearchActivity builds the title as the grouped count then a space then the localized " +
                    "search_results, so the Russian value can only be the tail. The title read \"$title\".",
                title.endsWith(russianTail),
            )
            assertEquals(
                "the count prefix must parse back to the number of rows, read through Character.digit so " +
                    "the Russian non breaking grouping separator does not matter. The title read \"$title\".",
                rows,
                results.awaitTitleCount(),
            )
        }
    }

    @Test
    @TestId("LOC-37")
    @TestDuration(DurationCategory.SHORT)
    fun theReaderSearchAcceptsPersianDigitsAndFindsThem() {
        useLocale(PERSIAN)

        openSearchable(LocaleFixtures.PERSIAN_DIGITS) {
            val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)

            search.openSearchDialog()
            search.typeQuery(LocaleFixtures.PERSIAN_DIGIT_STRING)
            assertEquals(
                "the search field installs no DigitsKeyListener, unlike Go To Page, so every Persian digit " +
                    "must survive typing",
                LocaleFixtures.PERSIAN_DIGIT_STRING,
                search.queryFieldText(),
            )

            search.submit()
            search.awaitSearchDialogGone()
            SearchIdle.awaitFinished()

            val hits = SearchOracle.awaitHits(fileHash, LocaleFixtures.PERSIAN_DIGIT_STRING)
            assertTrue(
                "a Persian digit run written in the document must be findable by typing the same digits, " +
                    "but the search reported ${hits.size} hits",
                hits.isNotEmpty(),
            )

            val counter = SearchBar.awaitCounter()
            assertTrue("the search bar must report at least one match, it read $counter", counter.total >= 1)

            val expectedPage = SearchOracle.pageIndexesOf(hits).first()
            reader.awaitPageIndex(expectedPage, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("LOC-50")
    fun cyrillicCaseInsensitiveSearchFindsBothMatchesWithIgnoreAccentsOff() {
        useLocale(RUSSIAN)
        assertEquals(
            "this test pins the default path, so the preference must be left at its false default",
            false,
            TestEnvironment.defaultPreferences.getBoolean(Preferences.searchIgnoreAccentsKey, false),
        )

        openSearchable(LocaleFixtures.CYRILLIC_CASE) {
            search.search(LocaleFixtures.CYRILLIC_LOWERCASE)
            SearchBar.awaitShown()
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitSearchFinished()
            results.awaitItemCount(2)

            val snippets = listOf(results.awaitRowText(0), results.awaitRowText(1))
            assertTrue(
                "Android backs java.util.regex with ICU, where Pattern.CASE_INSENSITIVE folds the whole " +
                    "of Unicode rather than ASCII alone, so the lowercase query matches the capitalised " +
                    "spelling too even with Ignore accent letters off. The rows read $snippets.",
                snippets.any { it.contains(LocaleFixtures.CYRILLIC_CAPITALISED) },
            )
            assertTrue(
                "the lowercase spelling must be found as well, the rows read $snippets",
                snippets.any {
                    it.contains(LocaleFixtures.CYRILLIC_LOWERCASE) &&
                        !it.contains(LocaleFixtures.CYRILLIC_CAPITALISED)
                },
            )
        }
    }

    @Test
    @TestId("LOC-51")
    @TestDuration(DurationCategory.SHORT)
    fun cyrillicCaseInsensitiveSearchFindsBothMatchesWithIgnoreAccentsOn() {
        useLocale(RUSSIAN)
        SettingsSeeder.seed(Preferences.searchIgnoreAccentsKey, true)

        openSearchable(LocaleFixtures.CYRILLIC_CASE) {
            search.search(LocaleFixtures.CYRILLIC_LOWERCASE)
            SearchBar.awaitShown()
            SearchBar.tapLabel()
            results.awaitShown()
            results.awaitSearchFinished()
            results.awaitItemCount(2)

            val snippets = listOf(results.awaitRowText(0), results.awaitRowText(1))
            assertTrue(
                "the folding path NFKD normalizes then lowercases, so Cyrillic case differences do match " +
                    "and both spellings must appear. The rows read $snippets.",
                snippets.any { it.contains(LocaleFixtures.CYRILLIC_CAPITALISED) },
            )
            assertTrue(
                "the lowercase spelling must be the other row. The rows read $snippets.",
                snippets.any { it.contains(LocaleFixtures.CYRILLIC_LOWERCASE) },
            )
        }
    }

    @Test
    @TestId("LOC-52")
    fun turkishAndEnglishLocalesFindTheSameDottedAndDotlessIMatches() {
        SettingsSeeder.seed(Preferences.searchIgnoreAccentsKey, true)

        val turkish = measureTurkishCaseCounts(TURKISH, TURKISH_LANGUAGE)
        forgetCachedSearchSessions(turkish.fileHash)
        val english = measureTurkishCaseCounts(LocalizedStrings.ENGLISH_TAG, ENGLISH_LANGUAGE)

        assertEquals(
            "all case folding in the app is Kotlin's no argument lowercase(), which is Locale.ROOT, and " +
                "ignoreCase is locale independent, so the Turkish dotted and dotless I cannot change what a " +
                "search finds. Under $TURKISH the two queries found ${turkish.counts} and under " +
                "${LocalizedStrings.ENGLISH_TAG} they found ${english.counts}. Both locales are measured in " +
                "this one process, because the Orchestrator clears package data between tests and a count " +
                "recorded by a sibling test can never reach this one.",
            turkish.counts,
            english.counts,
        )
    }

    private fun measureTurkishCaseCounts(tag: String, language: String): TurkishCaseRun {
        useLocale(tag)
        var measured: TurkishCaseRun? = null

        openSearchable(LocaleFixtures.TURKISH_CASE) {
            assertEquals(
                "the app process must be running under $tag before anything is searched, otherwise this half " +
                    "of the comparison measures the same locale twice",
                language,
                appLanguageInProcess(),
            )
            val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)
            val dotted = countFor(fileHash, LocaleFixtures.TURKISH_DOTTED_QUERY)
            val capitals = countFor(fileHash, LocaleFixtures.TURKISH_CAPITALS_QUERY)

            assertTrue(
                "all case folding in the app is Locale.ROOT, so a Turkish phone must still find " +
                    "\"${LocaleFixtures.TURKISH_DOTTED_QUERY}\" in the document. Under $tag it found $dotted.",
                dotted >= 1,
            )
            assertTrue(
                "the same holds for \"${LocaleFixtures.TURKISH_CAPITALS_QUERY}\". Under $tag it found " +
                    "$capitals.",
                capitals >= 1,
            )

            measured = TurkishCaseRun(fileHash, dotted to capitals)
        }

        return measured ?: throw AssertionError(
            "The reader closed under $tag before the two searches were counted, so there is nothing to " +
                "compare the other locale against"
        )
    }

    private fun countFor(fileHash: String, query: String): Int {
        assertNull(
            "SearchNavigationController.startQuery answers from SearchSessionCache without scanning when a " +
                "session is cached, and the cache is process wide, so a cached \"$query\" here would hand " +
                "this locale the other locale's hits and the comparison would prove nothing",
            SearchOracle.session(fileHash, query, ignoreAccents = true),
        )
        SearchIdle.clear()

        search.search(query)
        SearchBar.awaitShown()
        SearchIdle.awaitFinished()

        val progress = SearchIdle.progress()
        assertNotNull("the search for \"$query\" must have run rather than been answered from a cache", progress)
        assertEquals("the scan that finished must be the one this test asked for", query, progress!!.query)

        val hits = SearchOracle.awaitHits(fileHash, query, ignoreAccents = true)
        SearchBar.tapDone()
        SearchBar.awaitGone()
        return hits.size
    }

    private fun forgetCachedSearchSessions(fileHash: String) {
        repeat(EVICTING_PUTS) { slot ->
            SearchSessionCache.put("$EVICTION_HASH$slot", EVICTION_QUERY, false, emptyList())
        }
        listOf(LocaleFixtures.TURKISH_DOTTED_QUERY, LocaleFixtures.TURKISH_CAPITALS_QUERY).forEach { query ->
            assertNull(
                "\"$query\" survived $EVICTING_PUTS evicting puts into the process wide SearchSessionCache, " +
                    "so the cache now holds more than $EVICTING_PUTS sessions and this eviction has to be " +
                    "raised to match. Until it is, the second locale would be served the first locale's hits",
                SearchOracle.session(fileHash, query, ignoreAccents = true),
            )
        }
    }

    private data class TurkishCaseRun(val fileHash: String, val counts: Pair<Int, Int>)

    private fun openSearchable(name: String, body: (File) -> Unit) {
        val file = fixture(name)
        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(file)).use {
            awaitReaderReady()
            body(file)
        }
    }

    private companion object {
        const val ENGLISH_PAGE_LABEL = "PAGE"
        const val TURKISH_LANGUAGE = "tr"
        const val ENGLISH_LANGUAGE = "en"
        const val EVICTING_PUTS = 8
        const val EVICTION_HASH = "loc52-eviction-"
        const val EVICTION_QUERY = "eviction"
    }
}
