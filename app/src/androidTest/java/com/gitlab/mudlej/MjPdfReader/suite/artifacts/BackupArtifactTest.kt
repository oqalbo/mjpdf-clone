// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupCleanupRule
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupDialog
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupFixtures
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupPage
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupSeed
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupStrings
import com.gitlab.mudlej.MjPdfReader.suite.backup.BackupTree
import com.gitlab.mudlej.MjPdfReader.suite.backup.ExportOptions
import com.gitlab.mudlej.MjPdfReader.suite.backup.ImportSignals
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.StubSharerProvider
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.robot.SafPickerRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class BackupArtifactTest {

    private val suite = SuiteRules.standard()
    private val backupCleanup = BackupCleanupRule()
    private val cleanup = ArtifactCleanupRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite)
        .around(backupCleanup)
        .around(cleanup)

    private val picker = SafPickerRobot()
    private val reader = ArtifactReader()

    @After
    fun clearSharedDocuments() {
        StubSharerProvider.clear()
    }

    @Test
    @TestId("ART-33")
    fun theExportedBackupIsCanonicalPrettyPrintedJsonWithTheExpectedTopLevelShape() {
        val tree = tree()
        BackupSeed.seedRecords(THREE_RECORDS)
        BackupSeed.bookmark(RECORD_PREFIX + 1, pageIndex = 3)
        BackupSeed.bookmark(RECORD_PREFIX + 2, pageIndex = 7)

        BackupPage.launch().use {
            grantFolder(tree)
            val backup = BackupFile(exportWith(tree, settings = true, history = true, passwords = false))

            assertEquals("the envelope must declare schema version 1", 1, backup.schemaVersion())
            assertEquals(
                "the envelope must record the installed package's version code",
                installedVersionCode(),
                backup.appVersionCode(),
            )
            assertTrue(
                "the export timestamp must parse as an offset date time, it reads ${backup.exportedAt()}",
                runCatching { OffsetDateTime.parse(backup.exportedAt()) }.isSuccess,
            )
            assertEquals(
                "the root must carry exactly the six canonical members",
                ROOT_MEMBERS,
                backup.memberNames(),
            )
            assertTrue("the JSON must be pretty printed", backup.isPrettyPrinted())
            assertFalse("the JSON must carry no byte order mark", backup.hasByteOrderMark())
        }
    }

    @Test
    @TestId("ART-34")
    fun everyPopulatedRecordAndBookmarkFieldIsWrittenWithItsCanonicalMemberName() {
        val tree = tree()
        val seeded = BackupSeed.record(
            hash = FULL_RECORD_HASH,
            fileName = "fully-populated.pdf",
            pageNumber = 11,
            password = null,
            reading = ReadingStatus.READING,
            favorite = true,
            lastOpened = LocalDateTime.of(2026, 2, 3, 4, 5, 6),
            cropMargins = "0.1,0.2,0.3,0.4",
            cropMarginsVersion = 2,
            autoScrollSpeed = 42,
            readingDirectionOverride = "RTL",
            detectedReadingDirection = "LTR",
            documentTitle = "A Fully Populated Record",
            textModeJoinParagraphs = true,
            textModeDetectHeadings = false,
            textModeCodeBlocks = true,
            hidden = true,
            uri = "file:///sdcard/Download/fully-populated.pdf",
            sourceUri = "content://mjpdf.test.sharer/fully-populated.pdf",
            length = 12345,
        )
        val bookmark = BackupSeed.bookmark(
            fileHash = FULL_RECORD_HASH,
            pageIndex = 5,
            label = "A labelled bookmark",
            sortOrder = 9,
        )

        BackupPage.launch().use {
            grantFolder(tree)
            val backup = BackupFile(exportWith(tree, settings = true, history = true, passwords = false))
            val record = backup.records().singleOrNull()
                ?: throw AssertionError("The backup holds ${backup.records().size} records rather than one")
            val bookmarkObject = backup.bookmarks().singleOrNull()
                ?: throw AssertionError("The backup holds ${backup.bookmarks().size} bookmarks rather than one")

            assertEquals("the identity hash must be written as hash", seeded.hash, record.getString("hash"))
            assertEquals("the reading position must be written as pageNumber", seeded.pageNumber, record.getInt("pageNumber"))
            assertEquals("the file length must be written as length", seeded.length, record.getInt("length"))
            assertEquals("the file name must be written as fileName", seeded.fileName, record.getString("fileName"))
            assertEquals("the last opened time must be written as lastOpened", seeded.lastOpened.toString(), record.getString("lastOpened"))
            assertEquals("the reading status must be written as reading", seeded.reading.name, record.getString("reading"))
            assertEquals("the favourite flag must be written as favorite", seeded.favorite, record.getBoolean("favorite"))
            assertEquals("the crop margins must be written as cropMargins", seeded.cropMargins, record.getString("cropMargins"))
            assertEquals("the crop margins version must be written as cropMarginsVersion", seeded.cropMarginsVersion, record.getInt("cropMarginsVersion"))
            assertEquals("the auto scroll speed must be written as autoScrollSpeed", seeded.autoScrollSpeed, record.getInt("autoScrollSpeed"))
            assertEquals(
                "the reading direction override must be written under its own name",
                seeded.readingDirectionOverride,
                record.getString("readingDirectionOverride"),
            )
            assertEquals(
                "the detected reading direction must be written under its own name",
                seeded.detectedReadingDirection,
                record.getString("detectedReadingDirection"),
            )
            assertEquals("the document title must be written as documentTitle", seeded.documentTitle, record.getString("documentTitle"))
            assertEquals("the document location must be written as uri", seeded.uri.toString(), record.getString("uri"))
            assertEquals(
                "the join paragraphs switch must be written under its own name",
                seeded.textModeJoinParagraphs,
                record.getBoolean("textModeJoinParagraphs"),
            )
            assertEquals(
                "the detect headings switch must be written under its own name",
                seeded.textModeDetectHeadings,
                record.getBoolean("textModeDetectHeadings"),
            )
            assertEquals("the code block switch must be written as textModeCodeBlocks", seeded.textModeCodeBlocks, record.getBoolean("textModeCodeBlocks"))
            assertEquals("the hidden flag must be written as hidden", seeded.hidden, record.getBoolean("hidden"))
            assertEquals("the source link must be written as sourceUri", seeded.sourceUri, record.getString("sourceUri"))
            assertFalse(
                "a backup taken with passwords excluded must carry no password member",
                record.has("password"),
            )

            assertEquals("the bookmark owner must be written as fileHash", bookmark.fileHash, bookmarkObject.getString("fileHash"))
            assertEquals("the bookmark page must be written as pageIndex", bookmark.pageIndex, bookmarkObject.getInt("pageIndex"))
            assertEquals("the bookmark label must be written as label", bookmark.label, bookmarkObject.getString("label"))
            assertEquals("the bookmark creation time must be written as createdAt", bookmark.createdAt.toString(), bookmarkObject.getString("createdAt"))
            assertEquals("the bookmark order must be written as sortOrder", bookmark.sortOrder, bookmarkObject.getInt("sortOrder"))

            val keys = backup.settingKeys()
            assertEquals("the settings array must be sorted ascending by key", keys.sorted(), keys)
            assertTrue(
                "no key the exporter excludes may appear in the settings array, it carries $keys",
                keys.none { it in EXCLUDED_SETTING_KEYS },
            )
        }
    }

    @Test
    @TestId("ART-35")
    fun theSourceLinkOfAnImportedDocumentIsCarriedInTheBackup() {
        val tree = tree()
        cleanup.ownMediaUnder(MediaStoreProbe.DOCUMENTS_PATH)
        val source = ArtifactFixtures.require(ArtifactFixtures.SMALL)
        val shared = StubSharerProvider.registerPath(SHARED_NAME, source.absolutePath)

        ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(shared)).use {
            reader.awaitReady()
            Snackbars.awaitText(
                TestEnvironment.targetContext.getString(R.string.shared_copy_saved_public),
                Await.LONG_TIMEOUT_MS,
            )
        }
        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        val record = Await.untilValue(
            "the record is repointed at the durable copy and remembers where it came from",
            Await.LONG_TIMEOUT_MS,
            read = { Records.all().singleOrNull() },
        ) { it != null && it.sourceUri == shared.toString() }!!

        BackupPage.launch().use {
            grantFolder(tree)
            val backup = BackupFile(exportWith(tree, settings = false, history = true, passwords = false))
            val exported = backup.records().singleOrNull()
                ?: throw AssertionError("The backup holds ${backup.records().size} records rather than one")

            assertEquals(
                "the backup must carry the original content URI as the source link",
                shared.toString(),
                exported.getString("sourceUri"),
            )
            assertEquals(
                "the backup must carry the durable copy's URI as the record's own URI",
                record.uri.toString(),
                exported.getString("uri"),
            )
            assertNotEquals(
                "the record's URI must no longer be the temporary content URI",
                shared.toString(),
                exported.getString("uri"),
            )
            assertEquals("the backup must carry the record's identity hash", record.hash, exported.getString("hash"))
        }
    }

    @Test
    @TestId("ART-36")
    fun sectionOnlyBackupsOmitTheOtherSectionsMembersEntirely() {
        val tree = tree()
        BackupSeed.seedRecords(THREE_RECORDS)
        BackupSeed.bookmark(RECORD_PREFIX + 1, pageIndex = 1)
        BackupSeed.bookmark(RECORD_PREFIX + 2, pageIndex = 2)

        BackupPage.launch().use {
            grantFolder(tree)
            val settingsOnly = BackupFile(exportWith(tree, settings = true, history = false, passwords = false))
            val historyOnly = BackupFile(
                exportWith(tree, settings = false, history = true, passwords = false, expectedFileCount = 2)
            )

            assertFalse(
                "a settings only backup must carry no pdfRecords member at all, since an empty array and an " +
                    "absent member are different things to the importer",
                settingsOnly.has("pdfRecords"),
            )
            assertFalse(
                "a settings only backup must carry no userBookmarks member at all",
                settingsOnly.has("userBookmarks"),
            )
            assertTrue(
                "a settings only backup must carry a non empty settings array",
                settingsOnly.settings().isNotEmpty(),
            )
            assertFalse("a history only backup must carry no settings member", historyOnly.has("settings"))
            assertEquals(
                "a history only backup must carry every record",
                THREE_RECORDS,
                historyOnly.records().size,
            )
            assertEquals(
                "a history only backup must carry every bookmark",
                TWO_BOOKMARKS,
                historyOnly.bookmarks().size,
            )
            assertFalse("no member of either file may be written as null", settingsOnly.hasNullValuedMember())
            assertFalse("no member of either file may be written as null", historyOnly.hasNullValuedMember())
            assertEquals("both files must declare schema version 1", 1, settingsOnly.schemaVersion())
            assertEquals("both files must declare schema version 1", 1, historyOnly.schemaVersion())
        }
    }

    @Test
    @TestId("ART-37")
    fun exportLeavesTheFolderHoldingExactlyOneFile() {
        val tree = tree()
        BackupSeed.seedRecords(1)

        BackupPage.launch().use {
            grantFolder(tree)
            val produced = exportWith(tree, settings = true, history = true, passwords = false)
            val entries = BackupFolderProbe.entries()

            assertEquals(
                "the folder must hold the single produced backup, it holds ${entries.map { it.name }}",
                1,
                entries.size,
            )
            val entry = entries.single()
            assertFalse(
                "the verified temporary file must be renamed rather than left behind, the folder holds " +
                    entry.name,
                entry.name.endsWith(TEMPORARY_SUFFIX),
            )
            assertEquals("the surviving file must be the produced backup", produced.name, entry.name)
            if (android.webkit.MimeTypeMap.getSingleton().hasExtension("json")) {
                assertNotEquals(
                    "the surviving file must not keep the temporary octet stream type the writer created it with",
                    TEMPORARY_MIME_TYPE,
                    entry.mimeType,
                )
            }
            assertEquals(
                "the surviving file must parse as a backup carrying the seeded record",
                1,
                BackupFile(produced).records().size,
            )
        }
    }

    @Test
    @TestId("ART-38")
    @TestDuration(DurationCategory.LONG)
    fun aBackupRoundTripRestoresTheDatabaseExactly() {
        val tree = tree()
        seedVariedLibrary()
        val recordsBefore = dumpRecords()
        val bookmarksBefore = dumpBookmarks()
        assertEquals("the seeded library must hold five records", VARIED_RECORDS, recordsBefore.size)
        assertEquals("the seeded library must hold six bookmarks", VARIED_BOOKMARKS, bookmarksBefore.size)

        BackupPage.launch().use { scenario ->
            grantFolder(tree)
            val produced = exportWith(tree, settings = false, history = true, passwords = true)

            importFromFixtures(BackupFixtures.VALID_FULL)
            assertEquals(
                "the unrelated import must have replaced the library",
                BackupFixtures.VALID_FULL_RECORD_COUNT,
                Records.rowCount(),
            )
            assertEquals(
                "the unrelated import must have replaced the bookmarks",
                BackupFixtures.VALID_FULL_BOOKMARK_COUNT,
                dumpBookmarks().size,
            )
            ImportSignals.clearProgressDialog(scenario)

            importFromTree(tree, produced.name)

            assertEquals(
                "every record must come back exactly as it was, column for column",
                recordsBefore,
                dumpRecords(),
            )
            assertEquals(
                "every bookmark must come back exactly as it was, column for column",
                bookmarksBefore,
                dumpBookmarks(),
            )
            assertTrue(
                "no record from the unrelated backup may survive the restore",
                dumpRecords().none { it.startsWith(BackupFixtures.VALID_FULL_HASHES.first()) },
            )
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("ART-39")
    @TestDuration(DurationCategory.LONG)
    fun settingsRoundTripPreservesValueAndTypeForEverySupportedKind() {
        val tree = tree()
        SettingsSeeder.seedAll(ORIGINAL_SETTINGS)
        val preferences = TestEnvironment.defaultPreferences

        BackupPage.launch().use { scenario ->
            grantFolder(tree)
            val produced = exportWith(tree, settings = true, history = false, passwords = false)

            SettingsSeeder.seedAll(CHANGED_SETTINGS)
            assertNotEquals(
                "the changed values must really differ before the import can prove anything",
                ORIGINAL_SETTINGS[Preferences.maxZoomKey],
                preferences.getFloat(Preferences.maxZoomKey, 0f),
            )

            importFromTree(tree, produced.name, expectWipeWarning = false)

            assertEquals(
                "the boolean preference must come back with its recorded value",
                ORIGINAL_SETTINGS[Preferences.highQualityKey],
                preferences.getBoolean(Preferences.highQualityKey, false),
            )
            assertEquals(
                "the int preference must come back with its recorded value",
                ORIGINAL_SETTINGS[Preferences.hideDelayKey],
                preferences.getInt(Preferences.hideDelayKey, 0),
            )
            assertEquals(
                "the float preference must come back bit for bit, so no precision was lost through the " +
                    "string round trip the backup format uses",
                (ORIGINAL_SETTINGS[Preferences.maxZoomKey] as Float).toRawBits(),
                preferences.getFloat(Preferences.maxZoomKey, 0f).toRawBits(),
            )
            assertEquals(
                "the string preference must come back with its recorded value",
                ORIGINAL_SETTINGS[Preferences.homeViewModeKey],
                preferences.getString(Preferences.homeViewModeKey, null),
            )
            assertEquals(
                "the string set preference must come back with the same members, order independent",
                ORIGINAL_SETTINGS[Preferences.shortcutBarActionsKey],
                preferences.getStringSet(Preferences.shortcutBarActionsKey, emptySet()),
            )
            val declaredLongKeys = Preferences.backupSettingKinds
                .filterValues { it == Preferences.kindLong }
                .keys
            assertTrue(
                "Preferences.backupSettingKinds must declare at least one long typed key for this check to mean " +
                    "anything",
                declaredLongKeys.isNotEmpty(),
            )
            assertTrue(
                "every long typed key the app declares is excluded from backups, so no long value can round " +
                    "trip and this kind cannot be covered. The declared long keys are $declaredLongKeys",
                declaredLongKeys.all { it in EXCLUDED_SETTING_KEYS },
            )
            val exportedKeys = BackupFile(produced).settingKeys()
            assertTrue(
                "the produced backup must carry no long typed key, it carries $exportedKeys",
                exportedKeys.none { it in declaredLongKeys },
            )
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("ART-40")
    fun unknownAndWronglyTypedSettingsNeverReachSharedPreferences() {
        val directory = suite.testDirectory.directory()
        val backupFile = File(directory, TAMPERED_NAME)
        backupFile.parentFile?.mkdirs()
        backupFile.writeText(tamperedBackupJson())
        cleanup.own(backupFile)
        val preferences = TestEnvironment.defaultPreferences

        BackupPage.launch().use { scenario ->
            importFromDirectory(directory, TAMPERED_NAME)
            val stored = preferences.all

            assertFalse(
                "a key the app does not declare must never reach the preferences, they hold ${stored.keys}",
                stored.containsKey(UNKNOWN_KEY),
            )
            assertFalse(
                "a known boolean key declared as an int must be skipped rather than written, so it is absent " +
                    "and its getter falls back to the app default",
                stored.containsKey(Preferences.pageSnapKey),
            )
            val storedEnum = stored[Preferences.homeSortKey]
            assertTrue(
                "an enum key whose value is outside its declared domain must never be stored, it reads " +
                    "$storedEnum",
                storedEnum == null ||
                    storedEnum in Preferences.backupSettingEnumDomains.getValue(Preferences.homeSortKey),
            )
            assertEquals(
                "the valid boolean entry must be applied with its backed up value",
                true,
                stored[Preferences.screenOnKey],
            )
            assertEquals(
                "the valid int entry must be applied with its backed up value",
                VALID_SCROLL_SPEED,
                stored[Preferences.scrollSpeedKey],
            )
            assertEquals(
                "exactly the two usable entries may reach the preferences, everything else the file carried " +
                    "must be skipped. The applied keys read " + appliedKeys(stored.keys),
                setOf(Preferences.screenOnKey, Preferences.scrollSpeedKey),
                appliedKeys(stored.keys),
            )
            ImportSignals.clearProgressDialog(scenario)
        }
    }

    @Test
    @TestId("ART-41")
    fun anExcludedPasswordNeverAppearsAnywhereInTheBackupBytes() {
        val tree = tree()
        BackupSeed.record(hash = PASSWORD_RECORD_HASH, password = SECRET_PASSWORD)

        BackupPage.launch().use {
            grantFolder(tree)
            val withoutPasswords = exportWith(tree, settings = false, history = true, passwords = false)
            val excluded = BackupFile(withoutPasswords)

            assertEquals(
                "the password bytes must not occur anywhere in a backup taken with passwords excluded",
                0,
                excluded.textOccurrences(SECRET_PASSWORD),
            )
            assertFalse(
                "the record object must carry no password member",
                excluded.records().single().has("password"),
            )

            val withPasswords = exportWith(
                tree,
                settings = false,
                history = true,
                passwords = true,
                expectedFileCount = 2,
            )
            val included = BackupFile(withPasswords)
            assertEquals(
                "a backup taken with passwords included must carry the password exactly once",
                1,
                included.textOccurrences(SECRET_PASSWORD),
            )
        }
    }

    private fun tree(): File = suite.testDirectory.directory().also { BackupTree.requireEmpty(it) }

    private fun grantFolder(directory: File) {
        BackupPage.tapRow(BackupStrings.backupFolderTitle)
        BackupTree.pick(directory, picker)
        BackupPage.awaitShown()
        BackupPage.awaitSummaryContaining(BackupStrings.backupFolderTitle, directory.name)
    }

    private fun exportWith(
        directory: File,
        settings: Boolean,
        history: Boolean,
        passwords: Boolean,
        expectedFileCount: Int = 1,
    ): File {
        BackupPage.startExport()
        setOption(BackupStrings.optionHistory, history)
        if (history) {
            setOption(BackupStrings.optionPasswords, passwords)
        }
        setOption(BackupStrings.optionSettings, settings)
        ExportOptions.export()
        val produced = BackupTree.awaitManualBackupCount(directory, expectedFileCount)
        BackupTree.awaitNoTemporaryFiles(directory)
        dismissResultDialog()
        BackupPage.awaitShown()
        return produced.maxByOrNull { it.name }!!
    }

    private fun dismissResultDialog() {
        BackupDialog.awaitTitle(BackupStrings.exportTitle, Await.LONG_TIMEOUT_MS)
        Await.untilNotNull("the export result dialog carries a message", Await.LONG_TIMEOUT_MS) {
            BackupDialog.message()
        }
        BackupDialog.dismissWithOk()
    }

    private fun setOption(label: String, expected: Boolean) {
        if (ExportOptions.isChecked(label) == expected) {
            return
        }
        ExportOptions.toggle(label, expected)
    }

    private fun importFromFixtures(name: String) {
        val outcome = ImportSignals.awaitImport {
            BackupPage.startImport()
            BackupFixtures.open(name, picker)
            confirmWipeWarning()
        }
        assertEquals("the selected backup must complete successfully", ImportSignals.OUTCOME_IMPORTED, outcome)
    }

    private fun importFromTree(
        directory: File,
        name: String,
        expectWipeWarning: Boolean = true,
    ) {
        val outcome = ImportSignals.awaitImport {
            BackupPage.startImport()
            BackupFixtures.openInTree(directory, name, picker)
            if (expectWipeWarning) {
                confirmWipeWarning()
            }
        }
        assertEquals("the selected backup must complete successfully", ImportSignals.OUTCOME_IMPORTED, outcome)
    }

    private fun importFromDirectory(
        directory: File,
        name: String,
    ) {
        val outcome = ImportSignals.awaitImport {
            BackupPage.startImport()
            BackupFixtures.openInDirectory(directory, name, picker)
        }
        assertEquals("the selected backup must complete successfully", ImportSignals.OUTCOME_IMPORTED, outcome)
    }

    private fun confirmWipeWarning() {
        BackupDialog.awaitMessageContaining(BackupStrings.wipeWarning.take(WIPE_WARNING_PREFIX))
        BackupDialog.awaitButtonEnabled(BackupDialog.POSITIVE, WIPE_COUNTDOWN_TIMEOUT_MS)
        BackupDialog.tap(BackupDialog.POSITIVE)
    }

    private fun seedVariedLibrary() {
        BackupSeed.record(hash = "h2varied1", hidden = true, pageNumber = 4)
        BackupSeed.record(hash = "h2varied2", favorite = true, pageNumber = 9)
        BackupSeed.record(hash = "h2varied3", cropMargins = "0.05,0.06,0.07,0.08", cropMarginsVersion = 3)
        BackupSeed.record(hash = "h2varied4", password = "roundTripPassword")
        BackupSeed.record(hash = "h2varied5", lastOpened = LocalDateTime.of(2019, 12, 31, 23, 59, 58))
        BackupSeed.bookmark("h2varied1", pageIndex = 1, label = "first", sortOrder = 7)
        BackupSeed.bookmark("h2varied1", pageIndex = 2, label = null, sortOrder = 3)
        BackupSeed.bookmark("h2varied2", pageIndex = 5, label = "second", sortOrder = 11)
        BackupSeed.bookmark("h2varied2", pageIndex = 6, label = "third", sortOrder = 1)
        BackupSeed.bookmark("h2varied3", pageIndex = 8, label = "fourth", sortOrder = 20)
        BackupSeed.bookmark("h2varied3", pageIndex = 9, label = "fifth", sortOrder = 2)
    }

    private fun dumpRecords(): List<String> = Records.all().map { record ->
        listOf(
            record.hash,
            record.pageNumber,
            record.uri,
            record.length,
            record.fileName,
            record.password,
            record.lastOpened,
            record.reading,
            record.favorite,
            record.cropMargins,
            record.cropMarginsVersion,
            record.autoScrollSpeed,
            record.readingDirectionOverride,
            record.detectedReadingDirection,
            record.documentTitle,
            record.textModeJoinParagraphs,
            record.textModeDetectHeadings,
            record.textModeCodeBlocks,
            record.hidden,
            record.sourceUri,
        ).joinToString("|")
    }.sorted()

    private fun dumpBookmarks(): List<String> =
        Records.database.userBookmarkDao().findAll().map { bookmark ->
            listOf(
                bookmark.fileHash,
                bookmark.pageIndex,
                bookmark.label,
                bookmark.createdAt,
                bookmark.sortOrder,
            ).joinToString("|")
        }.sorted()

    private fun appliedKeys(keys: Set<String>): Set<String> = keys - EXCLUDED_SETTING_KEYS

    private fun tamperedBackupJson(): String = """
        {
          "schemaVersion": 1,
          "appVersionCode": 1,
          "exportedAt": "2026-01-15T09:30:00Z",
          "settings": [
            { "key": "$UNKNOWN_KEY", "type": "string", "value": "whatever" },
            { "key": "${Preferences.pageSnapKey}", "type": "int", "value": "1" },
            { "key": "${Preferences.homeSortKey}", "type": "string", "value": "$NOT_A_MEMBER" },
            { "key": "${Preferences.screenOnKey}", "type": "boolean", "value": "true" },
            { "key": "${Preferences.scrollSpeedKey}", "type": "int", "value": "$VALID_SCROLL_SPEED" }
          ]
        }
    """.trimIndent()

    private fun installedVersionCode(): Long {
        val context = TestEnvironment.targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.longVersionCode
    }

    private companion object {
        const val RECORD_PREFIX = "h2seed"
        const val THREE_RECORDS = 3
        const val TWO_BOOKMARKS = 2
        const val VARIED_RECORDS = 5
        const val VARIED_BOOKMARKS = 6

        const val FULL_RECORD_HASH = "h2fullypopulatedrecord"
        const val PASSWORD_RECORD_HASH = "h2passwordbearingrecord"
        const val SECRET_PASSWORD = "Pa55wordFixtureXYZ"

        const val SHARED_NAME = "art35-shared.pdf"

        const val TEMPORARY_SUFFIX = ".tmp"
        const val TEMPORARY_MIME_TYPE = "application/octet-stream"

        const val TAMPERED_NAME = "art40-tampered-backup.json"
        const val UNKNOWN_KEY = "notARealSetting"
        const val NOT_A_MEMBER = "NOT_A_MEMBER"
        const val VALID_SCROLL_SPEED = 5

        const val WIPE_WARNING_PREFIX = 40
        const val WIPE_COUNTDOWN_TIMEOUT_MS = 15_000L

        val ROOT_MEMBERS = setOf(
            "schemaVersion",
            "appVersionCode",
            "exportedAt",
            "settings",
            "pdfRecords",
            "userBookmarks",
        )

        val EXCLUDED_SETTING_KEYS = setOf(
            Preferences.firstInstallKey,
            Preferences.showFeaturesDialogKey,
            Preferences.showExitFullscreenTipKey,
            Preferences.lastSeenVersionCodeKey,
            Preferences.backupFolderTreeUriKey,
            Preferences.autoBackupLastRunKey,
            Preferences.autoBackupLastErrorKey,
            Preferences.autoBackupErrorAcknowledgedRunKey,
            Preferences.autoBackupEnabledAtKey,
            Preferences.importResultPendingKey,
        )

        val ORIGINAL_SETTINGS: Map<String, Any> = mapOf(
            Preferences.highQualityKey to false,
            Preferences.hideDelayKey to 4321,
            Preferences.maxZoomKey to 7.125f,
            Preferences.homeViewModeKey to "LIST",
            Preferences.shortcutBarActionsKey to setOf("screenshot", "print", "share"),
        )

        val CHANGED_SETTINGS: Map<String, Any> = mapOf(
            Preferences.highQualityKey to true,
            Preferences.hideDelayKey to 1111,
            Preferences.maxZoomKey to 3.5f,
            Preferences.homeViewModeKey to "GRID",
            Preferences.shortcutBarActionsKey to setOf("reload"),
        )
    }
}
