// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.scan

import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataFailure
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.SourceFingerprint
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import java.io.File

object ScanCache {

    const val STABLE_ABSENCE_MS = 1_500L

    private val database: AppDatabase
        get() = AppDatabase.getInstance(TestEnvironment.targetContext.applicationContext)

    fun all(): List<ScannedPdfEntry> = database.scannedPdfEntryDao().findAll()

    fun under(prefix: String): List<ScannedPdfEntry> =
        all().filter { it.path.startsWith(prefix) }.sortedBy { it.path }

    fun under(directory: File): List<ScannedPdfEntry> = under(ScanFixtures.prefixOf(directory))

    fun countUnder(prefix: String): Int = under(prefix).size

    fun countUnder(directory: File): Int = under(directory).size

    fun pathsUnder(prefix: String): List<String> = under(prefix).map { it.path }

    fun pathsUnder(directory: File): List<String> = under(directory).map { it.path }

    fun namesUnder(directory: File): List<String> = pathsUnder(directory).map { File(it).name }

    fun byPath(path: String): ScannedPdfEntry? = all().firstOrNull { it.path == path }

    fun byPath(file: File): ScannedPdfEntry? = byPath(file.absolutePath)

    fun insert(entry: ScannedPdfEntry) {
        database.scannedPdfEntryDao().upsertAll(listOf(entry))
    }

    fun insertLegacyRow(file: File, legacyHash: String): ScannedPdfEntry {
        val fingerprint = SourceFingerprint.normalized(
            file.absolutePath,
            file.length(),
            file.lastModified(),
        )
        val entry = ScannedPdfEntry(
            path = fingerprint.path,
            size = fingerprint.size,
            lastModified = fingerprint.lastModified,
            hash = legacyHash,
            pageCount = 0,
        )
        insert(entry)
        return entry
    }

    fun awaitRow(
        path: String,
        condition: String,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
        predicate: (ScannedPdfEntry?) -> Boolean,
    ): ScannedPdfEntry? = Await.untilValue(
        condition = condition,
        timeoutMs = timeoutMs,
        read = { byPath(path) },
        predicate = predicate,
    )

    fun awaitRowExists(file: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ScannedPdfEntry =
        awaitRow(file.absolutePath, "a ScannedPdfCache row exists for ${file.absolutePath}", timeoutMs) {
            it != null
        }!!

    fun awaitHashed(file: File, timeoutMs: Long = Await.LONG_TIMEOUT_MS): ScannedPdfEntry =
        awaitRow(file.absolutePath, "the row for ${file.absolutePath} carries a hash", timeoutMs) {
            !it?.hash.isNullOrEmpty()
        }!!

    fun awaitCountUnder(
        directory: File,
        expected: Int,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): List<ScannedPdfEntry> = awaitCountUnder(ScanFixtures.prefixOf(directory), expected, timeoutMs)

    fun awaitCountUnder(
        prefix: String,
        expected: Int,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): List<ScannedPdfEntry> = Await.untilValue(
        condition = "ScannedPdfCache holds exactly $expected rows under $prefix",
        timeoutMs = timeoutMs,
        read = { under(prefix) },
    ) { it.size == expected }

    fun awaitAllHashedUnder(
        directory: File,
        expected: Int,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): List<ScannedPdfEntry> = Await.untilValue(
        condition = "all $expected rows under ${directory.absolutePath} carry a hash and a page count",
        timeoutMs = timeoutMs,
        read = { under(directory) },
    ) { rows ->
        rows.size == expected && rows.all {
            !it.hash.isNullOrEmpty() && it.pageCount > 0 &&
                it.metadataState == ScannedPdfMetadataState.COMPLETE
        }
    }

    fun awaitFailed(
        file: File,
        failure: ScannedPdfMetadataFailure,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): ScannedPdfEntry = awaitRow(
        file.absolutePath,
        "the row for ${file.absolutePath} fails with $failure",
        timeoutMs,
    ) {
        it?.metadataState == ScannedPdfMetadataState.FAILED && it.metadataFailure == failure
    }!!

    fun awaitComplete(
        file: File,
        pageCount: Int,
        timeoutMs: Long = Await.LONG_TIMEOUT_MS,
    ): ScannedPdfEntry = awaitRow(
        file.absolutePath,
        "the row for ${file.absolutePath} completes with $pageCount pages",
        timeoutMs,
    ) {
        it?.metadataState == ScannedPdfMetadataState.COMPLETE && it.pageCount == pageCount
    }!!

    fun assertNoRowsUnder(directory: File, windowMs: Long = STABLE_ABSENCE_MS) {
        val prefix = ScanFixtures.prefixOf(directory)
        Await.assertThroughout(
            condition = "ScannedPdfCache holds no row under $prefix",
            windowMs = windowMs,
        ) { under(prefix).isEmpty() }
    }

    fun assertCountUnderStays(directory: File, expected: Int, windowMs: Long = STABLE_ABSENCE_MS) {
        val prefix = ScanFixtures.prefixOf(directory)
        Await.assertThroughout(
            condition = "ScannedPdfCache keeps exactly $expected rows under $prefix",
            windowMs = windowMs,
        ) { under(prefix).size == expected }
    }

    fun assertUnhashed(entry: ScannedPdfEntry?, message: String) {
        if (entry == null) {
            throw AssertionError("$message: no ScannedPdfCache row exists at all")
        }
        if (!entry.hash.isNullOrEmpty() || entry.pageCount != 0) {
            throw AssertionError(
                "$message: the row for ${entry.path} reads hash=${entry.hash} pageCount=${entry.pageCount}, " +
                    "but an out of scope file must be cached without being hashed"
            )
        }
    }

    fun assertCurrentIdentity(entry: ScannedPdfEntry?, message: String): String {
        val hash = entry?.hash
        if (hash.isNullOrEmpty()) {
            throw AssertionError("$message: the row carries no hash")
        }
        if (DocumentIdentity.isLegacy(hash)) {
            throw AssertionError(
                "$message: the hash \"$hash\" does not start with ${DocumentIdentity.PREFIX}, " +
                    "so it is a legacy identity rather than the current one"
            )
        }
        return hash
    }

    fun identityOf(file: File): String = DocumentIdentity.of(file)?.identity
        ?: throw AssertionError("DocumentIdentity could not read ${file.absolutePath}")
}
