// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.LayoutDirection
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class LiveLocaleChangeTest : LocaleTestCase() {

    @Test
    @TestId("LOC-48")
    fun changingTheLocaleWhileTheReaderIsOpenKeepsTheReadingPosition() {
        val scenario = openReaderScenario(LocaleFixtures.NORMAL)
        scenario.first.use {
            val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)

            LocaleGoToPage.open()
            LocaleGoToPage.type(TARGET_PAGE.toString())
            LocaleGoToPage.tapGo()
            reader.awaitPageIndex(TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)
            reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)

            locale.setLive(ARABIC)

            awaitReaderReady()
            reader.awaitPageIndex(TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)

            assertEquals(
                "MainActivity declares no locale or layoutDirection in its configChanges, so the locale " +
                    "change recreates it and the recreated reader must mirror",
                LayoutDirection.RTL,
                ScreenDirection.require(MainActivity::class.java, Await.LONG_TIMEOUT_MS),
            )

            LocaleActions.openActionsDialog()
            listOf(R.string.search, R.string.bookmarks, R.string.file_metadata).forEach { resId ->
                val label = translated(resId)
                assertTrue(
                    "the recreated reader menu must be Arabic, but no tile was described \"$label\"",
                    TestEnvironment.uiDevice.wait(Until.hasObject(By.desc(label)), Await.LONG_TIMEOUT_MS) == true,
                )
            }
            TestEnvironment.uiDevice.pressBack()

            Records.awaitStoredPage(fileHash, TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("LOC-49")
    @TestDuration(DurationCategory.SHORT)
    fun changingTheLocaleWhileHomeIsOpenRebuildsEveryRow() {
        SettingsSeeder.seed(Preferences.homeViewModeKey, HomeViewMode.LIST.name)

        val now = LocalDateTime.now()
        val agedTitle = LocaleRecords.titleFor(1)
        val agedOpened = now.minusDays(AGED_DAYS)
        LocaleRecords.seed(agedTitle, PAGE_COUNT, agedOpened)
        LocaleRecords.seed(LocaleRecords.titleFor(2), PAGE_COUNT, now)

        val weeks = (ChronoUnit.DAYS.between(agedOpened.toLocalDate(), now.toLocalDate()) / WEEK_DAYS).toInt()
        val englishRelative = LocalizedStrings.englishPlural(R.plurals.home_relative_weeks_ago, weeks, weeks)

        launchHome().use {
            LocaleHome.awaitLibrary(home, SEEDED_RECORDS)
            val before = home.library().cell(agedTitle).badges()
            assertTrue(
                "the run starts in English, so the aged row must read \"$englishRelative\". It read $before.",
                before.contains(englishRelative),
            )

            locale.setLive(RUSSIAN)

            home.awaitShown(Await.LONG_TIMEOUT_MS)
            home.awaitRendered(Await.LONG_TIMEOUT_MS)
            LocaleHome.awaitLibrary(home, SEEDED_RECORDS)

            val russianRelative = localizedPlural(R.plurals.home_relative_weeks_ago, weeks, weeks)
            val after = Await.untilValue(
                "the recreated library rebinds the aged row in Russian",
                Await.LONG_TIMEOUT_MS,
                read = { runCatching { home.library().cell(agedTitle).badges() }.getOrDefault(emptyList()) },
            ) { it.contains(russianRelative) }

            assertFalse(
                "the relative-date badge may not keep its English text after the language change, the row read $after",
                after.contains(englishRelative),
            )
        }
    }

    private companion object {
        const val TARGET_PAGE = 12
        const val AGED_DAYS = 14L
        const val WEEK_DAYS = 7L
        const val PAGE_COUNT = 20
        const val SEEDED_RECORDS = 2
    }
}
