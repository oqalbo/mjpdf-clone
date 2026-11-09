// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationCard
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationDialogs
import com.gitlab.mudlej.MjPdfReader.suite.annotations.AnnotationJournalFile
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.lists.ExportCleanupRule
import com.gitlab.mudlej.MjPdfReader.suite.lists.ExportPicker
import com.gitlab.mudlej.MjPdfReader.suite.lists.ListExport
import com.gitlab.mudlej.MjPdfReader.suite.lists.NotesListRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeViewMode
import com.gitlab.mudlej.MjPdfReader.ui.reader.annotation.HighlightPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class LocaleFormattingTest : LocaleTestCase() {

    @get:Rule
    val exports = ExportCleanupRule()

    @Test
    @TestId("LOC-30")
    fun theAnnotationCreationDateIsWrittenWithAsciiDigitsUnderPersian() {
        useLocale(PERSIAN)

        openReader(LocaleFixtures.NORMAL) { _ ->
            val journal = AnnotationJournalFile.fileOfOpenDocument(reader)
            val point = selectSomeText()

            AnnotationCard.tapSwatch(HighlightPalette.YELLOW)
            val afterHighlight = AnnotationJournalFile.awaitLineCount(journal, 1, Await.LONG_TIMEOUT_MS)
            val created = afterHighlight.first().created
                ?: throw AssertionError(
                    "The add edit carried no creation date, so there is nothing to check. The line read " +
                        "\"${afterHighlight.first().raw}\"."
                )
            assertPdfDate("the highlight creation date", created)

            tapViewPoint(point)
            AnnotationCard.awaitShown()
            AnnotationCard.tapButton(R.id.textSelectionNoteButton)
            AnnotationDialogs.awaitTitle(localized(R.string.note))
            AnnotationDialogs.typeIntoEditor(NOTE_TEXT)
            AnnotationDialogs.pressPositive()

            val afterNote = AnnotationJournalFile.awaitLineCount(journal, 2, Await.LONG_TIMEOUT_MS)
            val modified = afterNote.last().modified
                ?: throw AssertionError(
                    "The note edit carried no modification date. The line read \"${afterNote.last().raw}\"."
                )
            assertPdfDate("the note modification date", modified)
        }
    }

    @Test
    @TestId("LOC-31")
    @TestDuration(DurationCategory.NORMAL)
    fun theNotesExportTimestampStaysReadableUnderPersian() {
        useLocale(PERSIAN)
        val documentTitle = RICH_DOCUMENT_TITLE
        val fileName = exports.claim("$documentTitle - ${localized(R.string.user_notes_title)}.txt")

        openReader(LocaleFixtures.HIGHLIGHTS_RICH) { _ ->
            val screen = LocaleLists.screens().first { it.titleRes == R.string.user_notes_title }
            LocaleLists.openFromActions(screen)

            val list = NotesListRobot()
            list.awaitScanFinished()
            list.export()
            ExportPicker.awaitShown()
            ExportPicker.saveIntoDownloadsKeepingName()
            Snackbars.awaitText(ListExport.exportedDoneMessage(), Await.LONG_TIMEOUT_MS)

            val exported = ListExport.awaitWritten(fileName)
            assertTrue("the exported notes file must not be empty", exported.text.isNotBlank())

            val noteCount = exported.blocks().size
            assertTrue("the fixture must carry at least one note to export", noteCount >= 1)

            val template = localized(R.string.export_notes_line, noteCount, TIMESTAMP_MARKER)
            val prefix = template.substringBefore(TIMESTAMP_MARKER)
            val suffix = template.substringAfter(TIMESTAMP_MARKER)
            val header = exported.headerLines.firstOrNull { it.startsWith(prefix) && it.endsWith(suffix) }
                ?: throw AssertionError(
                    "No header line matched the Persian export_notes_line shaped \"$template\". The header " +
                        "lines were ${exported.headerLines}."
                )

            val timestamp = header.removePrefix(prefix).removeSuffix(suffix)
            assertTrue(
                "the exported timestamp must use ASCII digits under Persian; observed \"$timestamp\"",
                TIMESTAMP_SHAPE.matches(timestamp),
            )
        }
    }

    @Test
    @TestId("LOC-32")
    fun theLibraryLastOpenedDateRendersUnderPersian() {
        useLocale(PERSIAN)
        val opened = LocalDateTime.now().minusDays(AGED_DAYS)
        LocaleRecords.seed("loc32", LocaleRecords.DEFAULT_PAGES, opened)

        launchHome().use {
            LocaleHome.awaitLibrary(home, SINGLE_RECORD)
            home.library().cellAt(0).tapOptions()

            val row = Await.untilNotNull("the record options dialog shows its last opened row") {
                runCatching { LocaleViews.recordOptionsLastOpened() }.getOrNull()?.takeIf { it.isNotBlank() }
            }

            val template = localized(R.string.home_last_opened, TIMESTAMP_MARKER)
            val prefix = template.substringBefore(TIMESTAMP_MARKER)
            val suffix = template.substringAfter(TIMESTAMP_MARKER)
            assertTrue(
                "the last opened row must use the Persian home_last_opened wording shaped \"$template\", " +
                    "it read \"$row\"",
                row.startsWith(prefix) && row.endsWith(suffix),
            )

            val date = row.removePrefix(prefix).removeSuffix(suffix)
            assertTrue(
                "appDateFormatter writes yyyy-MM-dd, so the date must have that shape in whichever digit " +
                    "set is produced. It read \"$date\", digit set ${digitSetOf(date)}.",
                DATE_SHAPE.matches(asciiShapeOf(date)),
            )
            assertEquals(
                "the year must survive the formatting, read back through Character.digit",
                opened.year,
                LocaleFixtures.asciiDigitsOf(date).take(YEAR_DIGITS).toInt(),
            )
        }
    }

    @Test
    @TestId("LOC-33")
    fun relativeLastOpenedLabelsUseTheRightRussianPluralCategory() {
        useLocale(RUSSIAN)
        SettingsSeeder.seed(Preferences.homeViewModeKey, HomeViewMode.LIST.name)

        val now = LocalDateTime.now()
        val cases = RELATIVE_DAYS.mapIndexed { index, days ->
            val opened = now.minusDays(days)
            val title = LocaleRecords.titleFor(index + 1)
            LocaleRecords.seed(title, LocaleRecords.DEFAULT_PAGES, opened)
            RelativeCase(title, days, expectedRelativeLabel(opened, now))
        }

        launchHome().use {
            LocaleHome.awaitLibrary(home, cases.size)
            cases.forEach { case ->
                val badges = home.library().cell(case.title).badges()
                assertTrue(
                    "the row aged ${case.days} days must carry the Russian relative label " +
                        "\"${case.expected}\". Its badges were $badges.",
                    badges.contains(case.expected),
                )
            }
        }
    }

    @Test
    @TestId("LOC-34")
    fun theFileSizeKeepsADotDecimalSeparatorUnderGerman() {
        useLocale(GERMAN)
        val file = fixture(LocaleFixtures.SIZE_FIXTURE)
        assertEquals(
            "the size fixture must be exactly the recorded byte count, or the formatted size is not the " +
                "one this test pins",
            LocaleFixtures.SIZE_FIXTURE_BYTES,
            file.length(),
        )
        LocaleRecords.seed("loc34", LocaleFixtures.SIZE_FIXTURE_PAGES, file = file)

        launchHome().use {
            LocaleHome.awaitLibrary(home, SINGLE_RECORD)
            home.library().cellAt(0).tapOptions()

            val info = LocaleViews.awaitRecordOptionsInfo({ SIZE_SHAPE.containsMatchIn(it) })
            assertFalse(
                "a German decimal comma would mean the Locale.US pin was removed. The line read \"$info\".",
                info.contains(','),
            )

            val size = SIZE_SHAPE.find(info)!!.value
            val pages = LocaleFixtures.asciiDigitsOf(info.substringBefore(size)).toInt()
            assertEquals(
                "the size sits inside the German home_pages_size_template, whose page count is localized " +
                    "while the size is not",
                localized(R.string.home_pages_size_template, pages, size),
                info,
            )
        }
    }

    private data class RelativeCase(val title: String, val days: Long, val expected: String)

    private fun expectedRelativeLabel(opened: LocalDateTime, now: LocalDateTime): String {
        val date = opened.toLocalDate()
        val today = now.toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days < WEEK_THRESHOLD -> throw ConditionTimeoutException(
                "This test only covers the weeks, months and years branches of formatRelativeDate, and $days " +
                    "days falls in an earlier branch"
            )
            days < MONTH_THRESHOLD -> localizedPlural(
                R.plurals.home_relative_weeks_ago,
                (days / WEEK_THRESHOLD).toInt(),
                (days / WEEK_THRESHOLD).toInt(),
            )
            days < YEAR_THRESHOLD -> {
                val months = ChronoUnit.MONTHS.between(date, today).toInt().coerceAtLeast(1)
                localizedPlural(R.plurals.home_relative_months_ago, months, months)
            }
            else -> {
                val years = ChronoUnit.YEARS.between(date, today).toInt().coerceAtLeast(1)
                localizedPlural(R.plurals.home_relative_years_ago, years, years)
            }
        }
    }

    private fun selectSomeText(): PointF {
        var lastError: Throwable? = null
        SELECTION_FRACTIONS.forEach { vertical ->
            val point = pointInsidePage(SELECTION_HORIZONTAL, vertical)
            val attempt = runCatching { selection.longPressAtViewPoint(point, Await.SHORT_TIMEOUT_MS) }
            if (attempt.isSuccess) {
                AnnotationCard.awaitShown()
                return point
            }
            lastError = attempt.exceptionOrNull()
        }
        throw AssertionError(
            "No long press on the normal fixture produced a text selection, so no highlight could be made. " +
                "Last failure was $lastError"
        )
    }

    private fun pointInsidePage(horizontalFraction: Float, verticalFraction: Float): PointF {
        val page = reader.readState().pageRect
            ?: throw ConditionTimeoutException(
                "The reader publishes no page rectangle, so there is no page area to press inside"
            )
        return PointF(
            page.left + page.width() * horizontalFraction,
            page.top + page.height() * verticalFraction,
        )
    }

    private fun tapViewPoint(point: PointF) {
        val origin = selection.readerViewOriginOnScreen()
        TestEnvironment.uiDevice.click((origin.x + point.x).toInt(), (origin.y + point.y).toInt())
        TestEnvironment.waitForIdleSync()
    }

    private fun assertPdfDate(what: String, value: String) {
        assertTrue(
            "$what must be a PDF conformant ASCII date, which the specification requires. It read " +
                "\"$value\", digit set ${digitSetOf(value)}.",
            PDF_DATE.matches(value),
        )
        assertFalse(
            "$what must carry no Persian digit in U+06F0 to U+06F9, it read \"$value\"",
            LocaleFixtures.containsPersianDigit(value),
        )
    }

    private fun asciiShapeOf(value: String): String =
        value.map { character ->
            val digit = Character.digit(character, RADIX)
            if (digit >= 0) '0' + digit else character
        }.joinToString(separator = "")

    private fun digitSetOf(value: String): String = when {
        LocaleFixtures.containsPersianDigit(value) -> "Persian"
        LocaleFixtures.containsArabicIndicDigit(value) -> "Arabic-Indic"
        else -> "ASCII"
    }

    private companion object {
        const val NOTE_TEXT = "loc30"
        const val RICH_DOCUMENT_TITLE = "The Memory Model"
        const val TIMESTAMP_MARKER = "\u0001timestamp\u0001"
        const val AGED_DAYS = 400L
        const val YEAR_DIGITS = 4
        const val RADIX = 10
        const val SINGLE_RECORD = 1
        const val SELECTION_HORIZONTAL = 0.35f
        const val WEEK_THRESHOLD = 7L
        const val MONTH_THRESHOLD = 30L
        const val YEAR_THRESHOLD = 365L

        val RELATIVE_DAYS = listOf(14L, 60L, 1100L)
        val SELECTION_FRACTIONS = listOf(0.30f, 0.35f, 0.40f, 0.45f, 0.25f, 0.50f)
        val PDF_DATE = Regex("^D:[0-9]{14}$")
        val DATE_SHAPE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
        val TIMESTAMP_SHAPE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}$")
        val SIZE_SHAPE = Regex("[0-9]+\\.[0-9]{2} MB")
    }
}
