// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class PluralCategoryTest : LocaleTestCase() {

    @Test
    @TestId("LOC-18")
    fun theRussianDeleteConfirmationUsesOneAtASingleItem() {
        assertDeleteConfirmation(RUSSIAN, 1, neighbour = 5)
    }

    @Test
    @TestId("LOC-19")
    fun theRussianDeleteConfirmationUsesFewAtTwoItems() {
        assertDeleteConfirmation(RUSSIAN, 2, neighbour = 5)
    }

    @Test
    @TestId("LOC-20")
    fun theRussianDeleteConfirmationUsesManyAtFiveItems() {
        assertDeleteConfirmation(RUSSIAN, 5, neighbour = 2)
    }

    @Test
    @TestId("LOC-21")
    @TestDuration(DurationCategory.NORMAL)
    fun theRussianDeleteConfirmationShowsTheRealCountAtTwentyOne() {
        val body = assertDeleteConfirmation(RUSSIAN, REGRESSION_COUNT, neighbour = 5)
        assertTrue(
            "the Russian one category at 21 must carry the count. It read \"$body\".",
            LocaleFixtures.asciiDigitsOf(body).contains(REGRESSION_COUNT.toString()),
        )
    }

    @Test
    @TestId("LOC-22")
    fun theUkrainianDeleteConfirmationUsesOneAtASingleItem() {
        assertDeleteConfirmation(UKRAINIAN, 1, neighbour = 5)
    }

    @Test
    @TestId("LOC-23")
    fun theUkrainianDeleteConfirmationUsesFewAtTwoItems() {
        assertDeleteConfirmation(UKRAINIAN, 2, neighbour = 5)
    }

    @Test
    @TestId("LOC-24")
    fun theUkrainianDeleteConfirmationUsesManyAtFiveItems() {
        assertDeleteConfirmation(UKRAINIAN, 5, neighbour = 2)
    }

    @Test
    @TestId("LOC-25")
    @TestDuration(DurationCategory.NORMAL)
    fun theUkrainianDeleteConfirmationShowsTheRealCountAtTwentyOne() {
        val body = assertDeleteConfirmation(UKRAINIAN, REGRESSION_COUNT, neighbour = 5)
        assertTrue(
            "the Ukrainian one category at 21 must carry the count. It read \"$body\".",
            LocaleFixtures.asciiDigitsOf(body).contains(REGRESSION_COUNT.toString()),
        )
    }

    private fun assertDeleteConfirmation(tag: String, count: Int, neighbour: Int): String {
        useLocale(tag)
        val titles = LocaleRecords.seedMany(count).map { it.fileName }

        var body = ""
        launchHome().use {
            LocaleHome.selectItems(home, titles)

            val chip = localized(R.string.home_selected_count, count)
            assertTrue(
                "the selection chip must report $count, visible text was ${visibleTexts()}",
                awaitTextPresent(chip),
            )

            body = localizedPlural(R.plurals.home_delete_batch_confirm_message, count, count)
            assertNotEquals(
                "the $tag plural for $count must differ from the form chosen for $neighbour, or the check " +
                    "below could pass on a fallback form",
                LocalizedStrings.plural(tag, R.plurals.home_delete_batch_confirm_message, neighbour, count),
                body,
            )

            LocaleHome.tapDelete(body)
            assertTrue(
                "the confirmation must use the $tag plural form for $count. Expected \"$body\", visible " +
                    "text was ${visibleTexts()}",
                awaitTextPresent(body),
            )
            assertFalse(
                "a plural body must never leak its format specifier, it read \"$body\"",
                body.contains(FORMAT_SPECIFIER),
            )
        }
        return body
    }

    private fun awaitTextPresent(expected: String): Boolean =
        TestEnvironment.uiDevice.wait(Until.hasObject(By.text(expected)), Await.LONG_TIMEOUT_MS) == true

    private companion object {
        const val REGRESSION_COUNT = 21
        const val FORMAT_SPECIFIER = "%d"
    }
}
