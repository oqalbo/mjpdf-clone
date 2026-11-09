// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.UriPermission
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDb
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeReader
import com.gitlab.mudlej.MjPdfReader.suite.robot.DocumentPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.LONG)
class GrantScopeTest {
    private val suite = SuiteRules.standard()

    private val downloads = SecurityDownloadsRule(SecuritySaveFaults.DESTINATION_PREFIX)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(downloads)

    private val reader = ReaderRobot()
    private val picker = DocumentPickerRobot()

    @Test
    @TestId("SEC-64")
    fun aWriteGrantIsTakenOnlyForADestinationTheUserChoseToWriteTo() {
        SecurityReader.startFromOutside(pickerIntent())
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        picker.pickFromDownloads(SecurityFixtures.NORMAL)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender()

        val sourceUri = reader.readState().documentUri
            ?: throw AssertionError("The reader reports no document URI after the pick")

        SecuritySaveFaults.createHighlightAndSaveAsNewCopy(reader)

        val granted = Await.untilValue(
            "the chosen save destination leaves a write grant",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeReader.persistedPermissions() },
        ) { permissions -> permissions.any { it.isWritePermission } }

        val writable = granted.filter { it.isWritePermission }
        assertEquals(
            "exactly one grant may carry write access, the grants were ${granted.map { it.uri }}",
            1,
            writable.size,
        )
        assertFalse(
            "the write grant must be the destination and never the source document",
            writable.single().uri.toString() == sourceUri,
        )
        assertFalse(
            "no folder was granted, so the write grant must not be a tree URI",
            isTreeUri(writable.single()),
        )

        val source = granted.firstOrNull { it.uri.toString() == sourceUri }
        if (source != null) {
            assertFalse("the source document's grant must stay read only", source.isWritePermission)
        }

        val destination = IntakeDb.saveDestination(SourceKey.of(android.net.Uri.parse(sourceUri)))
            ?: throw AssertionError("No save destination row was written for the document")
        assertEquals(
            "the save destination row must hold the same URI the write grant covers",
            writable.single().uri.toString(),
            destination.destinationUri,
        )

        reader.pressBack()
    }

    @Test
    @TestId("SEC-65")
    fun theBackupFolderGrantIsTheOnlyTreeGrantAndIsNotTakenUntilAsked() {
        assertEquals(
            "a clean install must hold no persisted grant at all",
            emptyList<UriPermission>(),
            IntakeReader.persistedPermissions(),
        )

        val picked = Fixtures.pickerFixture(SecurityFixtures.NORMAL).name

        SecurityReader.startFromOutside(pickerIntent())
        picker.awaitShown(Await.LONG_TIMEOUT_MS)
        picker.pickFromDownloads(picked)
        reader.awaitShown(Await.LONG_TIMEOUT_MS)
        reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
        reader.awaitFullQualityRender()

        val afterPick = Await.untilValue(
            "the picked document leaves one persisted grant",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeReader.persistedPermissions() },
        ) { it.size == 1 }
        assertTrue(
            "opening a document must never take a folder grant, it took ${afterPick.map { it.uri }}",
            afterPick.none { isTreeUri(it) },
        )

        reader.pressBack()
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        SecurityBackupFolder.chooseThroughSettings()

        val afterFolder = Await.untilValue(
            "choosing a backup folder leaves exactly one tree grant",
            Await.LONG_TIMEOUT_MS,
            read = { IntakeReader.persistedPermissions() },
        ) { permissions -> permissions.count { isTreeUri(it) } == 1 }

        val tree = afterFolder.single { isTreeUri(it) }
        assertTrue(
            "SettingsFragment asks for both flags on the backup folder, so the tree grant must carry read",
            tree.isReadPermission,
        )
        assertTrue(
            "SettingsFragment asks for both flags on the backup folder, so the tree grant must carry write",
            tree.isWritePermission,
        )
        assertEquals(
            "the backup folder preference must hold the same URI the tree grant covers",
            tree.uri.toString(),
            SecurityBackupFolder.storedTreeUri(),
        )
    }

    private fun pickerIntent() = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setClass(TestEnvironment.targetContext, MainActivity::class.java)
    }

    private fun isTreeUri(permission: UriPermission): Boolean =
        permission.uri.path?.startsWith(TREE_PATH_PREFIX) == true

    private companion object {
        const val TREE_PATH_PREFIX = "/tree/"
    }
}
