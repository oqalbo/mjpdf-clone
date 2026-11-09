// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.ConditionTimeoutException
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object ScanFixtures {

    const val BULK = "bulk"
    const val HUGE = "huge"
    const val MIXED = "mixed"
    const val NESTED = "nested"
    const val DUPE = "dupe"

    const val TWIN_A = "twin-a.pdf"
    const val TWIN_B = "twin-b.pdf"

    const val NORMAL = "normal.pdf"
    const val LARGE = "large.pdf"
    const val CORRUPT = "corrupt.pdf"

    const val BULK_COUNT = 40
    const val HUGE_COUNT = 500
    const val NESTED_DEEP_RELATIVE = "a/b/c/deep.pdf"
    const val TWIN_BYTES = 3L * 1024 * 1024
    const val LARGE_PAGE_COUNT = 500

    val MIXED_INDEXED = listOf("report.pdf", "REPORT-UPPER.PDF")
    val MIXED_IGNORED = listOf("notes.txt", "cover.jpg", "book.epub", "trap.pdf.txt")

    val seededRoot: File = File(Fixtures.externalRoot, "scan")

    val downloadRoot: File = File(Fixtures.externalRoot, "Download")

    fun seededTree(name: String): File = requireExisting(File(seededRoot, name)) { it.isDirectory }

    fun seededFile(name: String): File = requireExisting(File(seededRoot, name)) { it.isFile }

    fun sharedFile(name: String): File = requireExisting(File(downloadRoot, name)) { it.isFile }

    fun bulkMember(index: Int): File = seededFile("$BULK/${bulkName(index)}")

    fun bulkName(index: Int): String = "bulk-%02d.pdf".format(index)

    fun copyTree(name: String, targetDirectory: File): File =
        copyDirectory(seededTree(name), File(targetDirectory, name))

    fun copyFile(source: File, target: File): File {
        Fixtures.ensureAllFilesAccess()
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        awaitLength(target, source.length())
        scan(listOf(target))
        return target
    }

    fun copyBulkMembers(range: IntRange, targetDirectory: File): List<File> {
        Fixtures.ensureAllFilesAccess()
        targetDirectory.mkdirs()
        val copied = range.map { index ->
            val source = bulkMember(index)
            val target = File(targetDirectory, source.name)
            source.copyTo(target, overwrite = true)
            awaitLength(target, source.length())
            target
        }
        scan(copied)
        return copied
    }

    fun copySharedAs(sourceName: String, targetDirectory: File, targetName: String): File =
        copyFile(sharedFile(sourceName), File(targetDirectory, targetName))

    fun placeWithoutScan(source: File, target: File): File {
        Fixtures.ensureAllFilesAccess()
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        awaitLength(target, source.length())
        return target
    }

    fun createDirectory(target: File): File {
        Fixtures.ensureAllFilesAccess()
        if (!target.exists() && !target.mkdirs()) {
            throw ConditionTimeoutException("Could not create ${target.absolutePath}")
        }
        return target
    }

    fun writeMarker(target: File): File {
        Fixtures.ensureAllFilesAccess()
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(0))
        Await.until("the marker ${target.absolutePath} exists") { target.isFile }
        return target
    }

    fun delete(target: File) {
        Fixtures.delete(target)
        Await.until("${target.absolutePath} is gone", Await.LONG_TIMEOUT_MS) { !target.exists() }
    }

    fun move(source: File, target: File): File {
        Fixtures.ensureAllFilesAccess()
        target.parentFile?.mkdirs()
        val sourcePath = source.absolutePath
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        Await.until("$sourcePath has moved to ${target.absolutePath}", Await.LONG_TIMEOUT_MS) {
            target.isFile && !File(sourcePath).exists()
        }
        Fixtures.deleteMediaStoreRowsUnder(File(sourcePath))
        scan(listOf(target))
        return target
    }

    fun scan(files: List<File>) {
        if (files.isEmpty()) return
        Fixtures.scanAndWait(files)
    }

    fun scanTree(root: File) {
        scan(pdfsUnder(root))
    }

    fun pdfsUnder(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension.equals(PDF_EXTENSION, ignoreCase = true) }.toList()

    fun prefixOf(directory: File): String = directory.absolutePath.trimEnd(File.separatorChar) + File.separator

    private fun copyDirectory(source: File, target: File): File {
        Fixtures.ensureAllFilesAccess()
        val expected = pdfsUnder(source).size
        target.parentFile?.mkdirs()
        if (target.exists()) {
            target.deleteRecursively()
        }
        if (!source.copyRecursively(target, overwrite = true)) {
            throw ConditionTimeoutException(
                "Copying ${source.absolutePath} to ${target.absolutePath} did not complete"
            )
        }
        Await.until(
            "the copy of ${source.absolutePath} at ${target.absolutePath} holds $expected PDFs",
            Await.LONG_TIMEOUT_MS,
        ) { pdfsUnder(target).size == expected }
        scanTree(target)
        return target
    }

    private fun awaitLength(target: File, expected: Long) {
        Await.until("the copy at ${target.absolutePath} reaches $expected bytes", Await.LONG_TIMEOUT_MS) {
            target.isFile && target.length() == expected
        }
    }

    private fun requireExisting(candidate: File, predicate: (File) -> Boolean): File {
        Fixtures.ensureAllFilesAccess()
        if (!predicate(candidate)) {
            throw AssertionError(
                "The seeded fixture ${candidate.absolutePath} is missing. python3 testing/runner/run.py pushes " +
                    "testing/fixtures/out/scan into ${seededRoot.absolutePath} and the top level fixtures into " +
                    "${downloadRoot.absolutePath} before the suite starts, so run it, or push by hand, " +
                    "before running this class."
            )
        }
        return candidate
    }

    private const val PDF_EXTENSION = "pdf"
}
