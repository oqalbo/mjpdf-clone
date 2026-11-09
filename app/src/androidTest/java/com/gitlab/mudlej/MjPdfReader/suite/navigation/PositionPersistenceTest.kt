// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.navigation

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.pdf.PDF
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class PositionPersistenceTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val navigation = NavigationRobot(reader)

    @Test
    @TestId("NAV-47")
    @TestDuration(DurationCategory.SHORT)
    fun thePositionIsRestoredWhenTheDocumentIsReopened() {
        val hash: String
        val firstOpened: LocalDateTime

        launchNormal().use {
            navigation.awaitReady()
            navigation.jumpToPage(11)
            reader.awaitPageIndex(10)

            hash = reader.awaitFileHashResolved()
            val record = NavigationRecords.awaitStoredPage(hash, 10)
            firstOpened = record.lastOpened
        }

        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(10)

            assertEquals("the reader comes back on the page it was left on", 10 as Int?, reader.readState().page)
            assertEquals("exactly one PdfRecord row must exist", 1, NavigationRecords.rowCount())

            Await.untilValue(
                "the reopen moves the last opened timestamp forward from $firstOpened",
                read = { NavigationRecords.byHash(hash) },
            ) { it != null && it.lastOpened.isAfter(firstOpened) }
        }
    }

    @Test
    @TestId("NAV-50")
    fun alwaysOpenAtFirstPageDoesNotEraseTheStoredPosition() {
        val hash: String

        launchNormal().use {
            navigation.awaitReady()
            navigation.jumpToPage(11)
            reader.awaitPageIndex(10)
            hash = reader.awaitFileHashResolved()
            NavigationRecords.awaitStoredPage(hash, 10)
        }

        SettingsSeeder.seed(Preferences.alwaysOpenAtFirstPageKey, true)
        assertEquals("the stored page index before reopening", 10, NavigationRecords.byHash(hash)?.pageNumber)

        launchNormal().use {
            navigation.awaitReady()
            reader.awaitPageIndex(0)

            assertEquals("the reader opens at the first page", 0 as Int?, reader.readState().page)
            NavigationRecords.assertStoredPageStays(hash, 10)

            val moved = navigation.swipeUntilPageChanges(forward = true)
            reader.awaitMotionSettled()
            val page = moved.page ?: throw AssertionError("The reader reports no page index")

            NavigationRecords.awaitStoredPage(hash, page)
        }
    }

    @Test
    @TestId("NAV-51")
    fun incognitoReadingDoesNotWriteThePageNumber() {
        ActivityScenario.launch<MainActivity>(incognitoIntent()).use {
            navigation.awaitReady()

            navigation.jumpToPage(9)
            reader.awaitPageIndex(8)

            assertEquals("navigation itself must still work in incognito", 8 as Int?, reader.readState().page)

            val hash = reader.awaitFileHashResolved()
            NavigationRecords.assertNoRowFor(hash)
            assertEquals("no bookmark row may be written in incognito", 0, NavigationRecords.bookmarksOf(hash).size)
            assertEquals("no PdfRecord row may exist at all", 0, NavigationRecords.rowCount())
        }
    }

    @Test
    @TestId("NAV-52")
    fun historyDisabledStopsThePositionWrite() {
        SettingsSeeder.seed(Preferences.historyEnabledKey, false)

        launchNormal().use {
            navigation.awaitReady()

            navigation.jumpToPage(9)
            reader.awaitPageIndex(8)

            val settled = reader.readState()
            assertEquals("navigation itself must still work with history off", 8 as Int?, settled.page)
            assertEquals(
                "the reader must still show the document",
                NavigationFixtures.NORMAL_PAGES as Int?,
                settled.pageCount,
            )

            val hash = reader.awaitFileHashResolved()
            NavigationRecords.assertNoRowFor(hash)
        }
    }

    private fun incognitoIntent(): Intent =
        ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)).putExtra(PDF.incognitoKey, true)

    private fun launchNormal(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(ReaderRobot.viewIntent(NavigationFixtures.uri(NavigationFixtures.NORMAL)))
}
