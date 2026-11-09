// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.resilience

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ReaderProbe
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FileChangedUnderneathTest : ResilienceTestCase() {

    @Test
    @TestId("RES-41")
    fun deletingTheOpenFileRunsTheRecoveryPath() {
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val hash = reader.awaitFileHashResolved()
        jumpToPage(STORED_PAGE)
        Records.awaitStoredPage(hash, STORED_PAGE, Await.LONG_TIMEOUT_MS)

        Fixtures.delete(file)
        Await.untilFileAbsent(file, Await.LONG_TIMEOUT_MS)

        ReaderProbe.onReader { it.reloadPdf() }

        awaitRecoveryDialog()
        Snackbars.assertNoneWithin()
        val record = Records.byHash(hash)
        assertEquals(
            "the recovery path must not touch the stored reading position",
            STORED_PAGE,
            record?.pageNumber,
        )
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-42")
    fun replacingTheOpenFileTreatsItAsADifferentDocument() {
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val originalHash = reader.awaitFileHashResolved()
        jumpToPage(BOOKMARKED_PAGE)
        Records.awaitStoredPage(originalHash, BOOKMARKED_PAGE, Await.LONG_TIMEOUT_MS)
        ResilienceSeed.insertBookmarks(originalHash, 1)
        val originalRecord = Records.awaitRowExists(originalHash)

        val replacement = Fixtures.seededFixture(ResilienceFixtures.SCANNED)
        val replacementBytes = replacement.readBytes()
        file.writeBytes(replacementBytes)
        Fixtures.scanAndWait(file)
        assertArrayEquals(
            "the replacement must actually be on disk before the reload, otherwise everything below is " +
                "asserted against the document that was already open",
            replacementBytes,
            file.readBytes(),
        )

        ReaderProbe.onReader { it.reloadPdf() }
        awaitReaderReady()
        reader.awaitState(
            "the reloaded document reports the replacement's page count, which is what proves the reload " +
                "reached pdfium rather than leaving the previous document on screen",
            Await.LONG_TIMEOUT_MS,
        ) { it.pageCount == ResilienceFixtures.SCANNED_PAGE_COUNT }
        val reloaded = settledFullQuality()

        val newHash = reader.awaitState(
            "the reloaded document resolves an identity of its own. Waiting for any non empty hash is not " +
                "enough here, because the previous document's hash is already non empty and satisfies such a " +
                "wait on its first evaluation",
            Await.LONG_TIMEOUT_MS,
        ) { !it.fileHash.isNullOrEmpty() && it.fileHash != originalHash }.fileHash!!
        assertNotEquals(
            "replacing the bytes under the same path must resolve to a different document identity",
            originalHash,
            newHash,
        )
        Records.awaitRowExists(newHash, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "the replaced document must open at its own first page, not the position of the document it replaced",
            0 as Int?,
            reloaded.page,
        )
        assertEquals(
            "the replaced document must report the replacement's page count",
            ResilienceFixtures.SCANNED_PAGE_COUNT as Int?,
            reloaded.pageCount,
        )
        assertEquals(
            "the original document's bookmarks must stay with the original identity",
            1,
            Records.bookmarksOf(originalHash).size,
        )
        assertTrue(
            "no bookmark may be carried onto the replacement's identity",
            Records.bookmarksOf(newHash).isEmpty(),
        )
        val preservedRecord = Records.awaitRowExists(originalHash)
        assertEquals("the old document keeps its reading position", originalRecord.pageNumber, preservedRecord.pageNumber)
        assertEquals("the old document keeps its page count", originalRecord.length, preservedRecord.length)
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    @Test
    @TestId("RES-55")
    fun aReplacementThatCannotBeIdentifiedDoesNotInheritTheOldIdentity() {
        SettingsSeeder.seed(Preferences.homeDisabledKey, true)
        val file = fixture(ResilienceFixtures.NORMAL)
        launchReaderOutsideScenario(ReaderRobot.viewIntent(file))
        val originalHash = reader.awaitFileHashResolved()
        jumpToPage(STORED_PAGE)
        Records.awaitStoredPage(originalHash, STORED_PAGE, Await.LONG_TIMEOUT_MS)
        val loadsBefore = ReaderTestHooks.documentLoadStarts()

        file.writeBytes(ByteArray(0))
        assertEquals("the replacement must be empty on disk before the reload", 0L, file.length())

        ReaderProbe.onReader { it.reloadPdf() }
        Await.until("the reload starts a new document load", Await.LONG_TIMEOUT_MS) {
            ReaderTestHooks.documentLoadStarts() > loadsBefore
        }
        reader.awaitState(
            "the reloaded reader drops the identity of the document that was replaced",
            Await.LONG_TIMEOUT_MS,
        ) { it.fileHash != originalHash }
        Records.assertStoredPageStays(originalHash, STORED_PAGE)
        ReaderProbe.finishAndRemoveReaderTasks()
    }

    private fun awaitRecoveryDialog() {
        val relocateTitle = TestEnvironment.targetContext.getString(R.string.home_relocate_title)
        val staleTitle = TestEnvironment.targetContext.getString(R.string.stale_shared_title)
        val fileError = TestEnvironment.targetContext.getString(R.string.file_opening_error)
        Await.until(
            "the reader offers a recovery dialog rather than a plain \"$fileError\" failure",
            Await.LONG_TIMEOUT_MS,
        ) {
            TestEnvironment.uiDevice.hasObject(By.text(relocateTitle)) ||
                TestEnvironment.uiDevice.hasObject(By.text(staleTitle))
        }
    }

    private companion object {
        const val STORED_PAGE = 2
        const val BOOKMARKED_PAGE = 14
    }
}
