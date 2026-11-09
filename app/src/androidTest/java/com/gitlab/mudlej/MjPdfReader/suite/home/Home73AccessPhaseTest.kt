// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.core.debug.StorageAccessOverride
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.history.PhaseRun
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.CrashGateRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.FailureArtifactRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.OnboardingSuppressionRule
import com.gitlab.mudlej.MjPdfReader.suite.rules.ToastRule
import com.gitlab.mudlej.MjPdfReader.ui.home.Availability
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeItem
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@TestId("HOME-73")
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class Home73AccessPhaseTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(OnboardingSuppressionRule())
        .around(FailureArtifactRule())
        .around(CrashGateRule())
        .around(ToastRule())

    private val home = HomeRobot()
    private val reader = ReaderRobot()

    @Test
    fun phaseOneCreateShellOwnedMediaHistory() {
        requirePhase(1)
        Fixtures.ensureAllFilesAccess()
        Fixtures.deleteFromShell(TARGET_FILE)
        Fixtures.deleteMediaStoreRowsUnder(TARGET_FILE)
        HomeLibrary.clearRecords()

        val book = HomeBaselines.scopedLibrary(
            TARGET_DIRECTORY,
            BookSeed(
                fixture = HomeFixtures.GAMMA,
                name = TARGET_NAME,
                record = false,
                indexed = false,
                pages = 1,
            ),
        ).books.single()
        Fixtures.replaceWithShellCopy(Fixtures.seededFixture(HomeFixtures.GAMMA), book.file)
        val mediaUri = Fixtures.scanFromShellAndWait(book.file)
        assertRealAccess(expected = true)
        assertNoPersistedReadGrant(mediaUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue(
                "the target package must not own the shell-indexed MediaStore row",
                Fixtures.mediaStoreOwnerPackage(mediaUri) != TestEnvironment.targetPackage,
            )
        }
        assertTrue("the exact shell-owned MediaStore URI opens before revocation", Fixtures.contentUriCanOpen(mediaUri))

        home.launchFromLauncher()

        val library = home.library()
        library.awaitContains(book.fileName, Await.LONG_TIMEOUT_MS)
        val openedFrom = home.generation()
        library.cell(book.fileName).click()
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()
        reader.awaitFileHashResolved()
        home.pressBack()
        Screens.awaitTopIs(HomeActivity::class.java)
        home.awaitGenerationAtLeast(openedFrom + 1)

        val fileRecord = HomeLibrary.awaitRecord(book.hash)
        val pinned = HomeLibrary.replaceRecordUri(fileRecord.hash, mediaUri)
        val pinnedFrom = home.readState()
        home.resumeExistingTaskAfter(pinnedFrom, expectedStorageAccess = true)
        val recent = home.selectTab(HomeTab.RECENT)
        val available = recent.awaitItemWithHash(pinned.hash, Availability.AVAILABLE)
        assertHistoryMatchesRecord(available, pinned)
        assertEquals(1, recent.items().count { it.hash == pinned.hash })
        assertEquals(1.0f, recent.cell(available.title).alpha(), ALPHA_TOLERANCE)
        assertEquals(1, recent.heroItems().count { it.hash == pinned.hash })
        assertNull("available MediaStore history raises no Recent permission card", recent.permissionCard())
        writeBaseline(pinned, available, mediaUri)
    }

    @Test
    fun phaseTwoRealRevocationMakesMediaHistoryUnknown() {
        requirePhase(2)
        assertRealAccess(expected = false)
        val mediaUri = storedMediaUri()
        assertTrue("the shell retains the MediaStore backing file", Fixtures.shellFileExists(TARGET_FILE))
        Await.until("the genuinely revoked app cannot open the exact MediaStore URI") {
            !Fixtures.contentUriCanOpen(mediaUri)
        }
        assertNoPersistedReadGrant(mediaUri)
        val record = requireTargetRecord()
        assertStoredRecord(record)

        val recent = home.launchRecent(expectedStorageAccess = false)
        val unknown = recent.awaitItemWithHash(record.hash, Availability.ACCESS_UNKNOWN)
        assertStoredHistory(unknown)
        assertEquals("the unknown MediaStore history remains exactly once", 1, recent.items().count { it.hash == record.hash })
        assertEquals(UNAVAILABLE_ALPHA, recent.cell(unknown.title).alpha(), ALPHA_TOLERANCE)
        assertEquals("unknown MediaStore history is excluded from Continue Reading", 0, recent.heroItems().count { it.hash == record.hash })
        assertRecentPermissionCard(recent)

        val beforeRecoveryTap = HomeLibrary.requireRecord(record.hash)
        recent.cell(unknown.title).click()
        HomeDialogs.awaitConfirmationDialog(Await.LONG_TIMEOUT_MS)
        assertEquals(
            TestEnvironment.targetContext.getString(R.string.storage_permission_rationale_title),
            HomeDialogs.dialogTitle(),
        )
        assertFalse("MediaStore recovery must not open the reader", Screens.isInTask(MainActivity::class.java))
        HomeDialogs.tapNegative()
        HomeLibrary.holdRecordUnchanged(beforeRecoveryTap)
        assertStoredRecord(HomeLibrary.requireRecord(record.hash))
    }

    @Test
    fun phaseThreeRealGrantRestoresMediaHistory() {
        requirePhase(3)
        assertRealAccess(expected = true)
        val mediaUri = storedMediaUri()
        Await.until("the real grant reopens the exact MediaStore URI") {
            Fixtures.contentUriCanOpen(mediaUri)
        }
        assertNoPersistedReadGrant(mediaUri)
        val record = requireTargetRecord()
        assertStoredRecord(record)

        val recent = home.launchRecent(expectedStorageAccess = true)
        val restored = recent.awaitItemWithHash(record.hash, Availability.AVAILABLE)
        assertStoredHistory(restored)
        assertEquals("grant restores the same MediaStore row without a duplicate", 1, recent.items().count { it.hash == record.hash })
        assertEquals(1.0f, recent.cell(restored.title).alpha(), ALPHA_TOLERANCE)
        assertEquals("restored MediaStore history returns to Continue Reading", 1, recent.heroItems().count { it.hash == record.hash })
        assertNull("restored MediaStore history raises no permission card", recent.permissionCard())
        assertEquals("the phase transition preserves one exact database row", 1, HomeLibrary.recordCount())
        Fixtures.deleteMediaStoreRowsUnder(TARGET_DIRECTORY)
    }

    private fun requirePhase(index: Int) {
        PhaseRun.requirePhase(SCENARIO, index)
    }

    private fun assertRealAccess(expected: Boolean) {
        assertFalse(
            "the phase scenario must never substitute StorageAccessOverride for host-driven access evidence",
            StorageAccessOverride.isForced(),
        )
        Await.until("the app process observes the host-installed storage access state $expected") {
            platformAccessGranted() == expected &&
                PermissionManager.hasFullAccess(TestEnvironment.targetContext) == expected &&
                TARGET_FILE.canRead() == expected
        }
    }

    private fun assertNoPersistedReadGrant(uri: Uri) {
        assertTrue(
            "the target must not hold an exact persisted read grant for $uri",
            TestEnvironment.targetContext.contentResolver.persistedUriPermissions.none {
                it.uri == uri && it.isReadPermission
            },
        )
    }

    private fun requireTargetRecord(): PdfRecord = HomeLibrary.recordNamed(TARGET_NAME)
        ?: throw AssertionError("No history row exists for $TARGET_NAME: ${HomeLibrary.records()}")

    private fun writeBaseline(record: PdfRecord, item: HomeItem, mediaUri: Uri) {
        recordBaselineFile().writeText(record.toString())
        itemBaselineFile().writeText(historyFingerprint(item))
        mediaUriFile().writeText(mediaUri.toString())
    }

    private fun assertStoredRecord(record: PdfRecord) {
        assertEquals("the exact MediaStore history row survives", recordBaselineFile().readText(), record.toString())
    }

    private fun assertStoredHistory(item: HomeItem) {
        assertEquals("all MediaStore history fields survive", itemBaselineFile().readText(), historyFingerprint(item))
    }

    private fun storedMediaUri(): Uri = Uri.parse(mediaUriFile().readText())

    private fun recordBaselineFile(): File = File(TestEnvironment.targetContext.filesDir, RECORD_BASELINE)

    private fun itemBaselineFile(): File = File(TestEnvironment.targetContext.filesDir, ITEM_BASELINE)

    private fun mediaUriFile(): File = File(TestEnvironment.targetContext.filesDir, MEDIA_URI_BASELINE)

    private companion object {
        const val SCENARIO = "HOME-73-ACCESS"
        const val TARGET_NAME = "HOME-73-ACCESS.pdf"
        const val RECORD_BASELINE = "home-73-access-record.txt"
        const val ITEM_BASELINE = "home-73-access-item.txt"
        const val MEDIA_URI_BASELINE = "home-73-access-uri.txt"
        const val UNAVAILABLE_ALPHA = 0.45f
        const val ALPHA_TOLERANCE = 0.001f

        val TARGET_DIRECTORY = File(Fixtures.externalRoot, "Documents/mjpdf-home73-access")
        val TARGET_FILE = File(TARGET_DIRECTORY, TARGET_NAME)
    }
}
