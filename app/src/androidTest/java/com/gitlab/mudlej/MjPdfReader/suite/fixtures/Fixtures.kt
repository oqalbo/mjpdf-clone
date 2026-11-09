// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.fixtures

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import com.gitlab.mudlej.MjPdfReader.core.PermissionManager
import com.gitlab.mudlej.MjPdfReader.core.debug.StorageAccessOverride
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Shell
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Fixtures {

    val externalRoot: File = Environment.getExternalStorageDirectory()

    val perTestRoot: File = File(externalRoot, "Documents/mjpdf-test")

    const val MEDIA_SCAN_TIMEOUT_MS = 20_000L

    const val EXTERNAL_VOLUME = "external"
    const val PICKER_PREFIX = "00-picker-"
    private const val PICKER_FOLDER_COUNT = 32
    private val PICKER_FOLDER_NAME = Regex("000-picker-folder-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-(?:[0-9]|[12][0-9]|3[01])")

    fun pickerFixtureName(name: String): String = PICKER_PREFIX + name

    fun withCrowdedDownloads(block: () -> Unit) {
        ensureAllFilesAccess()
        val abandoned = seededRoot.listFiles().orEmpty().filter {
            it.isDirectory && PICKER_FOLDER_NAME.matches(it.name) && it.list()?.isEmpty() == true
        }
        abandoned.forEach(::delete)
        check(abandoned.none { it.exists() }) { "Could not remove abandoned picker regression folders" }
        val prefix = "000-picker-folder-${java.util.UUID.randomUUID()}"
        val directories = mutableListOf<File>()
        try {
            repeat(PICKER_FOLDER_COUNT) { index ->
                val directory = File(seededRoot, "$prefix-$index")
                check(directory.mkdir()) { "Could not create picker regression folder $directory" }
                directories.add(directory)
            }
            block()
        } finally {
            directories.forEach(::delete)
        }
    }

    fun directoryForTest(testId: String): File = File(perTestRoot, testId)

    fun createDirectoryForTest(testId: String): File {
        ensureAllFilesAccess()
        val directory = directoryForTest(testId)
        if (!directory.exists() && !directory.mkdirs()) {
            throw AssertionError("Could not create the per test directory ${directory.absolutePath}")
        }
        return directory
    }

    fun deleteDirectoryForTest(testId: String) {
        val directory = directoryForTest(testId)
        if (!directory.exists()) return
        deleteMediaStoreRowsUnder(directory)
        deleteRecursively(directory)
    }

    fun pushBytes(bytes: ByteArray, target: File): File {
        ensureAllFilesAccess()
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    val seededRoot: File = File(externalRoot, "Download")

    fun seededFixture(name: String, sourceDirectory: File = seededRoot): File = File(sourceDirectory, name)

    fun requireSeededFixture(
        name: String,
        sourceDirectory: File = seededRoot,
        ensureAccess: Boolean = true,
    ): File {
        if (ensureAccess) ensureAllFilesAccess()
        val fixture = seededFixture(name, sourceDirectory)
        if (!fixture.isFile) {
            throw AssertionError(
                "The seeded fixture ${fixture.absolutePath} is missing. python3 testing/runner/run.py pushes " +
                    "testing/fixtures/out into ${sourceDirectory.absolutePath} before the suite starts, so run it, " +
                    "or push the file by hand, before running this class."
            )
        }
        return fixture
    }

    fun pickerFixture(name: String): File {
        val source = requireSeededFixture(name)
        val target = seededFixture(pickerFixtureName(name))
        if (!target.isFile || target.length() != source.length()) {
            Shell.execute("cp ${shellPath(source)} ${shellPath(target)}")
            Await.until("the picker fixture ${target.absolutePath} appears", Await.LONG_TIMEOUT_MS) {
                target.isFile && target.length() == source.length()
            }
        }
        setLastModified(target, System.currentTimeMillis())
        scanAndWait(target)
        return target
    }

    fun pickerFixture(source: File, own: (File) -> Unit = {}): File {
        ensureAllFilesAccess()
        if (!source.isFile) {
            throw AssertionError("The source fixture ${source.absolutePath} is missing")
        }
        val target = seededFixture(pickerFixtureName("backup-${source.name}"))
        own(target)
        source.copyTo(target, overwrite = true)
        Await.until("the picker fixture ${target.absolutePath} appears", Await.LONG_TIMEOUT_MS) {
            target.isFile && target.length() == source.length()
        }
        setLastModified(target, System.currentTimeMillis())
        scanAndWait(target)
        return target
    }

    fun copyDeviceFixture(
        name: String,
        targetDirectory: File,
        sourceDirectory: File = seededRoot,
        targetName: String = name,
    ): File {
        val source = requireSeededFixture(name, sourceDirectory)
        targetDirectory.mkdirs()
        val target = File(targetDirectory, targetName)
        Shell.execute("cp ${shellPath(source)} ${shellPath(target)}")
        Await.until("the fixture copy ${target.absolutePath} appears", Await.LONG_TIMEOUT_MS) {
            target.isFile && target.length() == source.length()
        }
        scanAndWait(listOf(target))
        return target
    }
    fun replaceWithShellCopy(source: File, target: File): File {
        ensureAllFilesAccess()
        val parent = target.parentFile
            ?: throw AssertionError("The shell-seeded fixture ${target.absolutePath} has no parent directory")
        Shell.execute("mkdir -p ${shellPath(parent)}")
        deleteMediaStoreRowsUnder(target)
        Shell.execute("rm -f ${shellPath(target)}")
        Shell.execute("cp ${shellPath(source)} ${shellPath(target)}")
        Await.until("the shell-created fixture ${target.absolutePath} is complete", Await.LONG_TIMEOUT_MS) {
            shellFileExists(target) && target.length() == source.length()
        }
        return target
    }


    fun pushAsset(assetName: String, target: File): File {
        val bytes = TestEnvironment.testContext.assets.open(assetName).use { it.readBytes() }
        return pushBytes(bytes, target)
    }

    fun delete(target: File) {
        deleteMediaStoreRowsUnder(target)
        deleteRecursively(target)
    }

    fun setLastModified(target: File, epochMillis: Long) {
        if (!target.setLastModified(epochMillis)) {
            Shell.execute("touch -d @${epochMillis / 1000} ${shellPath(target)}")
        }
        Await.until("mtime of ${target.absolutePath} reads back as $epochMillis", Await.SHORT_TIMEOUT_MS) {
            target.lastModified() / 1000 == epochMillis / 1000
        }
    }

    fun readMode(target: File): String =
        Shell.execute("stat -c %a ${shellPath(target)}").trim()

    fun scanAndWait(files: List<File>, timeoutMs: Long = MEDIA_SCAN_TIMEOUT_MS): Map<String, Uri?> {
        if (files.isEmpty()) return emptyMap()
        val paths = files.map { it.absolutePath }.toTypedArray()
        val results = LinkedHashMap<String, Uri?>()
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(TestEnvironment.targetContext, paths, null) { path, uri ->
            synchronized(results) { results[path] = uri }
            latch.countDown()
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw AssertionError("Timed out after ${timeoutMs}ms waiting for the media scan of ${paths.toList()}")
        }
        return synchronized(results) { LinkedHashMap(results) }
    }

    fun scanAndWait(vararg files: File): Map<String, Uri?> = scanAndWait(files.toList())
    fun scanFromShellAndWait(file: File, timeoutMs: Long = MEDIA_SCAN_TIMEOUT_MS): Uri {
        check(file.isFile) { "Cannot scan a missing shell-created fixture: ${file.absolutePath}" }
        Shell.execute(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE " +
                "-d file://${shellPath(file)}"
        )
        return Await.untilNotNull(
            "the shell-triggered media scan publishes ${file.absolutePath}",
            timeoutMs,
        ) {
            mediaStoreUriFor(file)
        }
    }

    private fun mediaStoreUriFor(file: File): Uri? {
        val resolver = TestEnvironment.targetContext.contentResolver
        val collection = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA} = ?",
            arrayOf(file.absolutePath),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
    }

    fun contentUriCanOpen(uri: Uri): Boolean = runCatching {
        TestEnvironment.targetContext.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    fun mediaStoreOwnerPackage(uri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        return TestEnvironment.targetContext.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }


    fun deleteMediaStoreRowsUnder(target: File): Int {
        val resolver = TestEnvironment.targetContext.contentResolver
        val collection = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
        val selection = "${MediaStore.MediaColumns.DATA} = ? OR ${MediaStore.MediaColumns.DATA} LIKE ?"
        val arguments = arrayOf(target.absolutePath, "${target.absolutePath}/%")
        return runCatching { resolver.delete(collection, selection, arguments) }.getOrDefault(0)
    }

    fun ensureAllFilesAccess() {
        grantAllFilesAccess()
        Await.until("the all files access grant is visible to the app", Await.SHORT_TIMEOUT_MS) {
            hasAllFilesAccess() && PermissionManager.hasFullAccess(TestEnvironment.targetContext)
        }
    }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) hasLegacyStorageGrant()
        else Environment.isExternalStorageManager()

    fun grantAllFilesAccess() {
        StorageAccessOverride.clear()
        if (!hasAllFilesAccess()) {
            throw AssertionError(
                "Storage access for ${TestEnvironment.targetPackage} must be granted by the host before " +
                    "instrumentation starts. Use testing/runner/run.py or prepare the device grant before " +
                    "running tests through Gradle or the IDE."
            )
        }
    }

    fun grantAllFilesAccess(probeFile: File) {
        grantAllFilesAccess()
        Await.until("shared access is restored for ${probeFile.absolutePath}", Await.LONG_TIMEOUT_MS) {
            shellFileExists(probeFile) &&
                probeFile.canRead() &&
                PermissionManager.hasFullAccess(TestEnvironment.targetContext)
        }
    }

    private val LEGACY_STORAGE_PERMISSIONS = listOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    )

    private fun hasLegacyStorageGrant(): Boolean = LEGACY_STORAGE_PERMISSIONS.all {
        TestEnvironment.targetContext.checkPermission(it, Process.myPid(), Process.myUid()) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun simulateNoAllFilesAccess() {
        ensureAllFilesAccess()
        denyAllFilesAccess()
    }

    fun stopSimulatingAccess() {
        StorageAccessOverride.clear()
    }

    fun denyAllFilesAccess() {
        StorageAccessOverride.force(false)
        Await.until("the app reports simulated no all files access", Await.SHORT_TIMEOUT_MS) {
            !PermissionManager.hasFullAccess(TestEnvironment.targetContext)
        }
    }

    fun denyAllFilesAccess(probeFile: File) {
        denyAllFilesAccess()
        Await.until(
            "the shared file remains shell-visible while app access is simulated denied at ${probeFile.absolutePath}",
            Await.LONG_TIMEOUT_MS,
        ) {
            shellFileExists(probeFile) &&
                !PermissionManager.hasFullAccess(TestEnvironment.targetContext)
        }
    }

    fun shellFileExists(target: File): Boolean =
        Shell.executeQuietly("stat -c %F ${shellPath(target)}").trim() == "regular file"

    fun deleteFromShell(target: File) {
        Shell.execute("rm -f ${shellPath(target)}")
        Await.until("the shell removes ${target.absolutePath}", Await.SHORT_TIMEOUT_MS) {
            !shellFileExists(target)
        }
    }

    private fun deleteRecursively(target: File) {
        if (!target.exists()) return
        if (!target.deleteRecursively()) {
            Shell.execute("rm -rf ${shellPath(target)}")
        }
    }

    private fun shellPath(target: File): String {
        val path = target.absolutePath
        if (path.any { it.isWhitespace() }) {
            throw IllegalArgumentException(
                "Path $path contains whitespace, which UiAutomation.executeShellCommand cannot pass on because it " +
                    "splits the command on whitespace and never processes quotes. Fixture paths must be unspaced."
            )
        }
        return path
    }
}
