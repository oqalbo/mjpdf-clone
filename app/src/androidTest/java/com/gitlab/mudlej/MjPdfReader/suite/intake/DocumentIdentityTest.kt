// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class DocumentIdentityTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()

    @Test
    @TestId("INTAKE-12")
    fun aDocumentUnderTwoMegabytesIsIdentifiedByItsWholeContents() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        assertTrue(
            "normal.pdf must stay under the whole file identity limit, it is ${fixture.length()} bytes",
            fixture.length() <= IntakeFixtures.WHOLE_FILE_LIMIT_BYTES,
        )
        val expected = IntakeFixtures.expectedIdentity(fixture)

        openAndSettle(fixture)

        val record = IntakeDb.awaitRecordCount(1).single()
        assertEquals("the stored identity must be the whole file digest", expected, record.hash)
    }

    @Test
    @TestId("INTAKE-13")
    fun aDocumentOverTwoMegabytesIsIdentifiedByHeadTailAndSize() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.LARGE)
        assertTrue(
            "large.pdf must exceed the whole file identity limit, it is ${fixture.length()} bytes",
            fixture.length() > IntakeFixtures.WHOLE_FILE_LIMIT_BYTES,
        )
        val expected = IntakeFixtures.expectedIdentity(fixture)

        openAndSettle(fixture, Await.LONG_TIMEOUT_MS)

        val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
        assertEquals("the stored identity must be the head, tail and length digest", expected, record.hash)
    }

    @Test
    @TestId("INTAKE-14")
    fun twoDocumentsWithDifferentIdentitiesKeepSeparatePositions() {
        val first = IntakeFixtures.seeded(IntakeFixtures.SHARED_PREFIX_A)
        val second = IntakeFixtures.seeded(IntakeFixtures.SHARED_PREFIX_B)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(first)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            ReaderNavigation.jumpToPage(reader, DEEP_PAGE_IN_SHARED_PREFIX)
        }

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(second)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            val state = reader.awaitFullQualityRender()
            assertEquals(
                "the second document must open at its own position, not the first document's",
                0 as Int?,
                state.page,
            )
        }

        val records = IntakeDb.awaitRecordCount(2, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "two documents sharing a first megabyte must still hold two identities",
            2,
            records.map { it.hash }.toSet().size,
        )
        records.forEach {
            assertTrue(
                "every identity must carry the new scheme prefix, read ${it.hash}",
                it.hash.startsWith(IntakeFixtures.IDENTITY_PREFIX),
            )
        }

        val recordA = IntakeDb.recordEndingIn(IntakeFixtures.SHARED_PREFIX_A)
        val recordB = IntakeDb.recordEndingIn(IntakeFixtures.SHARED_PREFIX_B)
        assertNotNull("no record points at ${IntakeFixtures.SHARED_PREFIX_A}", recordA)
        assertNotNull("no record points at ${IntakeFixtures.SHARED_PREFIX_B}", recordB)
        assertEquals(
            "the first document keeps the page it was left on",
            DEEP_PAGE_IN_SHARED_PREFIX,
            recordA!!.pageNumber,
        )
        assertEquals("the second document keeps its own first page", 0, recordB!!.pageNumber)
    }

    @Test
    @TestId("INTAKE-15")
    fun anOldRecordConvertsToTheNewIdentityOnOpenCarryingItsBookmarks() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val legacy = IntakeFixtures.expectedLegacyIdentity(fixture)
        val expected = IntakeFixtures.expectedIdentity(fixture)

        IntakeDb.insertRecord(legacy, Uri.fromFile(fixture), pageNumber = STORED_PAGE, fileName = "normal")
        IntakeDb.insertBookmark(legacy, 2, "first")
        IntakeDb.insertBookmark(legacy, 9, "second")
        IntakeDb.insertSaveDestination(SAVE_SOURCE_KEY, SAVE_DESTINATION_URI, legacy)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitPageIndex(STORED_PAGE, Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            val records = IntakeDb.awaitRecordCount(1)
            val record = records.single()
            assertEquals("the record must be rekeyed to the new identity", expected, record.hash)
            assertNull("no row may remain under the legacy identity", IntakeDb.record(legacy))
            assertEquals("the reading position must survive conversion", STORED_PAGE, record.pageNumber)

            val bookmarks = IntakeDb.bookmarks()
            assertEquals("both bookmarks must survive conversion", 2, bookmarks.size)
            assertEquals(
                "every bookmark must carry the new identity",
                setOf(expected),
                bookmarks.map { it.fileHash }.toSet(),
            )

            val destination = IntakeDb.saveDestination(SAVE_SOURCE_KEY)
            assertNotNull("the annotation save destination must survive conversion", destination)
            assertEquals(
                "the save destination must be rekeyed to the new identity",
                expected,
                destination!!.lastSavedHash,
            )
        }
    }

    @Test
    @TestId("INTAKE-16")
    fun aCopyOfAFileCannotClaimAnotherFilesOldRecord() {
        val original = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val copy = IntakeFixtures.copyInto(rules.testDirectory.directory(), IntakeFixtures.NORMAL)
        val legacy = IntakeFixtures.expectedLegacyIdentity(original)
        val expected = IntakeFixtures.expectedIdentity(original)

        IntakeDb.insertRecord(legacy, Uri.fromFile(original), pageNumber = STORED_PAGE, fileName = "normal")

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(copy)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            val state = reader.awaitFullQualityRender()
            assertEquals("the copy is a new document and opens at its first page", 0 as Int?, state.page)

            IntakeDb.awaitRecordCount(2, Await.LONG_TIMEOUT_MS)

            val legacyRow = IntakeDb.record(legacy)
            assertNotNull("the legacy row must survive untouched", legacyRow)
            assertEquals("the legacy row keeps its position", STORED_PAGE, legacyRow!!.pageNumber)
            assertEquals(
                "the legacy row keeps pointing at the file it describes",
                Uri.fromFile(original).toString(),
                legacyRow.uri.toString(),
            )

            val newRow = IntakeDb.record(expected)
            assertNotNull("the copy must get a record of its own", newRow)
            assertEquals(
                "the new row points at the copy",
                Uri.fromFile(copy).toString(),
                newRow!!.uri.toString(),
            )
        }
    }

    @Test
    @TestId("INTAKE-17")
    fun reopeningTheSameDocumentReusesItsRecordAndRefreshesLastOpened() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)

        openAndSettle(fixture)
        val first = IntakeDb.awaitRecordCount(1).single()
        val firstOpened = first.lastOpened

        openAndSettle(fixture)

        val second = Await.untilValue(
            "the record's lastOpened moves past $firstOpened",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeDb.record(first.hash) },
        ) { it != null && it.lastOpened.isAfter(firstOpened) }!!

        IntakeDb.holdRecordCount(1)
        assertEquals("reopening must reuse the same identity", first.hash, second.hash)
    }

    private fun openAndSettle(file: java.io.File, timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS) {
        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(file)).use {
            reader.awaitShown(timeoutMs)
            reader.awaitLoaded()
            reader.awaitFullQualityRender()
            reader.awaitFileHashResolved()
        }
    }

    private companion object {
        const val STORED_PAGE = 7
        const val DEEP_PAGE_IN_SHARED_PREFIX = 1
        const val SAVE_SOURCE_KEY = "intake-15-source"
        const val SAVE_DESTINATION_URI = "content://mjpdf.test.destination/intake-15.pdf"
    }
}
