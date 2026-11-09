// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.privacy

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ScanLifecycle
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.core.Toasts
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.rules.TestDirectoryRule
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.VERY_LONG)
class IncognitoStoreSuppressionTest {

    private val suite = SuiteRules.standard()
    private val directory = TestDirectoryRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(directory)

    private val reader = PrivacyReader()
    private val home = PrivacyHome()

    @After
    fun leaveNoTaskBehind() {
        reader.finishTask()
        home.picker.dismissIfShown()
    }

    @Test
    @TestId("PRIV-01")
    fun anIncognitoOpenWritesNoPdfRecordRow() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        home.launch()
        PrivacyStores.awaitRecordCount(0)

        home.longPressOpenFab()
        Toasts.awaitText(string(R.string.open_in_incognito_hint))
        home.picker.awaitShown(Await.LONG_TIMEOUT_MS)
        home.pickFromDownloads(PrivacyFixtures.NORMAL)

        reader.awaitReady()
        reader.awaitIncognitoIcon(visible = true)
        reader.awaitIdentity()
        reader.jumpToPageIndex(READ_TO_PAGE_INDEX)

        PrivacyStores.holdNoRecords()
        reader.finishTask()

        home.reopen()
        assertTrue(
            "the Recent tab must show its empty state, it lists ${home.recentTitles()}",
            home.recentHasEmptyState(),
        )
        assertEquals(
            "an incognito open must leave no row in the library",
            0,
            PrivacyStores.recordCount(),
        )

        home.openNormallyFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIdentity()
        val rows = PrivacyStores.awaitRecordCount(1, Await.LONG_TIMEOUT_MS)
        assertEquals(
            "the control pass must record the same document, otherwise the incognito assertion proves nothing",
            PrivacyFixtures.NORMAL_TITLE,
            rows.single().fileName,
        )
    }

    @Test
    @TestId("PRIV-02")
    @TestDuration(DurationCategory.LONG)
    fun anIncognitoOpenWritesNoAnnotationJournalFile() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        home.launch()
        home.openIncognitoFromDownloads(PrivacyFixtures.NORMAL)

        reader.awaitReady()
        reader.awaitIdentity()
        val documentUri = reader.documentUri()

        reader.createHighlightOnCurrentPage()
        reader.awaitDirtyFab(visible = true)

        PrivacyStores.drainJournalWriter(documentUri)
        assertEquals(
            "the journal for ${PrivacyStores.journalFor(documentUri).name} must not exist after an incognito edit",
            emptyList<String>(),
            PrivacyStores.journalFiles().map { it.name },
        )
        PrivacyStores.holdNoJournal()
        assertTrue(
            "the highlight must still live in memory, so the unsaved indicator stays visible",
            reader.isDirtyFabVisible(),
        )
    }

    @Test
    @TestId("PRIV-04")
    fun anIncognitoOpenTakesNoPersistedUriGrant() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        home.launch()
        assertEquals(
            "the persisted grant list must start empty on a clean install",
            emptyList<String>(),
            PrivacyStores.persistedUris(),
        )

        home.openIncognitoFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIdentity()

        PrivacyStores.holdNoPersistedGrants()
        assertEquals(
            "picking a document in incognito must leave no permanent read access",
            emptyList<String>(),
            PrivacyStores.persistedUris(),
        )

        reader.finishTask()
        home.reopen()
        home.openNormallyFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIdentity()

        val grants = Await.untilValue(
            "the same pick without incognito does take one persisted read grant",
            Await.LONG_TIMEOUT_MS,
            read = { PrivacyStores.persistedUris() },
        ) { it.size == 1 }
        assertEquals(
            "the control pass must take exactly one grant, otherwise the incognito assertion proves nothing",
            1,
            grants.size,
        )
    }

    @Test
    @TestId("PRIV-05")
    fun anIncognitoOpenWritesNoCoverCacheEntry() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        PrivacyFixtures.file(PrivacyFixtures.SECOND)
        home.listByFileName()

        home.launch()
        home.openNormallyFromDownloads(PrivacyFixtures.SECOND)
        reader.awaitReady()
        reader.awaitIdentity()
        reader.finishTask()

        home.reopen()
        home.home.awaitRendered(Await.LONG_TIMEOUT_MS)
        home.home.recent().cell(PrivacyFixtures.SECOND_TITLE).awaitCover(Await.LONG_TIMEOUT_MS)
        val covered = Await.untilValue(
            "a cover is cached for the document opened normally",
            Await.LONG_TIMEOUT_MS,
            read = { PrivacyStores.coverFiles().map { it.name } },
        ) { it.isNotEmpty() }
        assertTrue("the control pass must cache at least one cover", covered.isNotEmpty())

        home.startIncognitoPick()
        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.TIRAMISU) {
            placePickerRowAcrossNavigationBar(PrivacyFixtures.NORMAL)
        }
        home.pickFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIdentity()
        val incognitoHash = PrivacyFixtures.identityOf(PrivacyFixtures.file(PrivacyFixtures.NORMAL))
        reader.finishTask()

        home.reopen()
        home.home.awaitRendered(Await.LONG_TIMEOUT_MS)
        home.recentTitles()

        PrivacyStores.hold("no cover is ever cached for the incognito document $incognitoHash") {
            PrivacyStores.coverFilesFor(incognitoHash).isEmpty()
        }
        assertEquals(
            "no cover thumbnail may be left on disk for an incognito document, while the normally opened " +
                "one still has ${PrivacyStores.coverFiles().map { it.name }}",
            emptyList<String>(),
            PrivacyStores.coverFilesFor(incognitoHash).map { it.name },
        )
    }

    @Test
    @TestId("PRIV-07")
    @TestDuration(DurationCategory.LONG)
    fun anIncognitoOpenWritesNoScannedPdfEntryRow() {
        PrivacyFixtures.file(PrivacyFixtures.NORMAL)
        val scanned = PrivacyFixtures.copyInto(
            directory.directory(),
            PrivacyFixtures.SECOND,
        )
        Fixtures.scanAndWait(scanned)
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.scanModeKey to ScanMode.SELECTED_LOCATIONS.name,
                Preferences.scanLocationsKey to setOf(directory.directory().absolutePath),
            )
        )

        val scanGeneration = ScanLifecycle.generation()
        home.launch()
        ScanLifecycle.awaitCompletedAfter(scanGeneration)
        val control = PrivacyStores.awaitScannedEntry(scanned.absolutePath)
        assertEquals(
            "the completed control scan indexes the exact configured document",
            scanned.absolutePath,
            control.path,
        )

        val incognitoPath = PrivacyStores.downloadsFile(PrivacyFixtures.NORMAL).absolutePath
        val incognitoBefore = PrivacyStores.scannedEntry(incognitoPath)
        home.openIncognitoFromDownloads(PrivacyFixtures.NORMAL)
        reader.awaitReady()
        reader.awaitIdentity()
        reader.finishTask()

        home.reopen()

        PrivacyStores.holdScannedEntryUnchanged(incognitoPath, incognitoBefore)
        assertEquals(
            "reading a file in incognito must not change its exact scanned-cache row",
            incognitoBefore,
            PrivacyStores.scannedEntry(incognitoPath),
        )
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun placePickerRowAcrossNavigationBar(fileName: String) {
        home.picker.openDownloads()
        androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true))
            .scrollIntoView(androidx.test.uiautomator.UiSelector().text(fileName))
        val device = TestEnvironment.uiDevice
        val homeButton = device.findObject(androidx.test.uiautomator.By.res("com.android.systemui", "home"))
            ?: error("The navigation bar carries no Home button")
        val homeBounds = homeButton.visibleBounds
        repeat(6) {
            val title = device.findObject(androidx.test.uiautomator.By.text(fileName))
                ?: error("The picker carries no row named $fileName")
            var row = title
            while (!row.isClickable && row.parent != null) row = row.parent
            val centre = row.visibleCenter
            if (homeBounds.contains(centre.x, centre.y) && title.visibleBounds.top < homeBounds.top) return
            val delta = (homeBounds.centerY() - centre.y).coerceIn(-500, 500)
            val start = android.graphics.PointF(device.displayWidth / 2f, device.displayHeight / 2f)
            com.gitlab.mudlej.MjPdfReader.suite.core.RawInput.dragWithoutFling(
                start, android.graphics.PointF(start.x, start.y + delta),
            )
            device.waitForIdle()
        }
        error("The picker row named $fileName never straddled the navigation bar")
    }

    private companion object {
        const val READ_TO_PAGE_INDEX = 2
    }
}
