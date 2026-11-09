// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.core.debug

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.gitlab.mudlej.MjPdfReader.BuildConfig
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.BackupCodec
import com.gitlab.mudlej.MjPdfReader.data.BackupData
import com.gitlab.mudlej.MjPdfReader.data.BackupExportOptions
import com.gitlab.mudlej.MjPdfReader.data.BackupImportPipeline
import com.gitlab.mudlej.MjPdfReader.data.BackupManager
import com.gitlab.mudlej.MjPdfReader.data.BackupSetting
import com.gitlab.mudlej.MjPdfReader.data.PdfRepository
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupCleanupRule
import com.gitlab.mudlej.MjPdfReader.suite.settings.SettingsCleanupRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BackupDebugHooksTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val prefsName = "backup-hooks-${UUID.randomUUID()}"
    private val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private var importPrefs: SharedPreferences = prefs
    private val restartAttempts = AtomicInteger()
    private val context = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int) = importPrefs
        override fun getPackageName() = "${app.packageName}.backup_hooks_test"
        override fun getPackageManager(): PackageManager {
            if (Looper.myLooper() == Looper.getMainLooper()) restartAttempts.incrementAndGet()
            return app.packageManager
        }
    }
    private val settingsOnly = BackupData(
        schemaVersion = 1,
        settings = listOf(BackupSetting(Preferences.highQualityKey, Preferences.kindBoolean, "true")),
    )
    private lateinit var database: AppDatabase
    private lateinit var repository: PdfRepository
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        assertTrue("Debug hooks require a debug build", BuildConfig.DEBUG)
        BackupDebugHooks.consumePrepareImportFailure()
        BackupDebugHooks.suppressRestart = false
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = PdfRepository(database)
        manager = BackupManager(context, repository)
        assertTrue(prefs.edit().putBoolean(Preferences.highQualityKey, false).commit())
    }

    @After
    fun tearDown() {
        try {
            if (::database.isInitialized) database.close()
            prefs.edit().clear().commit()
            app.deleteSharedPreferences(prefsName)
        } finally {
            BackupDebugHooks.consumePrepareImportFailure()
            BackupDebugHooks.suppressRestart = false
        }
    }

    @Test
    fun settingsImportPreservesPreferenceOnlySuppressionAndHistoryCache() = runBlocking {
        assertTrue(prefs.edit().putBoolean(BackupDebugHooks.SUPPRESS_RESTART_KEY, true).commit())
        assertTrue(BackupDebugHooks.shouldSuppressRestart(context))
        val record = PdfRecord("backup-hooks", 2, Uri.EMPTY, 10, "kept.pdf", null,
            LocalDateTime.of(2026, 1, 1, 0, 0), ReadingStatus.UNSET, false)
        val bookmark = UserBookmark(record.hash, 2, "kept", record.lastOpened, 0)
        repository.upsertRecords(listOf(record))
        repository.upsertUserBookmarks(listOf(bookmark))
        val covers = File(app.cacheDir, "covers").apply { mkdirs() }
        val cover = File.createTempFile("backup-hooks-", ".jpg", covers)
        try {
            cover.writeText("kept cover")
            assertEquals(listOf(record), repository.findAllRecords())
            assertEquals(listOf(bookmark), repository.findAllUserBookmarks())
            val summary = manager.applyImport(manager.prepareImport(settingsOnly))
            assertEquals(1, summary.settingsCount)
            assertEquals(0, summary.recordsCount)
            assertEquals(0, summary.bookmarksCount)
            assertTrue(prefs.getBoolean(Preferences.highQualityKey, false))
            assertEquals(listOf(record), repository.findAllRecords())
            assertEquals(listOf(bookmark), repository.findAllUserBookmarks())
            assertEquals("kept cover", cover.readText())
            assertFalse(BackupDebugHooks.suppressRestart)
            assertTrue(prefs.getBoolean(BackupDebugHooks.SUPPRESS_RESTART_KEY, false))
            assertTrue(BackupDebugHooks.shouldSuppressRestart(context))
        } finally {
            cover.delete()
        }
    }

    @Test
    fun exportedSettingsJsonExcludesSuppression() = runBlocking {
        assertTrue(prefs.edit().putBoolean(BackupDebugHooks.SUPPRESS_RESTART_KEY, true).commit())
        val folder = File(app.cacheDir, prefsName).apply { mkdirs() }
        try {
            manager.export(DocumentFile.fromFile(folder), "settings.json",
                BackupExportOptions(includeSettings = true, includeHistory = false, includePasswords = false))
            val json = File(folder, "settings.json").readText()
            assertEquals(listOf(BackupSetting(Preferences.highQualityKey, Preferences.kindBoolean, "false")),
                BackupCodec.decode(json).settings)
            assertFalse(json.contains(BackupDebugHooks.SUPPRESS_RESTART_KEY))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun contradictoryIncomingSuppressionIsIgnoredAndLocalValuePreserved() = runBlocking {
        for (local in listOf(true, false)) {
            assertTrue(prefs.edit().putBoolean(BackupDebugHooks.SUPPRESS_RESTART_KEY, local).commit())
            val incoming = settingsOnly.copy(settings = settingsOnly.settings!! +
                BackupSetting(BackupDebugHooks.SUPPRESS_RESTART_KEY, Preferences.kindBoolean, (!local).toString()))
            val summary = manager.applyImport(manager.prepareImport(incoming))
            assertEquals(local, prefs.getBoolean(BackupDebugHooks.SUPPRESS_RESTART_KEY, !local))
            assertEquals(local, BackupDebugHooks.shouldSuppressRestart(context))
            assertTrue(prefs.getBoolean(Preferences.highQualityKey, false))
            assertEquals(1, summary.settingsCount)
            assertEquals(0, summary.skippedSettingsCount)
        }
    }

    @Test
    fun pipelineCapturesMemorySuppressionBeforeImport() = pipelineImport(true, false)

    @Test
    fun pipelinePreservesPreferenceOnlySuppression() = pipelineImport(false, true)

    @Test
    fun unsuppressedPipelineAttemptsRestart() = pipelineImport(false, false)

    @Test
    fun backupRuleDisarmsFaultAtStart() = verifyCleanup(BackupCleanupRule(), atStart = true)

    @Test
    fun settingsRuleDisarmsFaultAtStart() = verifyCleanup(SettingsCleanupRule(), atStart = true)

    @Test
    fun backupRuleDisarmsFaultAfterFailure() = verifyCleanup(BackupCleanupRule(), atStart = false)

    @Test
    fun settingsRuleDisarmsFaultAfterFailure() = verifyCleanup(SettingsCleanupRule(), atStart = false)

    private fun verifyCleanup(rule: TestRule, atStart: Boolean) {
        val failure = IllegalStateException("test statement failed")
        try {
            if (atStart) BackupDebugHooks.failNextPrepareImport()
            val statement = rule.apply(object : Statement() {
                override fun evaluate() {
                    if (atStart) {
                        assertFalse("${rule.javaClass.simpleName} starting left a pending fault",
                            BackupDebugHooks.consumePrepareImportFailure())
                    } else {
                        BackupDebugHooks.failNextPrepareImport()
                        throw failure
                    }
                }
            }, Description.createTestDescription(javaClass, rule.javaClass.simpleName))
            if (atStart) {
                statement.evaluate()
            } else {
                assertSame(failure, assertThrows(IllegalStateException::class.java) { statement.evaluate() })
                assertFalse("${rule.javaClass.simpleName} finished left a pending fault",
                    BackupDebugHooks.consumePrepareImportFailure())
            }
        } finally {
            BackupDebugHooks.consumePrepareImportFailure()
            BackupDebugHooks.suppressRestart = false
        }
    }

    private fun pipelineImport(memorySuppression: Boolean, preferenceSuppression: Boolean) {
        WorkManager.getInstance(app)
        AppDatabase.getInstance(app).openHelper.writableDatabase
        assertNull(app.packageManager.getLaunchIntentForPackage(context.packageName))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        importPrefs = object : SharedPreferences by prefs {
            override fun getAll(): MutableMap<String, *> {
                check(Looper.myLooper() != Looper.getMainLooper())
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Import preferences gate timed out" }
                return prefs.all
            }
        }
        if (preferenceSuppression) {
            assertTrue(prefs.edit().putBoolean(BackupDebugHooks.SUPPRESS_RESTART_KEY, true).commit())
        }
        BackupDebugHooks.suppressRestart = memorySuppression
        val before = BackupDebugHooks.importCompletions()
        var started = false
        try {
            started = BackupImportPipeline.start(context, settingsOnly, createSafety = false, onRejected = { false })
            assertTrue("Pipeline refused to start", started)
            assertTrue("Import did not reach IO preferences gate", entered.await(10, TimeUnit.SECONDS))
            BackupDebugHooks.suppressRestart = false
            release.countDown()
            waitPositive("Import completion") { BackupDebugHooks.importCompletions() == before + 1 }
            assertFalse(BackupDebugHooks.importRunning())
            assertEquals(BackupDebugHooks.OUTCOME_IMPORTED, BackupDebugHooks.lastImportOutcome())
            assertTrue(prefs.getBoolean(Preferences.highQualityKey, false))
            assertEquals(app.getString(R.string.backup_import_finished) + "\n" +
                app.getString(R.string.backup_import_done_settings, 1), Preferences(prefs).getImportResultPending())
            if (memorySuppression || preferenceSuppression) {
                val until = SystemClock.uptimeMillis() + 1_000
                do {
                    assertEquals("Suppressed import attempted restart", 0, restartAttempts.get())
                    SystemClock.sleep(20)
                } while (SystemClock.uptimeMillis() < until)
                assertEquals(0, restartAttempts.get())
            } else {
                waitPositive("Unsuppressed restart observer") { restartAttempts.get() > 0 }
                assertEquals(1, restartAttempts.get())
            }
            if (preferenceSuppression) assertTrue(BackupDebugHooks.shouldSuppressRestart(context))
        } finally {
            release.countDown()
            if (started) waitPositive("Pipeline cleanup") { BackupDebugHooks.importCompletions() > before }
        }
    }

    private fun waitPositive(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < until) SystemClock.sleep(20)
        assertTrue(message, condition())
    }
}
