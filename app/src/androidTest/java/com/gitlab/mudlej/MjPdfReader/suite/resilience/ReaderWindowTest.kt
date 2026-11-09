// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.FileDescriptors
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReaderWindowTest : ResilienceTestCase() {

    @Test
    @TestId("RES-32")
    fun aSecondViewIntentIntoTheSameTaskOpensTheNewDocument() {
        val first = fixture(ResilienceFixtures.NORMAL)
        val second = fixture(ResilienceFixtures.ARABIC)

        launchReaderOutsideScenario(ReaderRobot.viewIntent(first))
        val firstHash = reader.awaitFileHashResolved()
        Records.awaitRowExists(firstHash, Await.LONG_TIMEOUT_MS)
        jumpToPage(FIRST_DOCUMENT_PAGE)

        ReaderProbe.startReader(ReaderRobot.viewIntent(second))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        val opened = reader.awaitState(
            "the reader reports the second document's page count",
            Await.VERY_LONG_TIMEOUT_MS,
        ) { it.pageCount == ResilienceFixtures.ARABIC_PAGE_COUNT }

        assertEquals(
            "the action bar must name the newly opened document, which PDF.getTitle renders without the " +
                "file extension",
            second.nameWithoutExtension,
            ReaderProbe.actionBarTitle(),
        )
        assertEquals(
            "the viewer must be showing the second document, not the first",
            ResilienceFixtures.ARABIC_PAGE_COUNT as Int?,
            opened.pageCount,
        )

        val secondHash = reader.awaitFileHashResolved()
        val firstRow = Records.awaitRowExists(firstHash, Await.LONG_TIMEOUT_MS)
        val secondRow = Records.awaitRowExists(secondHash, Await.LONG_TIMEOUT_MS)
        assertTrue(
            "the newly opened document must be the more recently opened of the two, but the first reads " +
                "${firstRow.lastOpened} and the second ${secondRow.lastOpened}",
            !secondRow.lastOpened.isBefore(firstRow.lastOpened),
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-33")
    fun openingASecondDocumentClosesTheOtherReaderWindow() {
        SettingsSeeder.seed(Preferences.openPdfsInSeparateWindowsKey, false)
        val first = fixture(ResilienceFixtures.NORMAL)
        val second = fixture(ResilienceFixtures.SCANNED)

        launchReaderOutsideScenario(ReaderRobot.viewIntent(first))
        assertEquals(
            "the first document must be open in exactly one reader task",
            1,
            ReaderProbe.readerTaskCount(),
        )

        ReaderProbe.startReader(
            ReaderRobot.viewIntent(second).addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        )
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitState("the second document reports its page count", Await.VERY_LONG_TIMEOUT_MS) {
            it.pageCount == ResilienceFixtures.SCANNED_PAGE_COUNT
        }

        Await.untilValue(
            condition = "the separate windows setting being off leaves exactly one reader task. " +
                "Preferences.openPdfsInSeparateWindowsDefault is true, so the setting has to be seeded off " +
                "rather than assumed off, and two surviving tasks on defaults is the app doing what it was " +
                "told",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { ReaderProbe.readerTaskCount() },
        ) { it == 1 }
        FileDescriptors.awaitSettledCountOf(first, 0)
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private companion object {
        const val FIRST_DOCUMENT_PAGE = 5
    }
}
