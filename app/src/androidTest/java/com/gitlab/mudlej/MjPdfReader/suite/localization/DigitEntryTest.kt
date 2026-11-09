// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.runner.lifecycle.Stage
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class DigitEntryTest : LocaleTestCase() {

    @Test
    @TestId("LOC-35")
    fun goToPageAcceptsArabicIndicDigitsTypedUnderArabic() {
        assertLocalizedPageEntry(ARABIC, LocaleFixtures.ARABIC_INDIC_STRING)
    }

    @Test
    fun goToPageAcceptsPersianDigitsTypedUnderPersian() {
        assertLocalizedPageEntry(PERSIAN, "۴۲")
    }

    @Test
    fun goToPageAcceptsMixedAsciiAndArabicIndicDigits() {
        assertLocalizedPageEntry(ARABIC, "4٢")
    }

    private fun assertLocalizedPageEntry(locale: String, digits: String) {
        useLocale(locale)
        openReader(LocaleFixtures.LARGE) {
            LocaleGoToPage.open()
            LocaleGoToPage.pasteIntoField(digits)
            assertEquals("localized digits survive typing or pasting", digits, LocaleGoToPage.fieldText())
            LocaleGoToPage.awaitGoToEnabled(true)
            LocaleGoToPage.tapGo()
            reader.awaitPageIndex(TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)
        }
    }

    @Test
    @TestId("LOC-36")
    @TestDuration(DurationCategory.NORMAL)
    fun goToPageAcceptsAsciiDigitsUnderArabic() {
        useLocale(ARABIC)

        val scenario = openReaderScenario(LocaleFixtures.LARGE)
        scenario.first.use {
            val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)

            LocaleGoToPage.open()
            LocaleGoToPage.type(TARGET_PAGE.toString())
            LocaleGoToPage.awaitGoToEnabled(true)
            LocaleGoToPage.tapGo()

            reader.awaitPageIndex(TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)
            reader.awaitMotionSettled(Await.LONG_TIMEOUT_MS)

            TestEnvironment.uiDevice.pressHome()
            Screens.awaitStage(MainActivity::class.java, Stage.STOPPED, Await.LONG_TIMEOUT_MS)

            Records.awaitStoredPage(fileHash, TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)
        }
    }

    private companion object {
        const val TARGET_PAGE = 42
    }
}
