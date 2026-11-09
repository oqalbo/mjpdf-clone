// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.ActivityWatcher
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class RelocateIntakeTest {

    private val suite = SuiteRules.standard()
    private val downloads = DownloadsGuardRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(downloads)

    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("INTAKE-32")
    fun aFileDeletedWhileOpenOffersRelocationOnReload() {
        val fixture = openOwnCopyOfNormal()
        val identity = IntakeFixtures.expectedIdentity(fixture)
        IntakeDb.awaitRecord(identity, Await.LONG_TIMEOUT_MS)

        Fixtures.delete(fixture)
        ReaderActions.reload()

        IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.home_relocate_title), Await.LONG_TIMEOUT_MS)
        IntakeDialogs.awaitMessageContaining("normal")

        val record = IntakeDb.record(identity)
        assertNotNull("the record for a document that vanished must survive", record)
        assertEquals("the record keeps the identity it was created under", identity, record!!.hash)
    }

    @Test
    @TestId("INTAKE-33")
    fun aScannedLibraryEntryHealsABrokenLocationWithoutPrompting() {
        val directory = suite.testDirectory.directory()
        val fixture = IntakeFixtures.copyInto(directory, IntakeFixtures.NORMAL)
        val healed = IntakeFixtures.copyInto(directory, IntakeFixtures.NORMAL, "healed.pdf")
        val identity = IntakeFixtures.expectedIdentity(fixture)
        IntakeDb.insertScannedEntry(healed.absolutePath, identity, healed)

        openFrom(fixture)
        IntakeDb.awaitRecord(identity, Await.LONG_TIMEOUT_MS)

        Fixtures.delete(fixture)
        ReaderActions.reload()

        Snackbars.awaitText(IntakeDialogs.string(R.string.home_relocate_found), Await.LONG_TIMEOUT_MS)
        IntakeDialogs.holdAbsent(IntakeDialogs.string(R.string.home_relocate_title))

        IntakeDb.awaitRecordUri(identity, Uri.fromFile(healed).toString(), Await.LONG_TIMEOUT_MS)

        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        val state = reader.awaitFullQualityRender()
        assertEquals(IntakeFixtures.NORMAL_PAGES as Int?, state.pageCount)
    }

    @Test
    @TestId("INTAKE-34")
    fun relocatingToTheSameFileKeepsTheReadingPosition() {
        val fixture = openOwnCopyOfNormal()
        val identity = IntakeFixtures.expectedIdentity(fixture)
        IntakeDb.awaitRecord(identity, Await.LONG_TIMEOUT_MS)

        ReaderNavigation.jumpToPage(reader, DEEP_PAGE)
        awaitStoredPage(identity, DEEP_PAGE)

        val moved = File(IntakeFixtures.seededRoot, RELOCATED_NAME)
        moveTo(fixture, moved)

        ReaderActions.reload()
        IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.home_relocate_title), Await.LONG_TIMEOUT_MS)
        IntakeDialogs.tap(IntakeDialogs.string(R.string.home_relocate_action))

        picker.pickFromDownloads(RELOCATED_NAME)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        reader.awaitPageIndex(DEEP_PAGE, Await.LONG_TIMEOUT_MS)

        val records = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
        val record = records.single()
        assertEquals("relocation must not change the document identity", identity, record.hash)
        assertEquals(
            "the record must point at the new location",
            Uri.fromFile(moved).toString(),
            record.uri.toString(),
        )
        assertEquals("the reading position must survive the move", DEEP_PAGE, record.pageNumber)
    }

    @Test
    @TestId("INTAKE-35")
    fun relocatingToADifferentFileWarnsBeforeOpeningIt() {
        val fixture = openOwnCopyOfNormal()
        val identity = IntakeFixtures.expectedIdentity(fixture)
        val other = Fixtures.pickerFixture(IntakeFixtures.LARGE)
        IntakeDb.awaitRecord(identity, Await.LONG_TIMEOUT_MS)

        ReaderNavigation.jumpToPage(reader, DEEP_PAGE)
        awaitStoredPage(identity, DEEP_PAGE)
        val originalUri = IntakeDb.record(identity)!!.uri.toString()

        Fixtures.delete(fixture)
        ReaderActions.reload()
        IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.home_relocate_title), Await.LONG_TIMEOUT_MS)
        IntakeDialogs.tap(IntakeDialogs.string(R.string.home_relocate_action))

        picker.pickFromDownloads(other.name)

        IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.home_relocate_mismatch_title), Await.LONG_TIMEOUT_MS)
        IntakeDialogs.tap(IntakeDialogs.string(R.string.home_open_anyway))

        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.awaitState(
            "the reader reports the page count of the file that was actually picked",
            Await.LONG_TIMEOUT_MS,
        ) { it.pageCount == IntakeFixtures.LARGE_PAGES }

        val expectedOtherUri = Uri.fromFile(other).toString()
        val records = Await.untilValue(
            condition = "the picked document is recorded at its own canonical path",
            timeoutMs = Await.LONG_TIMEOUT_MS,
            read = { IntakeDb.records() },
        ) { rows ->
            rows.size == 2 && rows.any { it.uri.toString() == expectedOtherUri }
        }
        assertEquals(
            "the wrong file must get its own identity, never the original's",
            2,
            records.map { it.hash }.toSet().size,
        )
        val original = IntakeDb.record(identity)
        assertNotNull("the original record must survive untouched", original)
        assertEquals("the original keeps its position", DEEP_PAGE, original!!.pageNumber)
        assertEquals("the original keeps its location", originalUri, original.uri.toString())
    }

    @Test
    @TestId("INTAKE-36")
    fun cancellingRelocationExitsAReaderThatWasOpenedByIntent() {
        ActivityWatcher().use { watcher ->
            val fixture = openOwnCopyOfNormal()
            val identity = IntakeFixtures.expectedIdentity(fixture)
            val before = IntakeDb.awaitRecord(identity, Await.LONG_TIMEOUT_MS)

            Fixtures.delete(fixture)
            ReaderActions.reload()
            IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.home_relocate_title), Await.LONG_TIMEOUT_MS)

            IntakeDialogs.tap(IntakeDialogs.string(R.string.cancel))

            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            assertTrue(
                "cancelling recovery must finish a reader opened by intent, observed ${watcher.observed()}",
                watcher.wasFinishingWhenDestroyed(MainActivity::class.java),
            )
            Await.until("the app has no resumed activity left") { Screens.resumedName() == null }

            val after = IntakeDb.record(identity)
            assertNotNull("the record must survive a declined recovery", after)
            assertEquals("the record must be unchanged", before.uri.toString(), after!!.uri.toString())
            assertEquals("the record must be unchanged", before.pageNumber, after.pageNumber)
        }
    }

    private fun openOwnCopyOfNormal(): File {
        val fixture = IntakeFixtures.copyInto(suite.testDirectory.directory(), IntakeFixtures.NORMAL)
        openFrom(fixture)
        return fixture
    }

    private fun openFrom(file: File) {
        IntakeReader.startFromOutside(IntakeReader.fileViewIntent(file))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender()
    }

    private fun moveTo(source: File, target: File) {
        Fixtures.ensureAllFilesAccess()
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        Fixtures.delete(source)
        Fixtures.scanAndWait(target)
    }

    private fun awaitStoredPage(identity: String, pageIndex: Int) {
        Await.untilValue(
            "the stored position reaches page index $pageIndex",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeDb.record(identity)?.pageNumber },
        ) { it == pageIndex }
    }

    private companion object {
        const val DEEP_PAGE = 5
        const val RELOCATED_NAME = "intake-relocated.pdf"
    }
}
