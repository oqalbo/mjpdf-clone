// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.core.debug.ReaderTestHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class IntentIntakeTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("INTAKE-06")
    fun aViewIntentOnAPushedFileOpensTheDocumentAndWritesOneRecord() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)

        ActivityScenario.launch<MainActivity>(IntakeReader.fileViewIntent(fixture)).use {
            reader.awaitShown()
            reader.awaitLoaded()
            val state = reader.awaitFullQualityRender()

            assertEquals("page count of normal.pdf", IntakeFixtures.NORMAL_PAGES as Int?, state.pageCount)
            assertEquals("the document opens on the first page, zero based", 0 as Int?, state.page)

            val record = IntakeDb.awaitRecordCount(1).single()
            assertTrue(
                "the identity must carry the new scheme prefix, read ${record.hash}",
                record.hash.startsWith(IntakeFixtures.IDENTITY_PREFIX),
            )
            assertEquals(
                "the record must point at the file that was opened",
                Uri.fromFile(fixture).toString(),
                record.uri.toString(),
            )
            assertEquals("the record file name carries no .pdf suffix", "normal", record.fileName)
            IntakeReader.awaitActionBarTitle("normal")
        }
    }

    @Test
    @TestId("INTAKE-07")
    fun aViewIntentOnASafContentUriOpensAndCanonicalizesTheRecord() {
        val fixture = Fixtures.pickerFixture(IntakeFixtures.NORMAL)

        IntakeReader.startFromOutside(IntakeReader.pickerIntent())
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        picker.pickFromDownloads(fixture.name)
        reader.awaitShown()
        reader.awaitLoaded()
        reader.awaitFullQualityRender()

        val record = Await.untilValue(
            "the single PdfRecord row is canonicalized to a file path",
            Await.DEFAULT_TIMEOUT_MS,
            read = { IntakeDb.records() },
        ) { it.size == 1 && it.single().uri.scheme == "file" }.single()
        assertEquals(
            "updateRecordUri must resolve the picked content URI to the real path",
            fixture.absolutePath,
            record.uri.path,
        )
        assertNull("no temporary copy was involved, so sourceUri stays null", record.sourceUri)

        val persisted = IntakeReader.persistedPermissions()
        assertEquals("the picked document must leave exactly one persisted grant", 1, persisted.size)
        assertEquals("content", persisted.single().uri.scheme)
        assertTrue("the persisted grant must carry read permission", persisted.single().isReadPermission)
    }

    @Test
    @TestId("INTAKE-08")
    fun aPathContainingADotInAFolderNameStillMatchesTheViewFilter() {
        Fixtures.ensureAllFilesAccess()
        val fixture = File(
            File(IntakeFixtures.seededRoot, IntakeFixtures.DOTTED_FOLDER),
            IntakeFixtures.NORMAL,
        )
        assertTrue("the dotted folder fixture is missing at ${fixture.absolutePath}", fixture.isFile)

        val probe = IntakeReader.implicitViewIntent(Uri.fromFile(fixture))
        val resolved = TestEnvironment.targetContext.packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName == TestEnvironment.targetPackage }
            .map { it.activityInfo.name }
        assertEquals(
            "the pathPattern workaround filters must match a folder name containing a dot",
            listOf("${TestEnvironment.targetPackage}.ui.reader.MainActivity"),
            resolved,
        )

        val launch = Shell.execute(
            "am start -W -a ${Intent.ACTION_VIEW} -d ${Uri.fromFile(fixture)} -p ${TestEnvironment.targetPackage}"
        )
        if (launch.contains("Error:")) {
            throw AssertionError("The outside file view intent was rejected: $launch")
        }

        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        val state = reader.awaitFullQualityRender()
        assertEquals(
            "page count of the dotted folder fixture",
            IntakeFixtures.DOTTED_PAGES as Int?,
            state.pageCount,
        )

        val record = IntakeDb.awaitRecordCount(1).single()
        assertEquals(
            "the record must point at the pushed path",
            Uri.fromFile(fixture).toString(),
            record.uri.toString(),
        )
    }

    @Test
    @TestId("INTAKE-11")
    fun sharedTextWithNoLinkReportsItAndFallsBackToThePicker() {
        IntakeReader.startFromOutside(IntakeReader.sendTextIntent("no link here at all"))
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        IntakeSnackbars.awaitTextIn(MainActivity::class.java, IntakeDialogs.string(R.string.share_text_no_link))
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        IntakeDb.holdEmpty()

        picker.dismissIfShown()
    }

    @Test
    @TestId("INTAKE-50")
    fun aNewIntentForADifferentDocumentReplacesTheCurrentOne() {
        val first = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val second = IntakeFixtures.seeded(IntakeFixtures.LARGE)

        IntakeReader.startFromOutside(IntakeReader.fileViewIntent(first))
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        reader.awaitPageIndex(0)

        IntakeReader.startFromOutside(IntakeReader.fileViewIntent(second))

        reader.awaitState(
            "the reader reports the page count of large.pdf",
            Await.LONG_TIMEOUT_MS,
        ) { it.pageCount == IntakeFixtures.LARGE_PAGES }
        IntakeReader.awaitActionBarTitle("large")

        val records = IntakeDb.awaitRecordCount(2, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "two documents must produce two distinct identities",
            2,
            records.map { it.hash }.toSet().size,
        )
    }

    @Test
    @TestId("INTAKE-51")
    fun aNewIntentForTheSameDocumentDoesNotReloadIt() {
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)

        IntakeReader.startFromOutside(IntakeReader.fileViewIntent(fixture))
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        reader.awaitFullQualityRender()
        IntakeDb.awaitRecordCount(1)

        ReaderNavigation.jumpToPage(reader, 5)
        val loadsBefore = ReaderTestHooks.documentLoadStarts()
        assertTrue("the initial document load was observed", loadsBefore > 0)

        IntakeReader.startFromOutside(IntakeReader.fileViewIntent(fixture))
        reader.awaitShown()
        reader.awaitFullQualityRender()

        Await.assertThroughout(
            condition = "the reader stays on page index 5 after the identical intent",
            windowMs = IntakeDb.QUIET_WINDOW_MS,
        ) {
            assertEquals("the identical intent must not start another document load", loadsBefore, ReaderTestHooks.documentLoadStarts())
            reader.readStateOrNull()?.page == 5
        }

        IntakeDb.holdRecordCount(1)
    }

    @Test
    @TestId("INTAKE-52")
    fun openingASecondDocumentClosesThePreviousReaderWindow() {
        SettingsSeeder.seed(Preferences.openPdfsInSeparateWindowsKey, false)
        val first = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val second = IntakeFixtures.seeded(IntakeFixtures.LARGE)

        IntakeReader.startFromOutside(newDocumentIntent(first))
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded()
        reader.awaitFullQualityRender()

        IntakeReader.startFromOutside(newDocumentIntent(second))
        reader.awaitState(
            "the reader reports the page count of large.pdf",
            Await.LONG_TIMEOUT_MS,
        ) { it.pageCount == IntakeFixtures.LARGE_PAGES }

        Await.untilValue(
            "exactly one reader task remains in the recents list",
            Await.LONG_TIMEOUT_MS,
            read = { readerTaskCount() },
        ) { it == 1 }
    }

    private fun newDocumentIntent(file: File): Intent =
        IntakeReader.fileViewIntent(file).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

    private fun readerTaskCount(): Int {
        val manager = TestEnvironment.targetContext
            .getSystemService(android.app.ActivityManager::class.java)
        return manager.appTasks.count { task ->
            runCatching {
                task.taskInfo.baseIntent.component?.className == MainActivity::class.java.name
            }.getOrDefault(false)
        }
    }
}
