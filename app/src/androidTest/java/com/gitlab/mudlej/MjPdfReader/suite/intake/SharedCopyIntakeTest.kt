// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.intake

import com.gitlab.mudlej.MjPdfReader.suite.core.FixtureResource
import com.gitlab.mudlej.MjPdfReader.suite.core.RequiresFixture
import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.text.format.Formatter
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.SharedCopyMode
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class SharedCopyIntakeTest {

    private val suite = SuiteRules.standard()
    private val downloads = DownloadsGuardRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(downloads)

    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    @After
    fun clearStagedShares() {
        StubSharer.clear()
    }

    @Test
    @TestId("INTAKE-37")
    @TestDuration(DurationCategory.NORMAL)
    fun aRevokedContentGrantEntersRecoveryInsteadOfFailingBlankly() {
        SettingsSeeder.seed(Preferences.sharedCopyModeKey, SharedCopyMode.ASK.name)
        val fixture = IntakeFixtures.seeded(IntakeFixtures.NORMAL)
        val context = TestEnvironment.targetContext
        val resolver = context.contentResolver
        lateinit var shared: Uri

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            picker.awaitShown()
            picker.pickFromDownloads(fixture.name)
            val loaded = reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            shared = Uri.parse(checkNotNull(loaded.documentUri))
            assertEquals("the system picker supplies a content URI", "content", shared.scheme)
            val provider = checkNotNull(
                context.packageManager.resolveContentProvider(checkNotNull(shared.authority), 0)
            )
            assertTrue(
                "the provider must belong to another UID",
                provider.applicationInfo.uid != Process.myUid(),
            )
            assertTrue(
                "the picker grant is persisted",
                resolver.persistedUriPermissions.any { grant -> grant.uri == shared && grant.isReadPermission },
            )
            resolver.openFileDescriptor(shared, "r").use { descriptor -> assertNotNull(descriptor) }
            IntakeDb.awaitRecordCount(1)
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        val originalHash = IntakeDb.awaitRecordCount(1).single().hash
        resolver.releasePersistableUriPermission(shared, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertTrue("revocation must not delete the source PDF", fixture.isFile)
        val denied = runCatching { resolver.openFileDescriptor(shared, "r").use { } }.exceptionOrNull()
        assertTrue(
            "the revoked provider grant must deny reads, observed $denied",
            denied is SecurityException,
        )

        val intent = Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
            .setDataAndType(shared, "application/pdf")
        ActivityScenario.launch<MainActivity>(intent).use {
            Await.until("the revoked document is recovered or relocation is offered", Await.LONG_TIMEOUT_MS) {
                reader.readStateOrNull()?.loadState == DocumentLoadState.LOADED ||
                    IntakeDialogs.isShowing(IntakeDialogs.string(R.string.home_relocate_title))
            }
            if (reader.readStateOrNull()?.loadState == DocumentLoadState.LOADED) {
                assertEquals("recovery reopens the same document", originalHash, reader.awaitFileHashResolved())
                reader.awaitFullQualityRender()
            }
            assertEquals(
                "recovery preserves the original history record",
                originalHash,
                IntakeDb.awaitRecordCount(1).single().hash,
            )
            Toasts.assertNoneWithin(IntakeDialogs.string(R.string.file_opening_error))
        }
    }

    @Test
    @TestId("INTAKE-43")
    fun aTemporarilySharedFileIsCopiedAndTheRecordRepointed() {
        StubSharer.requireInstalled()
        val shared = StubSharer.publishAsset(SHARED_NAME, ASSET_NAME)
        val sharedBytes = stagedSize(ASSET_NAME)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            Snackbars.awaitText(IntakeDialogs.string(R.string.shared_copy_saved_public), Await.LONG_TIMEOUT_MS)
            IntakeDialogs.holdAbsent(IntakeDialogs.string(R.string.copy_consent_title))

            val copy = IntakeReader.awaitDurableCopy(SHARED_NAME)
            assertEquals(
                "the durable copy must land in the app's copy folder",
                IntakeReader.DURABLE_COPY_PATH,
                copy.relativePath,
            )
            assertEquals("the durable copy must be the whole document", sharedBytes, copy.sizeBytes)

            val record = Await.untilValue(
                "the record is repointed at the durable copy",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeDb.records().singleOrNull() },
            ) { it != null && it.sourceUri == shared.toString() }!!
            assertEquals(
                "the record must point at the durable copy",
                copy.uri,
                record.uri,
            )
        }
    }

    @Test
    @TestId("INTAKE-44")
    fun askModeOffersTheChoiceAndJustReadItCopiesNothing() {
        StubSharer.requireInstalled()
        SettingsSeeder.seed(Preferences.sharedCopyModeKey, SharedCopyMode.ASK.name)
        val shared = StubSharer.publishAsset(SHARED_NAME, ASSET_NAME)
        val sharedBytes = stagedSize(ASSET_NAME)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.copy_consent_title), Await.LONG_TIMEOUT_MS)

            IntakeDialogs.awaitMessageContaining(
                Formatter.formatShortFileSize(TestEnvironment.targetContext, sharedBytes)
            )
            assertTrue(
                "the keep a copy button must be offered",
                IntakeDialogs.isShowing(IntakeDialogs.string(R.string.copy_consent_copy)),
            )
            assertTrue(
                "the grant access button must be offered",
                IntakeDialogs.isShowing(IntakeDialogs.string(R.string.copy_consent_grant)),
            )
            assertTrue(
                "the just read it button must be offered",
                IntakeDialogs.isShowing(IntakeDialogs.string(R.string.copy_consent_just_open)),
            )
            onView(withId(android.R.id.button1)).check(matches(isEnabled()))

            IntakeDialogs.tap(IntakeDialogs.string(R.string.copy_consent_just_open))

            IntakeReader.holdNoDurableCopy()
            assertTrue(
                "declining the copy must leave the private import folder empty",
                IntakeReader.importedFiles().isEmpty(),
            )

            val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
            assertEquals(
                "the record must still point at the shared URI",
                shared.toString(),
                record.uri.toString(),
            )
            assertNull("nothing was copied, so sourceUri stays null", record.sourceUri)
        }
    }

    @Test
    @TestId("INTAKE-45")
    fun askModeKeepACopySavesTheFileAndRepointsTheRecord() {
        StubSharer.requireInstalled()
        SettingsSeeder.seed(Preferences.sharedCopyModeKey, SharedCopyMode.ASK.name)
        val shared = StubSharer.publishAsset(SHARED_NAME, ASSET_NAME)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.copy_consent_title), Await.LONG_TIMEOUT_MS)
            IntakeDialogs.tap(IntakeDialogs.string(R.string.copy_consent_copy))

            Snackbars.awaitText(IntakeDialogs.string(R.string.shared_copy_saved_public), Await.LONG_TIMEOUT_MS)
            val copy = IntakeReader.awaitDurableCopy(SHARED_NAME)

            val record = Await.untilValue(
                "the record is repointed at the durable copy",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeDb.records().singleOrNull() },
            ) { it != null && it.sourceUri == shared.toString() }!!
            assertEquals(
                "the record must point at the durable copy",
                copy.uri,
                record.uri,
            )
        }
    }

    @Test
    @TestId("INTAKE-46")
    @RequiresFixture(FixtureResource.HUGE_PDF)
    fun aVeryLargeSharedFileAsksBeforeCopyingEvenInAlwaysCopyMode() {
        StubSharer.requireInstalled()
        val huge = IntakeFixtures.seeded(HUGE_NAME)
        assertTrue(
            "huge.pdf must exceed the large file threshold, it is ${huge.length()} bytes",
            huge.length() > LARGE_FILE_BYTES,
        )
        val shared = StubSharer.register(HUGE_NAME, huge)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.VERY_LONG_TIMEOUT_MS)

            IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.copy_consent_title), Await.VERY_LONG_TIMEOUT_MS)
            IntakeDialogs.tap(IntakeDialogs.string(R.string.copy_consent_just_open))

            IntakeReader.holdNoDurableCopy()
        }
    }

    @Test
    @TestId("INTAKE-47")
    @SdkSuppress(minSdkVersion = 30)
    @TestDuration(DurationCategory.NORMAL)
    fun theConsentDialogCanSendTheUserToTheFileAccessSettings() {
        StubSharer.requireInstalled()
        Fixtures.simulateNoAllFilesAccess()
        assertFalse(
            "the app-level storage access override must read as absent before the consent dialog is reached, " +
                "otherwise PermissionManager.requestFullAccess returns early and the rationale dialog " +
                "this test drives never appears",
            PermissionManager.hasFullAccess(TestEnvironment.targetContext),
        )
        SettingsSeeder.seed(Preferences.sharedCopyModeKey, SharedCopyMode.ASK.name)
        val shared = StubSharer.publishAsset(SHARED_NAME, ASSET_NAME)

        try {
            ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
                reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
                IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.copy_consent_title), Await.LONG_TIMEOUT_MS)
                IntakeDialogs.tap(IntakeDialogs.string(R.string.copy_consent_grant))

                IntakeDialogs.awaitShowing(
                    IntakeDialogs.string(R.string.storage_permission_rationale_title),
                    Await.LONG_TIMEOUT_MS,
                )
                IntakeDialogs.tap(IntakeDialogs.string(R.string.storage_permission_continue))

                Await.until("the system settings screen comes to the foreground", Await.LONG_TIMEOUT_MS) {
                    TestEnvironment.uiDevice.currentPackageName?.contains("settings", ignoreCase = true) == true
                }

                TestEnvironment.uiDevice.pressBack()
                TestEnvironment.uiDevice.pressBack()
                TestEnvironment.uiDevice.waitForIdle()

                IntakeReader.holdNoDurableCopy()
            }
        } finally {
            Fixtures.stopSimulatingAccess()
        }
    }

    @Test
    @TestId("INTAKE-48")
    fun aPickerOpenedDocumentIsNeverTreatedAsATemporaryShare() {
        val picked = Fixtures.pickerFixture(IntakeFixtures.NORMAL).name

        IntakeReader.startFromOutside(IntakeReader.pickerIntent())
        Screens.awaitResumed(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        picker.pickFromDownloads(picked)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender()

        val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()

        IntakeReader.holdNoDurableCopy()
        assertTrue(
            "a picked document must not be imported into the private copy folder",
            IntakeReader.importedFiles().isEmpty(),
        )
        assertNull("a picked document is not a temporary share, so sourceUri stays null", record.sourceUri)

        val persisted = IntakeReader.persistedPermissions()
        assertEquals("the picked document must leave exactly one persisted grant", 1, persisted.size)
        assertTrue("the persisted grant must carry read permission", persisted.single().isReadPermission)
    }

    @Test
    @TestId("INTAKE-57")
    @TestDuration(DurationCategory.NORMAL)
    fun legacyKeepCopiesOffFallsBackToTheConsentDialog() {
        StubSharer.requireInstalled()
        SettingsSeeder.remove(Preferences.sharedCopyModeKey)
        SettingsSeeder.seed(Preferences.keepSharedCopiesKey, false)
        val shared = StubSharer.publishAsset(SHARED_NAME, ASSET_NAME)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)

            assertFalse(
                "the explicit replacement mode must stay absent so the legacy fallback remains under test",
                SettingsSeeder.preferences.contains(Preferences.sharedCopyModeKey),
            )
            IntakeDialogs.awaitShowing(IntakeDialogs.string(R.string.copy_consent_title), Await.LONG_TIMEOUT_MS)
            IntakeReader.holdNoDurableCopy()

            IntakeDialogs.tap(IntakeDialogs.string(R.string.copy_consent_just_open))

            IntakeReader.holdNoDurableCopy()
            val record = IntakeDb.awaitRecordCount(1, Await.LONG_TIMEOUT_MS).single()
            assertEquals("just reading keeps the temporary shared URI", shared, record.uri)
            assertNull("the legacy ASK fallback produces no durable source copy", record.sourceUri)
        }
    }

    private fun stagedSize(assetName: String): Long =
        TestEnvironment.testContext.assets.open(assetName).use { it.readBytes().size.toLong() }

    private companion object {
        const val SHARED_NAME = "normal.pdf"
        const val ASSET_NAME = "normal.pdf"
        const val HUGE_NAME = "huge.pdf"
        const val LARGE_FILE_BYTES = 250L * 1024 * 1024
    }
}
