// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import com.gitlab.mudlej.MjPdfReader.core.debug.BackupDebugHooks
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import kotlinx.coroutines.CompletableDeferred
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.resilience.ResilienceLogcat
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackupResilienceTest : BackupTestCase() {
    @Test
    @TestId("BKP-58")
    fun rotationDuringAnImportDoesNotStopIt() {
        val tree = tree()
        val seeded = BackupHashes.ALL.take(3)
        seeded.forEach { hash -> BackupSeed.record(hash = hash) }
        val fixture = BackupFixtures.writeInto(tree, LARGE_FIXTURE_NAME, largeFixtureJson())

        launchBackupPage().use {
            val since = ResilienceLogcat.since()
            val barrier = CompletableDeferred<Unit>()
            BackupDebugHooks.importBarrier = barrier
            val outcome = try {
                ImportSignals.awaitImport(Await.VERY_LONG_TIMEOUT_MS) {
                    BackupPage.startImport()
                    BackupFixtures.openInDirectory(tree, fixture.name)
                    confirmWipeWarning()
                    rotateOnceTheImportIsUnderway()
                    barrier.complete(Unit)
                }
            } finally {
                BackupDebugHooks.importBarrier = null
                barrier.complete(Unit)
            }
            assertEquals(ImportSignals.OUTCOME_IMPORTED, outcome)
            Await.until("the rotated Settings window receives input", Await.LONG_TIMEOUT_MS) {
                TestEnvironment.onMainThread {
                    TestEnvironment.resumedActivityOfType(SettingsActivity::class.java)?.hasWindowFocus() == true
                }
            }
            ResilienceLogcat.assertAbsent(
                since,
                ResilienceLogcat.WINDOW_LEAK_MARKER,
                "Rotating during an import must release the old activity's progress dialog",
            )
        }

        val restored = BackupSeed.recordHashes()
        assertEquals(
            "The import did not restore every record in the file",
            LARGE_RECORD_COUNT,
            restored.size,
        )
        seeded.forEach { hash ->
            assertFalse("The pre import record $hash survived the wipe", restored.contains(hash))
        }

        val pending = BackupSeed.importResultPending()
        assertNotNull("A rotation stopped the import before it could report", pending)
        assertTrue(
            "The import did not report finishing, it read \"$pending\"",
            pending!!.contains(BackupStrings.importFinished),
        )
        assertEquals(1, BackupTree.awaitSafetySnapshotCount(1).size)
    }

    private fun rotateOnceTheImportIsUnderway() {
        Await.until("the running import is held with its progress dialog visible") {
            BackupDebugHooks.isImportBlocked() && ImportSignals.isRunning() &&
                BackupDialog.title() == BackupStrings.importRunning
        }
        val previous = Screens.awaitResumed(SettingsActivity::class.java) as SettingsActivity
        TestEnvironment.uiDevice.setOrientationLeft()
        Screens.awaitReplacement(previous, SettingsActivity::class.java)
        Screens.awaitResumed(SettingsActivity::class.java)
        assertTrue("the import remains in flight through activity replacement", BackupDebugHooks.isImportBlocked() && ImportSignals.isRunning())
    }

    private fun largeFixtureJson(): String {
        val records = (1..LARGE_RECORD_COUNT).joinToString(",") { index ->
            val hash = "h2%032d".format(index)
            "{\"hash\":\"$hash\",\"pageNumber\":${index % 50},\"length\":0," +
                "\"fileName\":\"padded-$index.pdf\",\"lastOpened\":\"2026-01-01T00:00:00\"," +
                "\"reading\":\"UNSET\",\"favorite\":false,\"cropMarginsVersion\":0,\"hidden\":false," +
                "\"uri\":\"file:///sdcard/Download/padded-$index.pdf\"}"
        }
        return "{\"schemaVersion\":1,\"appVersionCode\":1,\"exportedAt\":\"2026-01-01T00:00:00Z\"," +
            "\"settings\":[{\"key\":\"screenOn\",\"type\":\"boolean\",\"value\":\"true\"}]," +
            "\"pdfRecords\":[$records],\"userBookmarks\":[]}"
    }

    private companion object {
        const val LARGE_FIXTURE_NAME = "padded-400-records.json"
        const val LARGE_RECORD_COUNT = 400
    }
}
