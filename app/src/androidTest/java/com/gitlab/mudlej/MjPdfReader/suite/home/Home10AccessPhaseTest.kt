// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
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
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomePage
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
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class Home10AccessPhaseTest {

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(OnboardingSuppressionRule())
        .around(FailureArtifactRule())
        .around(CrashGateRule())
        .around(ToastRule())

    private val home = HomeRobot()
    private val reader = ReaderRobot()

    @Test
    @TestId("HOME-10")
    fun phaseOneCreateAndOpenRealSharedHistory() {
        requirePhase(1)
        Fixtures.ensureAllFilesAccess()
        Fixtures.deleteFromShell(TARGET_FILE)
        Fixtures.deleteMediaStoreRowsUnder(TARGET_FILE)
        HomeLibrary.clearRecords()

        val seeded = HomeBaselines.scopedLibrary(
            TARGET_DIRECTORY,
            BookSeed(
                fixture = HomeFixtures.GAMMA,
                name = TARGET_NAME,
                record = false,
                indexed = false,
                pages = 1,
            ),
        ).books.single()
        assertEquals("the phase fixture uses the fixed host-visible path", TARGET_FILE, seeded.file)
        Fixtures.replaceWithShellCopy(Fixtures.seededFixture(HomeFixtures.GAMMA), seeded.file)
        val mediaUri = Fixtures.scanFromShellAndWait(seeded.file)
        assertTrue(
            "the target must not hold an exact persisted read grant for the shell-indexed row",
            TestEnvironment.targetContext.contentResolver.persistedUriPermissions.none {
                it.uri == mediaUri && it.isReadPermission
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue(
                "the target package must not own the shell-indexed MediaStore row",
                Fixtures.mediaStoreOwnerPackage(mediaUri) != TestEnvironment.targetPackage,
            )
        }
        assertTrue(
            "the exact shell-indexed MediaStore URI opens while real storage access is granted",
            Fixtures.contentUriCanOpen(mediaUri),
        )
        assertRealAccess(expected = true, fileMustExist = true)

        home.launchFromLauncher()
        home.awaitState("Home commits a render with genuine shared-storage access") {
            it.hasHomeGroup && it.storageAccess == true && (it.generation ?: 0) >= 1
        }

        val library = home.library()
        library.awaitContains(seeded.fileName, Await.LONG_TIMEOUT_MS)

        val openedFrom = home.generation()
        library.cell(seeded.fileName).click()
        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()
        assertTrue("the app process can read the genuine shared PDF before revocation", TARGET_FILE.canRead())

        home.pressBack()
        Screens.awaitTopIs(HomeActivity::class.java)
        home.awaitGenerationAtLeast(openedFrom + 1)
        val record = requireTargetRecord()
        val recent = home.selectTab(HomeTab.RECENT)
        val available = recent.awaitItemWithHash(record.hash, Availability.AVAILABLE)
        assertHistoryMatchesRecord(available, record)
        assertEquals(1.0f, recent.cell(available.title).alpha(), ALPHA_TOLERANCE)
        assertEquals("the available history appears exactly once", 1, recent.items().count { it.hash == record.hash })
        assertEquals("the opened book is eligible for Continue Reading", 1, recent.heroItems().count { it.hash == record.hash })
        assertNull("available history must not raise the Recent permission card", recent.permissionCard())
        writeBaseline(record, available)
    }

    @Test
    @TestId("HOME-10")
    fun phaseTwoRealRevocationRetainsUnknownHistory() {
        requirePhase(2)
        assertRealAccess(expected = false, fileMustExist = true)
        val record = requireTargetRecord()
        assertStoredRecord(record)
        assertUnknownHistory(home.launchRecent(expectedStorageAccess = false), record, "first revocation")
    }

    @Test
    @TestId("HOME-24")
    fun phaseThreeRecoveryTapWhileReallyRevoked() {
        requirePhase(3)
        assertRealAccess(expected = false, fileMustExist = true)
        val record = requireTargetRecord()
        assertStoredRecord(record)
        val recent = home.launchRecent(expectedStorageAccess = false)
        val unknown = assertUnknownHistory(recent, record, "recovery phase")

        val beforeRecoveryTap = HomeLibrary.requireRecord(record.hash)
        recent.cell(unknown.title).click()
        HomeDialogs.awaitConfirmationDialog()
        assertEquals(
            "recovery from genuinely revoked access requests storage permission",
            TestEnvironment.targetContext.getString(R.string.storage_permission_rationale_title),
            HomeDialogs.dialogTitle(),
        )
        assertFalse("the recovery tap must not open the reader", Screens.isInTask(MainActivity::class.java))
        HomeDialogs.tapNegative()
        HomeLibrary.holdRecordUnchanged(beforeRecoveryTap)
        assertStoredRecord(HomeLibrary.requireRecord(record.hash))
    }

    @Test
    @TestId("HOME-10")
    fun phaseFourRealGrantRestoresTheExistingHistory() {
        requirePhase(4)
        assertRealAccess(expected = true, fileMustExist = true)
        val record = requireTargetRecord()
        assertStoredRecord(record)

        val recent = home.launchRecent(expectedStorageAccess = true)
        val restored = recent.awaitItemWithHash(record.hash, Availability.AVAILABLE)
        assertStoredHistory(restored)
        assertEquals("grant restores the existing row without a duplicate", 1, recent.items().count { it.hash == record.hash })
        assertEquals(1.0f, recent.cell(restored.title).alpha(), ALPHA_TOLERANCE)
        assertEquals("the restored row is eligible for Continue Reading", 1, recent.heroItems().count { it.hash == record.hash })
        assertNull("restored access removes the Recent permission card", recent.permissionCard())
        assertEquals("the grant must not manufacture another history row", 1, HomeLibrary.recordCount())
    }

    @Test
    @TestId("HOME-10")
    fun phaseFiveSecondRealRevocationIsAccessUnknownAgain() {
        requirePhase(5)
        assertRealAccess(expected = false, fileMustExist = true)
        val record = requireTargetRecord()
        assertStoredRecord(record)
        assertUnknownHistory(home.launchRecent(expectedStorageAccess = false), record, "second revocation")
    }

    @Test
    @TestId("HOME-71")
    fun phaseSixHome71StartsAccessUnknown() {
        requirePhase(6)
        assertRealAccess(expected = false, fileMustExist = true)
        val record = requireTargetRecord()
        assertStoredRecord(record)
        assertUnknownHistory(home.launchRecent(expectedStorageAccess = false), record, "before host deletion")
    }

    @Test
    @TestId("HOME-71")
    fun phaseSevenHostDeletionCannotBeInferredWhileDenied() {
        requirePhase(7)
        assertRealAccess(expected = false, fileMustExist = false)
        val record = requireTargetRecord()
        assertStoredRecord(record)
        assertUnknownHistory(home.launchRecent(expectedStorageAccess = false), record, "after host deletion while denied")
    }

    @Test
    @TestId("HOME-71")
    fun phaseEightGrantAfterHostDeletionProvesDefinitiveMissing() {
        requirePhase(8)
        assertRealAccess(expected = true, fileMustExist = false)
        val record = requireTargetRecord()
        assertStoredRecord(record)
        val title = storedTitle()

        home.launchFromLauncher()
        home.awaitState("Home commits a render after the final real access grant") {
            it.hasHomeGroup && it.storageAccess == true && (it.generation ?: 0) >= 1
        }

        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitHashAbsent(record.hash)
        recent.awaitAbsent(title)
        assertEquals("a definitively missing book cannot remain in Continue Reading", 0, recent.heroItems().count { it.hash == record.hash })
        assertNull("a definitive missing result does not raise a Recent permission card", recent.permissionCard())

        val library = home.selectTab(HomeTab.LIBRARY)
        library.awaitHashAbsent(record.hash)
        library.awaitAbsent(title)
        assertNull("restored access leaves no Library permission card", library.permissionCard())
        assertEquals("the exact historical database row is retained", record, requireTargetRecord())
        assertEquals("missing-file omission must not duplicate or delete history", 1, HomeLibrary.recordCount())
        Fixtures.deleteMediaStoreRowsUnder(TARGET_DIRECTORY)
    }

    private fun requirePhase(index: Int) {
        PhaseRun.requirePhase(SCENARIO, index)
    }

    private fun assertRealAccess(expected: Boolean, fileMustExist: Boolean) {
        assertFalse(
            "the phase scenario must never substitute StorageAccessOverride for host-driven access evidence",
            StorageAccessOverride.isForced(),
        )
        Await.until("the app process observes the host-installed storage access state $expected") {
            platformAccessGranted() == expected &&
                PermissionManager.hasFullAccess(TestEnvironment.targetContext) == expected
        }
        assertEquals("the shell-visible fixture existence", fileMustExist, Fixtures.shellFileExists(TARGET_FILE))
        if (fileMustExist) {
            Await.until("the app-process readability of the shared fixture becomes $expected") {
                TARGET_FILE.canRead() == expected
            }
        } else {
            assertFalse("the host deleted the shared fixture while access was denied", TARGET_FILE.exists())
        }
    }

    private fun assertUnknownHistory(recent: HomePage, record: PdfRecord, transition: String): HomeItem {
        val unknown = recent.awaitItemWithHash(record.hash, Availability.ACCESS_UNKNOWN)
        assertStoredHistory(unknown)
        assertEquals("$transition retains one historical row", 1, recent.items().count { it.hash == record.hash })
        assertEquals(UNAVAILABLE_ALPHA, recent.cell(unknown.title).alpha(), ALPHA_TOLERANCE)
        assertEquals("$transition excludes the row from Continue Reading", 0, recent.heroItems().count { it.hash == record.hash })
        assertRecentPermissionCard(recent)
        HomeLibrary.holdRecordUnchanged(record)
        return unknown
    }

    private fun HomePage.awaitHashAbsent(hash: String) {
        Await.untilValue(
            "the $tab tab omits the row with hash $hash",
            read = { items().filter { it.hash == hash } },
        ) { it.isEmpty() }
    }

    private fun requireTargetRecord(): PdfRecord = HomeLibrary.recordNamed(TARGET_NAME)
        ?: throw AssertionError(
            "No history row exists for $TARGET_NAME. Rows are: " +
                HomeLibrary.records().joinToString { "${it.fileName} at ${it.uri}" },
        )

    private fun writeBaseline(record: PdfRecord, item: HomeItem) {
        recordBaselineFile().writeText(record.toString())
        itemBaselineFile().writeText(historyFingerprint(item))
        titleBaselineFile().writeText(item.title)
    }

    private fun assertStoredRecord(record: PdfRecord) {
        assertEquals("the exact history row written in phase one survives", recordBaselineFile().readText(), record.toString())
    }

    private fun assertStoredHistory(item: HomeItem) {
        assertEquals("all phase-one history fields survive the access transition", itemBaselineFile().readText(), historyFingerprint(item))
    }

    private fun storedTitle(): String = titleBaselineFile().readText()

    private fun recordBaselineFile(): File = File(TestEnvironment.targetContext.filesDir, RECORD_BASELINE)

    private fun itemBaselineFile(): File = File(TestEnvironment.targetContext.filesDir, ITEM_BASELINE)

    private fun titleBaselineFile(): File = File(TestEnvironment.targetContext.filesDir, TITLE_BASELINE)

    private companion object {
        const val SCENARIO = "HOME-10-ACCESS"
        const val TARGET_NAME = "HOME-10-ACCESS.pdf"
        const val RECORD_BASELINE = "home-10-access-record.txt"
        const val ITEM_BASELINE = "home-10-access-item.txt"
        const val TITLE_BASELINE = "home-10-access-title.txt"
        const val UNAVAILABLE_ALPHA = 0.45f
        const val ALPHA_TOLERANCE = 0.001f

        val TARGET_DIRECTORY = File(Fixtures.externalRoot, "Documents/mjpdf-home10-access")
        val TARGET_FILE = File(TARGET_DIRECTORY, TARGET_NAME)
    }
}
