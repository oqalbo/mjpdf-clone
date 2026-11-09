// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.translation.DictionaryDebugConfig
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDialogs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.settings.SettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class DictionaryIntegrityTest {
    private val suite = SuiteRules.standard()
    private val server = SecurityServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(server)

    @After
    fun clearTheDownloadOverride() {
        DictionaryDebugConfig.clear()
    }

    @Test
    @TestId("SEC-66")
    fun theDictionaryDownloadIsVerifiedAgainstItsPinnedHash() {
        val filesDirectory = TestEnvironment.targetContext.filesDir
        val before = FileTree.snapshot(filesDirectory).entries.keys

        DictionaryDebugConfig.urlOverride = server.serveBytes(
            DICTIONARY_PATH,
            gzip(WRONG_DATABASE_BODY.toByteArray()),
            GZIP_MIME_TYPE,
        )

        openSettings()
        tapRow(string(R.string.settings_translation))
        tapRow(string(R.string.dictionary_title))
        IntakeDialogs.tap(string(R.string.dictionary_download_action))

        val requests = server.awaitRequestCount(1, Await.LONG_TIMEOUT_MS)
        assertEquals("the dictionary installer must request the configured body", listOf(DICTIONARY_PATH), requests)
        IntakeDialogs.awaitShowing(string(R.string.dictionary_download_failed), Await.VERY_LONG_TIMEOUT_MS)
        IntakeDialogs.awaitMessageContaining(HASH_FAILURE_MESSAGE)

        val dictionary = File(filesDirectory, DICTIONARY_PATH_ON_DISK)
        assertFalse(
            "a body that fails the pinned SHA-256 must leave no database behind at ${dictionary.absolutePath}",
            dictionary.exists(),
        )

        val added = FileTree.snapshot(filesDirectory).entries.keys - before
        val leftovers = added.filter { it.endsWith(TMP_SUFFIX) || it.endsWith(GZ_TMP_SUFFIX) }
        assertEquals(
            "the installer must delete both temporary files on the failure path, it left $leftovers",
            emptyList<String>(),
            leftovers,
        )

        IntakeDialogs.tap(string(R.string.ok))
        leaveSettings()
    }

    private fun openSettings() {
        TestEnvironment.targetContext.startActivity(
            Intent(TestEnvironment.targetContext, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Screens.awaitResumed(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun tapRow(title: String) {
        SettingsRows.tap(title)
    }

    private fun leaveSettings() {
        repeat(SETTINGS_BACK_PRESS_BUDGET) {
            if (!Screens.isInTask(SettingsActivity::class.java)) return
            TestEnvironment.uiDevice.pressBack()
            TestEnvironment.waitForIdleSync()
        }

        val remaining = Screens.liveActivitiesOfType(SettingsActivity::class.java)
        TestEnvironment.onMainThread { remaining.forEach { it.finish() } }
        Screens.awaitGone(SettingsActivity::class.java, Await.LONG_TIMEOUT_MS)
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private companion object {
        const val DICTIONARY_PATH = "/dictionary.db.gz"
        const val DICTIONARY_PATH_ON_DISK = "dictionaries/en-wordnet.db"
        const val TMP_SUFFIX = ".tmp"
        const val GZ_TMP_SUFFIX = ".gz.tmp"
        const val WRONG_DATABASE_BODY = "this is not the dictionary the app pinned"
        const val HASH_FAILURE_MESSAGE = "Downloaded file failed the integrity check"
        const val GZIP_MIME_TYPE = "application/gzip"
        const val SETTINGS_BACK_PRESS_BUDGET = 4
    }
}
