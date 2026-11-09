// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.suite.actions.ShareIntents
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.intake.DurableCopyRow
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeDb
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeReader
import com.gitlab.mudlej.MjPdfReader.suite.intake.StubSharer
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.IntentStubs
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class HostileNameTest {
    @get:Rule
    val rules = SuiteRules.standard()

    @get:Rule
    val intents = IntentsRule()

    private val reader = ReaderRobot()

    @After
    fun clearStagedShares() {
        HostileNames.clear()
        IntakeReader.deleteDurableCopies()
    }

    @Test
    @TestId("SEC-40")
    fun aHostileDisplayNameCannotEscapeTheShareCacheDirectory() {
        assertShareStaysInsideTheCache(
            HostileNames.DATABASE_TRAVERSAL,
            expectsCacheCopy = false,
        )
        assertShareStaysInsideTheCache(
            HostileNames.DATABASE_TRAVERSAL.removeSuffix(PDF_SUFFIX),
            expectsCacheCopy = true,
        )
    }

    @Test
    @TestId("SEC-41")
    fun theDoubleDecodedSmbNamePathCannotReintroduceASeparator() {
        assertShareStaysInsideTheCache(
            HostileNames.SMB_DOUBLE_ENCODED.removeSuffix(PDF_SUFFIX),
            expectsCacheCopy = true,
        )
    }

    @Test
    @TestId("SEC-42")
    fun aHostileDisplayNameDoesNotEscapeTheDurableCopyDirectory() {
        val escapeTarget = File(FileTree.externalRoot, ESCAPE_NAME)
        escapeTarget.delete()
        val externalBefore = FileTree.sharedStorageSnapshot()
        val shared = HostileNames.serve(HostileNames.SDCARD_TRAVERSAL)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            val record = Await.untilValue(
                "the record is repointed at the durable copy",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeDb.records().singleOrNull() },
            ) { it != null && it.sourceUri == shared.toString() }!!

            val copies = durableCopiesPlusPrivateImports()
            assertEquals(
                "the hostile name must produce exactly one copy, the copies were $copies",
                1,
                copies.size,
            )
            val copyName = copies.single()
            assertFalse(
                "sanitizeFileName replaces every path separator, so the copy name \"$copyName\" may hold none",
                copyName.contains('/') || copyName.contains('\\'),
            )
            val copyUris = IntakeReader.durableCopies().map { it.uri.toString() } +
                IntakeReader.importedFiles().map { Uri.fromFile(it).toString() }
            assertTrue(
                "the record must point at the copy that was written, it reads ${record.uri} and the copies " +
                    "are $copyUris",
                record.uri.toString() in copyUris,
            )
        }

        assertFalse(
            "no file may be written at ${escapeTarget.absolutePath}",
            escapeTarget.exists(),
        )
        val added = FileTree.diff(externalBefore, FileTree.sharedStorageSnapshot()).added
        val outsideTheCopyFolder = added.filter { !it.path.contains(DURABLE_COPY_FRAGMENT) }
        assertEquals(
            "the durable copy path may write nowhere on shared storage but its own folder, it also wrote " +
                outsideTheCopyFolder,
            emptyList<FileFacts>(),
            outsideTheCopyFolder,
        )
    }

    @Test
    @TestId("SEC-43")
    fun anExtremelyLongOrEmptyDisplayNameProducesAUsableFilename() {
        for ((label, displayName) in HOSTILE_LENGTH_NAMES) {
            IntakeReader.deleteDurableCopies()
            AppDatabase.getInstance(TestEnvironment.targetContext).clearAllTables()
            val shared = HostileNames.serve(displayName)

            ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
                reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
                reader.awaitFullQualityRender()

                val copies = Await.untilValue(
                    "the $label name produces a durable copy",
                    Await.LONG_TIMEOUT_MS,
                    read = { durableCopiesPlusPrivateImports() },
                ) { it.size == 1 }
                val name = copies.single()
                val baseName = name.removeSuffix(PDF_SUFFIX)

                assertTrue(
                    "the $label name must produce a base name of at most $MAX_BASE_NAME_LENGTH characters, " +
                        "\"$name\" has ${baseName.length}",
                    baseName.length <= MAX_BASE_NAME_LENGTH,
                )
                assertTrue(
                    "the $label name must fit the filesystem component byte limit, \"$name\" uses " +
                        "${name.toByteArray(Charsets.UTF_8).size} bytes",
                    name.toByteArray(Charsets.UTF_8).size <= MAX_FILE_NAME_BYTES,
                )
                assertTrue(
                    "the $label name must produce a non empty base name, it produced \"$name\"",
                    baseName.isNotBlank(),
                )
                assertTrue(
                    "the $label name must produce a pdf, it produced \"$name\"",
                    name.endsWith(PDF_SUFFIX, ignoreCase = true),
                )
            }

            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
            HostileNames.clear()
        }
    }

    private fun assertShareStaysInsideTheCache(displayName: String, expectsCacheCopy: Boolean) {
        IntentStubs.stubChooser()
        val database = File(FileTree.dataDirectory, DATABASE_PATH)
        val dataBefore = FileTree.snapshot(FileTree.dataDirectory)
        val sharedBefore = FileTree.sharedStorageSnapshot()
        val databaseBefore = dataBefore.factsFor(database.absolutePath)
        val shared = HostileNames.serve(displayName)

        ActivityScenario.launch<MainActivity>(StubSharer.sharedViewIntent(shared)).use {
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            SecurityReader.openAction(R.string.share_file)
            val payload = ShareIntents.payloadOf(ShareIntents.awaitChooser(Await.LONG_TIMEOUT_MS))
            val stream = IntentCompat.getParcelableExtra(payload, Intent.EXTRA_STREAM, Uri::class.java)
            assertTrue("the share payload must carry a stream", stream != null)

            if (expectsCacheCopy) {
                assertEquals(
                    "a name the source cannot be shared under directly must be staged through the app's own " +
                        "FileProvider, the stream read $stream",
                    "${TestEnvironment.targetPackage}$FILE_PROVIDER_SUFFIX",
                    stream?.authority,
                )
                assertTrue(
                    "the staged copy must sit under the share path the provider declares, the stream read " +
                        "$stream",
                    stream?.path?.startsWith(SHARE_PATH_PREFIX) == true,
                )
            }
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        val added = FileTree.diff(dataBefore, FileTree.snapshot(FileTree.dataDirectory)).added
        val shareWrites = added.filter { it.path.contains(SHARE_CACHE_FRAGMENT) }
        if (expectsCacheCopy) {
            assertEquals(
                "the share path must stage exactly one file, it wrote $shareWrites",
                1,
                shareWrites.size,
            )
        }
        for (facts in shareWrites) {
            val name = File(facts.path).name
            assertFalse(
                "ensurePdfFileName replaces every separator, so \"$name\" may hold none",
                name.contains('/') || name.contains('\\'),
            )
            assertTrue(
                "the staged copy must resolve inside the share cache, ${facts.path} resolves to " +
                    File(facts.path).canonicalPath,
                File(facts.path).canonicalPath.startsWith(shareCacheRoot()),
            )
        }

        val sharedAdded = FileTree.diff(sharedBefore, FileTree.sharedStorageSnapshot()).added
        val documentRoots = listOf(shareCacheRoot(), importedRoot(), durableCopyRoot())
        val escaped = (added + sharedAdded).filter { facts ->
            val file = File(facts.path)
            val looksLikeTheShare = file.name.endsWith(PDF_SUFFIX, ignoreCase = true) ||
                file.name.contains(ESCAPE_STEM, ignoreCase = true)
            looksLikeTheShare && documentRoots.none { root -> file.canonicalPath.startsWith(root + File.separator) }
        }
        assertEquals(
            "the hostile name \"$displayName\" wrote a document outside the share cache, the private import " +
                "folder and the durable copy folder",
            emptyList<FileFacts>(),
            escaped,
        )

        val databaseAfter = FileTree.snapshot(FileTree.dataDirectory).factsFor(database.absolutePath)
        if (databaseBefore != null) {
            assertEquals(
                "the Room database must be untouched in size and modification time",
                databaseBefore,
                databaseAfter,
            )
        }

        HostileNames.clear()
    }

    private fun shareCacheRoot(): String =
        File(TestEnvironment.targetContext.cacheDir, SHARE_DIRECTORY).canonicalPath

    private fun importedRoot(): String =
        IntakeReader.filesDirectory(IntakeReader.IMPORTED_DIRECTORY).canonicalPath

    private fun durableCopyRoot(): String =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            DURABLE_COPY_FOLDER,
        ).canonicalPath

    private fun durableCopiesPlusPrivateImports(): List<String> =
        IntakeReader.durableCopies().map(DurableCopyRow::displayName) +
            IntakeReader.importedFiles().map { it.name }

    private companion object {
        const val PDF_SUFFIX = ".pdf"
        const val ESCAPE_NAME = "escape.pdf"
        const val DATABASE_PATH = "databases/app-db.db"
        const val SHARE_DIRECTORY = "share"
        const val SHARE_CACHE_FRAGMENT = "/cache/share/"
        const val DURABLE_COPY_FRAGMENT = "/MJ PDF/"
        const val DURABLE_COPY_FOLDER = "MJ PDF"
        const val ESCAPE_STEM = "escape"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val SHARE_PATH_PREFIX = "/share/"
        const val MAX_BASE_NAME_LENGTH = 120
        const val MAX_FILE_NAME_BYTES = 124

        val HOSTILE_LENGTH_NAMES = listOf(
            "4000 character" to HostileNames.LONG_NAME,
            "multibyte" to ("文".repeat(200) + PDF_SUFFIX),
            "blank" to HostileNames.BLANK,
            "dots only" to HostileNames.DOTS_ONLY,
        )
    }
}
