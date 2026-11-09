// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.home

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.lifecycle.lifecycleScope
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.core.debug.StorageAccessOverride
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.robot.HomeRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.home.Availability
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeActivity
import com.gitlab.mudlej.MjPdfReader.ui.home.HomeTab
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class ProviderDocumentsTest {

    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val homeCleanup = HomeCleanupRule()

    private val home = HomeRobot()
    private val reader = ReaderRobot()
    private val picker = SafPickerRobot()

    @Test
    @TestId("HOME-12")
    fun anOpenablePersistedContentUriRemainsAvailableWithoutAllFilesAccess() = Fixtures.withCrowdedDownloads {
        val baseline = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
        )
        val picked = Fixtures.pickerFixture(HomeFixtures.BANANA)

        home.launchFromLauncher()

        home.library().awaitTitles(listOf("alpha"))

        val contentUri = openThroughThePicker(picked.name)
        assertPersistedReadGrant(contentUri)
        val providerRecord = HomeLibrary.records().single { it.hash != baseline.hashOf("alpha") }
        HomeLibrary.replaceRecordUri(providerRecord.hash, contentUri)

        home.selectTab(HomeTab.RECENT)
        val deniedFrom = home.readState()
        denyAccessDuringPendingRecentLayout()
        HomeBaselines.simulateDeniedAccess(Fixtures.seededFixture(HomeFixtures.BANANA))
        home.resumeExistingTaskAfter(deniedFrom, expectedStorageAccess = false)

        val recent = home.selectTab(HomeTab.RECENT)
        val providerItem = recent.awaitItemWithHash(providerRecord.hash, Availability.AVAILABLE)
        assertEquals(1.0f, recent.cell(providerItem.title).alpha(), ALPHA_TOLERANCE)
        assertNull(
            "an actually openable persisted URI must not raise the Recent access card",
            recent.permissionCard(),
        )

        home.selectTab(HomeTab.LIBRARY)
        home.tapChip(R.id.chipReading)
        setStatusOfProviderDocument(providerItem.title)
        home.library().awaitTitles(listOf(providerItem.title))
    }

    @Test
    @TestId("HOME-72")
    fun aStalePersistedContentUriIsMissingRatherThanAccessUnknown() {
        val baseline = HomeBaselines.scopedLibrary(
            rules.testDirectory.directory(),
            BookSeed(HomeFixtures.ALPHA, pages = 1),
        )
        val disposable = Fixtures.pickerFixture(HomeFixtures.BANANA)

        home.launchFromLauncher()

        val contentUri = openThroughThePicker(disposable.name)
        assertPersistedReadGrant(contentUri)
        val providerRecord = HomeLibrary.records().single { it.hash != baseline.hashOf("alpha") }
        val pinned = HomeLibrary.replaceRecordUri(providerRecord.hash, contentUri)

        val pinnedFrom = home.readState()
        home.resumeExistingTaskAfter(pinnedFrom, expectedStorageAccess = true)
        val recent = home.selectTab(HomeTab.RECENT)
        val available = recent.awaitItemWithHash(pinned.hash, Availability.AVAILABLE)

        Fixtures.deleteFromShell(disposable)
        val deletedFrom = home.readState()
        home.resumeExistingTaskAfter(deletedFrom, expectedStorageAccess = true)

        recent.awaitAbsent(available.title)
        val library = home.selectTab(HomeTab.LIBRARY)
        library.awaitAbsent(available.title)
        assertEquals(0, library.items().count { it.hash == pinned.hash })
        assertEquals("the stale URI keeps its historical row", pinned, HomeLibrary.requireRecord(pinned.hash))
        assertNull("a stale persisted URI is missing, not permission-blocked", recent.permissionCard())
    }

    private fun denyAccessDuringPendingRecentLayout() {
        Await.until("access changes while Recent has a controlled layout continuation", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.onMainThread {
                val activity = TestEnvironment.resumedActivityOfType(HomeActivity::class.java) as? HomeActivity
                    ?: return@onMainThread false
                val recentTab = activity.recentTabForTest
                if (recentTab.isRenderingForTest) return@onMainThread false
                val heldRender = activity.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    recentTab.holdRenderUntilAccessInvalidatedForTest()
                }
                try {
                    assertTrue("the Recent layout continuation is held before access changes", recentTab.isRenderingForTest)
                    StorageAccessOverride.force(false)
                    activity.renderingForTest.onStorageAccessChanged(false)
                    assertTrue("invalidating the layout resumes the render immediately", heldRender.isCompleted)
                } finally {
                    heldRender.cancel()
                }
                true
            }
        }
    }

    private fun openThroughThePicker(fixtureName: String): Uri {
        val recordsBefore = HomeLibrary.recordCount()
        home.tapVisible(R.id.openPdfFab)
        picker.awaitShown()
        picker.navigateToDownloads().chooseFileNamedInCurrentFolder(fixtureName, Await.DEFAULT_TIMEOUT_MS)

        Screens.awaitResumed(MainActivity::class.java)
        reader.awaitShown()
        reader.awaitLoaded()
        reader.awaitFileHashResolved()
        val uri = TestEnvironment.onMainThread {
            TestEnvironment.resumedActivityOfType(MainActivity::class.java)?.intent?.data
        } ?: throw AssertionError("the picker-opened reader intent carries no content URI")

        home.pressBack()
        Screens.awaitTopIs(HomeActivity::class.java)
        HomeLibrary.awaitRecordCount(recordsBefore + 1)
        return uri
    }

    private fun assertPersistedReadGrant(uri: Uri) {
        assertTrue(
            "an exact persisted read grant must exist for $uri",
            TestEnvironment.targetContext.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            },
        )
    }

    private fun setStatusOfProviderDocument(title: String) {
        val recent = home.selectTab(HomeTab.RECENT)
        recent.awaitContains(title)
        recent.cell(title).tapOptions()
        HomeDialogs.awaitOptionsDialog()
        HomeDialogs.chooseStatus(
            TestEnvironment.targetContext.getString(R.string.home_chip_reading)
        )
        home.pressBack()
        HomeDialogs.awaitOptionsDialogGone()
        home.selectTab(HomeTab.LIBRARY)
    }

    private companion object {
        const val ALPHA_TOLERANCE = 0.001f
    }
}
