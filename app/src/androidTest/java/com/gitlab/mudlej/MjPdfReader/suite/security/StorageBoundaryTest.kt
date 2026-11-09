// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.os.Environment
import android.system.Os
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.ui.home.ScanMode
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.artifacts.MediaStoreProbe
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeReader
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.DocumentLoadState
import com.gitlab.mudlej.MjPdfReader.ui.reader.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class StorageBoundaryTest {
    private val suite = SuiteRules.standard()
    private val server = SecurityServer()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(suite).around(server)

    private val reader = ReaderRobot()

    @Before
    fun leaveTheScannerOff() {
        SettingsSeeder.seed(Preferences.scanModeKey, ScanMode.NOT_CONFIGURED.name)
        removeEscapeDownloads()
    }

    @After
    fun removeOwnedDownloads() {
        removeEscapeDownloads()
    }

    @Test
    @TestId("SEC-44")
    fun aHostileUrlPathSegmentCannotEscapeTheDownloadsDirectory() {
        val escapeTarget = File(FileTree.externalRoot, ESCAPE_NAME)
        escapeTarget.delete()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val externalBefore = FileTree.sharedStorageSnapshot()

        val url = server.servePdf(TRAVERSAL_PATH, SecurityFixtures.file(SecurityFixtures.NORMAL))

        reader.withExternalIntent(SecurityReader.viewIntent(android.net.Uri.parse(url), null)) {
            reader.acceptCleartextWarning()
            reader.awaitShown()
            reader.awaitLoaded(Await.LONG_TIMEOUT_MS)
            reader.awaitFullQualityRender()

            val cached = Await.untilValue(
                "the downloaded document lands in the online cache",
                Await.LONG_TIMEOUT_MS,
                read = { IntakeReader.onlineCacheFiles().filter { !it.name.endsWith(PART_SUFFIX) } },
            ) { it.size == 1 }.single()
            assertTrue(
                "the cache entry must be named by the SHA-256 of the url, it read \"${cached.name}\"",
                cached.name.matches(SHA256_NAME)
            )

            Await.until(
                "the automatic Downloads copy reports an outcome",
                Await.LONG_TIMEOUT_MS,
            ) {
                Snackbars.currentText() != null ||
                    FileTree.diff(externalBefore, FileTree.sharedStorageSnapshot()).added.isNotEmpty()
            }
        }

        Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)

        assertFalse(
            "the traversal must not produce a file at ${escapeTarget.absolutePath}",
            escapeTarget.exists(),
        )
        val added = FileTree.diff(externalBefore, FileTree.sharedStorageSnapshot()).added
        val outsideDownloads = added.filterNot { it.path.startsWith(downloads.absolutePath) }
        assertEquals(
            "the automatic Downloads copy may write nowhere else on shared storage, it also wrote " +
                outsideDownloads,
            emptyList<FileFacts>(),
            outsideDownloads,
        )
        for (facts in added) {
            val name = File(facts.path).name
            assertFalse(
                "a file written into Downloads may carry no separator in its name, \"$name\" does",
                name.contains('/') || name.contains('\\'),
            )
            assertTrue(
                "every written file must resolve inside Downloads, ${facts.path} resolves to " +
                    File(facts.path).canonicalPath,
                File(facts.path).canonicalPath.startsWith(downloads.canonicalPath),
            )
        }
    }

    @Test
    @TestId("SEC-46")
    @TestDuration(DurationCategory.LONG)
    fun openingAHostileDocumentWritesNothingOutsideTheAppsOwnDirectories() {
        val dataBefore = FileTree.snapshot(FileTree.dataDirectory)
        val externalBefore = FileTree.sharedStorageSnapshot()

        for (name in SecurityFixtures.structuralFixtures()) {
            val fixture = SecurityFixtures.file(name)
            ActivityScenario.launch<MainActivity>(ReaderRobot.viewIntent(fixture)).use {
                reader.awaitShown()
                val state = reader.awaitLoadSettled(Await.LONG_TIMEOUT_MS)
                if (state.loadState != DocumentLoadState.FAILED && (state.pageCount ?: 0) > 0) {
                    reader.awaitRenderIdle()
                }
                reader.pressBack()
            }
            Screens.awaitGone(MainActivity::class.java, Await.LONG_TIMEOUT_MS)
        }

        val dataDiff = FileTree.diff(dataBefore, FileTree.snapshot(FileTree.dataDirectory))
        val unexpected = (dataDiff.added + dataDiff.modified.map { it.second }).filter { facts ->
            ALLOWED_DATA_FRAGMENTS.none { facts.path.contains(it) }
        }
        assertEquals(
            "reading a document may write only to the app's own bookkeeping directories, it also wrote " +
                unexpected,
            emptyList<FileFacts>(),
            unexpected,
        )

        val externalDiff = FileTree.diff(externalBefore, FileTree.sharedStorageSnapshot())
        assertTrue(
            "reading a document must write nothing at all on shared storage:\n${externalDiff.describe()}",
            externalDiff.isEmpty,
        )
    }

    @Test
    @TestId("SEC-47")
    fun aScreenshotIsNoMoreReadableThanTheDirectoryItLandsIn() {
        val pictures = File(FileTree.externalRoot, MediaStoreProbe.screenshotPicturesPath)
        val before = FileTree.snapshot(pictures).entries.keys
        val dataBefore = FileTree.snapshot(FileTree.dataDirectory)
        val sharedBefore = FileTree.sharedStorageSnapshot()
        val mediaBefore = MediaStoreProbe.filesUnder(MediaStoreProbe.screenshotPicturesPath)
            .mapTo(HashSet()) { it.uri }

        try {
            ActivityScenario.launch<MainActivity>(
                SecurityReader.fixtureViewIntent(SecurityFixtures.HIGHLIGHTS)
            ).use {
                reader.awaitReady()
                reader.awaitFullQualityRender()

                SecurityReader.openAction(R.string.screenshot)
                val screenshot = Await.untilValue(
                    "the screenshot lands in the public gallery pictures directory",
                    Await.LONG_TIMEOUT_MS,
                    read = { FileTree.snapshot(pictures).entries.keys - before },
                ) { it.isNotEmpty() }.first()

                val mode = Os.stat(screenshot).st_mode
                val directoryMode = Os.stat(pictures.absolutePath).st_mode
                assertEquals(
                    "a screenshot must carry no permission bit its directory lacks. File ${modeString(mode)}, " +
                        "directory ${modeString(directoryMode)}",
                    0,
                    mode and PERMISSION_BITS and directoryMode.inv(),
                )
                assertEquals(
                    "a screenshot must not be world writable, its mode is ${modeString(mode)}",
                    0,
                    mode and OTHER_WRITE,
                )
                assertEquals(
                    "a screenshot must not be world readable, its mode is ${modeString(mode)}",
                    0,
                    mode and OTHER_READ,
                )

                val screenshotName = File(screenshot).name
                val privateDuplicates = FileTree.diff(dataBefore, FileTree.snapshot(FileTree.dataDirectory)).added
                    .filter { File(it.path).name == screenshotName }
                assertEquals(
                    "the screenshot must exist only in the gallery, the app's private storage also holds " +
                        privateDuplicates,
                    emptyList<FileFacts>(),
                    privateDuplicates,
                )
            }
        } finally {
            val addedToSharedStorage = FileTree.diff(sharedBefore, FileTree.sharedStorageSnapshot()).added
            (FileTree.snapshot(pictures).entries.keys - before).forEach { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
            MediaStoreProbe.filesUnder(MediaStoreProbe.screenshotPicturesPath)
                .filterNot { it.uri in mediaBefore }
                .forEach { row -> TestEnvironment.targetContext.contentResolver.delete(row.uri, null, null) }
            addedToSharedStorage.forEach { facts ->
                val file = File(facts.path)
                Fixtures.deleteMediaStoreRowsUnder(file)
                file.takeIf { it.exists() }?.delete()
            }
        }
    }

    @Test
    @TestId("SEC-48")
    fun theFileProviderExposesOnlyTheShareCacheDirectory() {
        val context = TestEnvironment.targetContext
        val authority = "${context.packageName}.fileprovider"

        val refused = listOf(
            File(context.filesDir, "annotations"),
            File(context.applicationInfo.dataDir, "databases/app-db.db"),
            File(context.cacheDir, "online-pdf"),
        )
        for (target in refused) {
            val outcome = runCatching { FileProvider.getUriForFile(context, authority, target) }
            assertTrue(
                "file_paths.xml declares only the share cache, so ${target.absolutePath} must be refused. " +
                    "It produced ${outcome.getOrNull()}",
                outcome.exceptionOrNull() is IllegalArgumentException,
            )
        }

        val shareDirectory = File(context.cacheDir, "share").apply { mkdirs() }
        val staged = File(shareDirectory, "mjpdf-security-probe.pdf")
        staged.writeBytes(ByteArray(1))
        try {
            val uri = FileProvider.getUriForFile(context, authority, staged)
            assertEquals("the exposed URI must carry the app's own authority", authority, uri.authority)
            assertTrue(
                "the exposed URI must sit under the one declared path, it read $uri",
                uri.path?.startsWith("/share/") == true,
            )
        } finally {
            staged.delete()
        }

        val declared = context.resources.getXml(R.xml.file_paths)
        var pathElements = 0
        var event = declared.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && declared.name != "paths") {
                pathElements++
            }
            event = declared.next()
        }
        assertEquals(
            "the installed file_paths.xml must declare exactly one path, it declares $pathElements",
            1,
            pathElements,
        )
    }

    private fun modeString(mode: Int): String = Integer.toOctalString(mode)

    private fun removeEscapeDownloads() {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloads.listFiles().orEmpty()
            .filter { it.name.endsWith(ESCAPE_NAME) }
            .forEach { file ->
                MediaStoreProbe.deleteFileNamed(file.name)
                file.delete()
            }
    }

    private companion object {
        const val ESCAPE_NAME = "escape.pdf"
        const val TRAVERSAL_PATH = "/a%2F..%2F..%2Fescape.pdf"
        const val PART_SUFFIX = ".part"
        const val PERMISSION_BITS = 0b111_111_111
        const val OTHER_WRITE = 0b000_000_010
        const val OTHER_READ = 0b000_000_100

        val SHA256_NAME = Regex("[0-9a-f]{64}\\.pdf")

        val ALLOWED_DATA_FRAGMENTS = listOf(
            "/databases/",
            "/shared_prefs/",
            "/cache/covers/",
            "/cache/previews/",
            "/files/annotations/",
            "/cache/WebView",
            "/code_cache/",
            "/no_backup/",
            "/cache/app-db.db.lck",
            "/files/profileInstalled",
        )
    }
}
