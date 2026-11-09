// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.bookmarks

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.annotation.IdRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.PdfExtractorDebug
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.core.CrashGate
import com.gitlab.mudlej.MjPdfReader.suite.core.Dialogs
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.history.HistoryReader
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.localization.LocaleRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.zoom.ReaderCanvas
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class GoToPageDialogTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val locale = LocaleRule()

    private val reader = ReaderRobot()
    private val goToPage = GoToPageRobot()

    @Test
    @TestId("BMK-41")
    fun theGoToPageDialogOpensOnTheCurrentPage() {
        openReader(BookmarksFixtures.LARGE)
        BookmarksReader.jumpToPage(reader, CURRENT_PAGE_INDEX)

        ReaderCanvas.withQueuedInput {
            goToPage.openDialogFromReader()
        }
        goToPage.awaitStripSettled()

        assertEquals(
            "the dialog title",
            BookmarksReader.string(R.string.go_to_page),
            goToPage.dialogTitle(),
        )
        assertEquals(
            "the input hint names where the reader currently is, one based",
            goToPage.hintFor(CURRENT_PAGE_INDEX + 1, BookmarksFixtures.LARGE_PAGES),
            goToPage.hintText(),
        )

        val strip = goToPage.strip()
        assertEquals(
            "the strip lays its cells out horizontally",
            LinearLayoutManager.HORIZONTAL,
            TestEnvironment.onMainThread {
                (strip.layoutManager as LinearLayoutManager).orientation
            },
        )
        goToPage.awaitStripCellVisible(CURRENT_PAGE_INDEX)
        assertEquals(
            "the centred cell is labelled with the one based current page",
            (CURRENT_PAGE_INDEX + 1).toString(),
            goToPage.stripCellPageNumber(CURRENT_PAGE_INDEX),
        )
        assertTrue(
            "the current page cell carries the current page stroke",
            goToPage.stripCellIsCurrentPage(CURRENT_PAGE_INDEX),
        )

        assertTrue("the Go To button is offered", dialogButtonShown(android.R.id.button1))
        assertTrue("the Cancel button is offered", dialogButtonShown(android.R.id.button2))
        assertTrue("the All Pages button is offered", goToPage.isAllPagesShown())
    }

    @Test
    @TestId("BMK-42")
    fun typingAPageNumberAndGoingToIt() {
        openReader(BookmarksFixtures.LARGE)
        reader.awaitPageIndex(0)

        goToPage.openDialogFromReader()
        goToPage.type(TARGET_PAGE.toString())
        goToPage.awaitGoToEnabled(true)
        goToPage.tapGoTo()

        goToPage.awaitDialogGone()
        reader.awaitPageIndex(TARGET_PAGE - 1, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("BMK-43")
    fun emptyInputIsRefused() {
        openReader(BookmarksFixtures.LARGE)
        BookmarksReader.jumpToPage(reader, EMPTY_INPUT_PAGE_INDEX)

        goToPage.openDialogFromReader()
        goToPage.awaitGoToEnabled(false)

        goToPage.type("2")
        goToPage.awaitGoToEnabled(true)

        goToPage.clearField()
        goToPage.awaitGoToEnabled(false)
        assertTrue("the dialog stays open while confirmation is disabled", goToPage.isDialogShown())
        assertEquals("the field is still empty", "", goToPage.fieldText())
        assertEquals(
            "the reader did not move",
            EMPTY_INPUT_PAGE_INDEX as Int?,
            reader.readState().page,
        )
    }

    @Test
    @TestId("BMK-44")
    fun aPageNumberPastTheEndClampsToTheLastPage() {
        openReader(BookmarksFixtures.NORMAL)
        reader.awaitPageIndex(0)

        goToPage.openDialogFromReader()
        goToPage.type(PAST_THE_END)
        assertEquals(
            "the four digit overshoot survives the field's maxLength of 4",
            PAST_THE_END,
            goToPage.fieldText(),
        )
        goToPage.awaitGoToEnabled(true)
        goToPage.tapGoTo()

        goToPage.awaitDialogGone()
        reader.awaitPageIndex(BookmarksFixtures.NORMAL_PAGES - 1, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("BMK-45")
    fun zeroClampsToTheFirstPage() {
        openReader(BookmarksFixtures.NORMAL)
        BookmarksReader.jumpToPage(reader, ZERO_CLAMP_START_INDEX)

        goToPage.openDialogFromReader()
        goToPage.type("0")
        goToPage.awaitGoToEnabled(true)
        goToPage.tapGoTo()

        goToPage.awaitDialogGone()
        reader.awaitPageIndex(0, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("BMK-46")
    @TestDuration(DurationCategory.NORMAL)
    fun theInputAcceptsAtMostFourDigits() {
        openReader(BookmarksFixtures.LARGE)
        reader.awaitPageIndex(0)
        goToPage.openDialogFromReader()

        goToPage.typeAsciiThroughKeyboard("abc")
        assertEquals(
            "letters are refused by the digits filter",
            "",
            goToPage.fieldText(),
        )

        goToPage.typeAsciiThroughKeyboard("12.5")
        assertEquals(
            "the separator is filtered out by the digits attribute, which is an InputFilter",
            "125",
            goToPage.fieldText(),
        )

        goToPage.clearField()
        goToPage.type("123456")
        assertEquals("the field caps at maxLength=4", "1234", goToPage.fieldText())

        goToPage.awaitGoToEnabled(true)
        goToPage.tapGoTo()
        goToPage.awaitDialogGone()
        reader.awaitPageIndex(BookmarksFixtures.LARGE_PAGES - 1, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("BMK-47")
    @TestDuration(DurationCategory.NORMAL)
    fun arabicAndPersianDigitsInThePageField() {
        locale.set(ARABIC_LOCALE)
        openReader(BookmarksFixtures.ARABIC_RTL)
        reader.awaitPageIndex(0)

        assertUnicodeDigitsResolve(ARABIC_INDIC_TWELVE)
        assertUnicodeDigitsResolve(PERSIAN_TWELVE)
    }

    @Test
    @TestId("BMK-48")
    fun cancellingTheGoToPageDialog() {
        openReader(BookmarksFixtures.LARGE)
        BookmarksReader.jumpToPage(reader, CANCEL_PAGE_INDEX)

        val history = HistoryReader(reader)
        val backStackBefore = history.backStackPages()

        goToPage.openDialogFromReader()
        goToPage.type(CANCELLED_TARGET.toString())
        goToPage.tapCancel()

        goToPage.awaitDialogGone()
        assertEquals(
            "a cancelled navigation does not navigate",
            CANCEL_PAGE_INDEX as Int?,
            reader.readState().page,
        )
        assertEquals(
            "a cancelled navigation leaves no back entry behind",
            backStackBefore,
            history.backStackPages(),
        )
    }

    @Test
    @TestId("BMK-49")
    fun tappingAThumbnailInTheDialogStrip() {
        openReader(BookmarksFixtures.LARGE)
        BookmarksReader.jumpToPage(reader, STRIP_START_INDEX)

        goToPage.openDialogFromReader()
        goToPage.awaitStripSettled()

        goToPage.type(STRIP_TARGET.toString())
        goToPage.awaitStripCellVisible(STRIP_TARGET - 1)
        goToPage.awaitStripSettled()
        assertEquals(
            "typing only recentres the strip, it does not navigate",
            STRIP_START_INDEX as Int?,
            reader.readState().page,
        )

        goToPage.awaitStripThumbnail(STRIP_TARGET - 1)
        assertTrue(
            "the cell was showing a rendered thumbnail before it was tapped",
            goToPage.stripCellHasThumbnail(STRIP_TARGET - 1),
        )

        goToPage.tapStripCell(STRIP_TARGET - 1)
        goToPage.awaitDialogGone()
        reader.awaitPageIndex(STRIP_TARGET - 1, Await.LONG_TIMEOUT_MS)
    }

    @Test
    @TestId("BMK-56")
    @TestDuration(DurationCategory.NORMAL)
    fun theGoToPageDialogDoesNotOutliveItsActivity() {
        val marker = CrashGate.timestampNow()
        openReader(BookmarksFixtures.LARGE)
        reader.awaitPageIndex(0)

        val baselineExtractors = PdfExtractorDebug.liveInstances()

        goToPage.openDialogFromReader()
        goToPage.awaitStripSettled()
        goToPage.awaitStripThumbnail(goToPage.stripFirstVisiblePosition())
        assertTrue(
            "the thumbnail cache opened a document of its own, read $baselineExtractors before opening",
            PdfExtractorDebug.liveInstances() > baselineExtractors,
        )

        TestEnvironment.onMainThread { requireReader().finish() }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        goToPage.awaitDialogGone()
        Await.untilValue(
            "the live PdfExtractor count returns to $baselineExtractors, proving the thumbnail cache closed",
            Await.LONG_TIMEOUT_MS,
            read = { PdfExtractorDebug.liveInstances() },
        ) { it == baselineExtractors }
        assertNoWindowLeakSince(marker)

        openReader(BookmarksFixtures.LARGE)
        reader.awaitPageIndex(0)
        goToPage.openDialogFromReader()
        goToPage.awaitStripSettled()
        goToPage.awaitStripThumbnail(goToPage.stripFirstVisiblePosition())

        val beforeRotation = PdfExtractorDebug.liveInstances()
        rotateReaderToLandscape()

        assertTrue(
            "the dialog survives the rotation because MainActivity handles the configuration change itself",
            goToPage.isDialogShown(),
        )
        goToPage.awaitStripSettled()
        assertTrue(
            "the strip is still bound after the rotation",
            goToPage.stripLastVisiblePosition() >= goToPage.stripFirstVisiblePosition(),
        )
        assertEquals(
            "no document was opened or closed by the rotation",
            beforeRotation,
            PdfExtractorDebug.liveInstances(),
        )
    }

    private fun assertUnicodeDigitsResolve(digits: String) {
        goToPage.openDialogFromReader()

        onView(withId(R.id.editText)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            TestEnvironment.onMainThread {
                val field = view as android.widget.EditText
                field.requestFocus()
                val connection = field.onCreateInputConnection(android.view.inputmethod.EditorInfo())
                    ?: throw AssertionError("The page field has no input connection")
                assertTrue("the keyboard can commit localized digits", connection.commitText(digits, 1))
            }
        }
        TestEnvironment.waitForIdleSync()
        assertEquals("localized digits survive keyboard input", digits, goToPage.fieldText())
        goToPage.awaitGoToEnabled(true)
        goToPage.tapGoTo()
        goToPage.awaitDialogGone()
        reader.awaitPageIndex(UNICODE_TARGET_INDEX, Await.LONG_TIMEOUT_MS)
        BookmarksReader.jumpToPage(reader, 0)
    }

    private fun rotateReaderToLandscape() {
        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        reader.awaitState("the reader reports a landscape viewport", Await.LONG_TIMEOUT_MS) {
            val width = it.viewWidth
            val height = it.viewHeight
            width != null && height != null && width > height
        }
        reader.awaitRenderIdle(Await.LONG_TIMEOUT_MS)
    }

    private fun assertNoWindowLeakSince(marker: String) {
        val leaks = Shell.executeQuietly("logcat -d -v threadtime -T $marker")
            .lineSequence()
            .filter { it.contains(WINDOW_LEAK_MARKER) && it.contains(MainActivity::class.java.simpleName) }
            .toList()
        assertTrue(
            "finishing the reader with the dialog open must leak no window, logcat reported $leaks",
            leaks.isEmpty(),
        )
    }

    private fun dialogButtonShown(@IdRes buttonId: Int): Boolean =
        Dialogs.hasViewOnScreen(buttonId)

    private fun requireReader(): MainActivity =
        TestEnvironment.resumedActivityOfType(MainActivity::class.java) as? MainActivity
            ?: throw ConditionTimeoutException("No resumed reader activity")

    private fun openReader(fixture: String) {
        TestEnvironment.targetContext.startActivity(
            BookmarksReader.viewIntent(fixture).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitReady()
    }

    private companion object {
        const val CURRENT_PAGE_INDEX = 56
        const val TARGET_PAGE = 150
        const val EMPTY_INPUT_PAGE_INDEX = 19
        const val PAST_THE_END = "9999"
        const val ZERO_CLAMP_START_INDEX = 9
        const val CANCEL_PAGE_INDEX = 29
        const val CANCELLED_TARGET = 200
        const val STRIP_START_INDEX = 4
        const val STRIP_TARGET = 80
        const val UNICODE_TARGET_INDEX = 11
        const val ARABIC_LOCALE = "ar"
        const val ARABIC_INDIC_TWELVE = "١٢"
        const val PERSIAN_TWELVE = "۱۲"
        const val WINDOW_LEAK_MARKER = "WindowLeaked"
    }
}
