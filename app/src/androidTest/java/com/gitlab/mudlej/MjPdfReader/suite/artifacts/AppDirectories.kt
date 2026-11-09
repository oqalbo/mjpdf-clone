// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.artifacts

import android.os.Environment
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object AppDirectories {

    const val STAGING_PREFIX = "annotation-save-"
    const val STAGING_SUFFIX = ".pdf"
    const val PRINT_PREFIX = "print-"
    const val QUOTE_PREFIX = "MJ_quote_"

    val cache: File get() = TestEnvironment.targetContext.cacheDir

    val files: File get() = TestEnvironment.targetContext.filesDir

    val printStaging: File get() = File(cache, "print")

    val shareCache: File get() = File(cache, "share")

    val onlineCache: File get() = File(cache, "online-pdf")

    val annotationRescue: File get() = File(files, "annotation-rescue")

    val importedCopies: File get() = File(files, "imported")

    val privatePictures: File?
        get() = TestEnvironment.targetContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

    val publicDownloads: File get() = File(Fixtures.externalRoot, "Download")

    val publicDurableCopies: File get() = File(File(Fixtures.externalRoot, "Documents"), "MJ PDF")

    fun partialFilesIn(directory: File?): List<String> =
        namesIn(directory).filter { it.endsWith(".part") || it.contains(".part.") }

    fun namesIn(directory: File?): List<String> =
        directory?.listFiles()?.map { it.name }?.sorted().orEmpty()

    fun filesIn(directory: File?): List<File> = directory?.listFiles()?.sortedBy { it.name }.orEmpty()

    fun stagingLeftovers(): List<String> =
        namesIn(cache).filter { it.startsWith(STAGING_PREFIX) && it.endsWith(STAGING_SUFFIX) }

    fun printStagedFiles(): List<File> =
        filesIn(printStaging).filter { it.name.startsWith(PRINT_PREFIX) && it.name.endsWith(STAGING_SUFFIX) }

    fun quoteCards(): List<File> =
        filesIn(shareCache).filter { it.name.startsWith(QUOTE_PREFIX) && it.name.endsWith(".png") }

    fun awaitPrintStagedFile(timeoutMs: Long = Await.LONG_TIMEOUT_MS): File =
        Await.untilValue(
            "a staged print file appears in ${printStaging.absolutePath} with a non zero length",
            timeoutMs,
            read = { printStagedFiles().filter { it.length() > 0L } },
        ) { it.isNotEmpty() }.first()

    fun awaitPrintStagingEmpty(timeoutMs: Long = Await.LONG_TIMEOUT_MS) {
        Await.until("the print staging directory is emptied", timeoutMs) { printStagedFiles().isEmpty() }
    }

    fun assertNoStagingLeftovers(windowMs: Long = STABLE_WINDOW_MS) {
        Await.assertThroughout(
            condition = "no annotation save staging file is left in the cache",
            windowMs = windowMs,
        ) { stagingLeftovers().isEmpty() }
    }

    fun ageFile(file: File, millisAgo: Long) {
        Fixtures.setLastModified(file, System.currentTimeMillis() - millisAgo)
    }

    fun writePlaceholder(directory: File, name: String, bytes: ByteArray = PLACEHOLDER_BYTES): File {
        directory.mkdirs()
        val file = File(directory, name)
        file.writeBytes(bytes)
        return file
    }

    fun clearAll() {
        runCatching { printStaging.deleteRecursively() }
        runCatching { shareCache.deleteRecursively() }
        runCatching { annotationRescue.deleteRecursively() }
        runCatching { importedCopies.deleteRecursively() }
        runCatching { stagingLeftovers().forEach { File(cache, it).delete() } }
        runCatching { privatePictures?.listFiles()?.forEach { it.delete() } }
    }

    const val STABLE_WINDOW_MS = 1_000L

    private val PLACEHOLDER_BYTES = ByteArray(64)
}
