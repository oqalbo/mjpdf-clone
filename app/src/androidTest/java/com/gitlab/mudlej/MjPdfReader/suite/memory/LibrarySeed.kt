// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.memory

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.core.io.DocumentIdentity
import com.gitlab.mudlej.MjPdfReader.data.AppDatabase
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File
import java.time.LocalDateTime

object LibrarySeed {

    const val SCAN_TREE = "huge"
    const val REQUIRED_DOCUMENTS = 300

    val scanRoot: File = File(Fixtures.externalRoot, "scan")

    fun sourceDocuments(count: Int = REQUIRED_DOCUMENTS): List<File> {
        Fixtures.ensureAllFilesAccess()
        val tree = File(scanRoot, SCAN_TREE)
        val files = tree.listFiles()
            ?.filter { it.isFile && it.name.endsWith(PDF_SUFFIX, ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.size < count) {
            throw AssertionError(
                "${tree.absolutePath} holds ${files.size} documents, and this test needs $count distinct " +
                    "ones. The runner pushes testing/fixtures/out/scan onto the device, so a short tree " +
                    "means the scan fixtures were not seeded for this run. This surface deliberately draws " +
                    "from the scan tree rather than from fifty-distinct, which only holds 50."
            )
        }
        return files.take(count)
    }

    fun seedRecords(count: Int = REQUIRED_DOCUMENTS): List<SeededDocument> {
        val database = AppDatabase.getInstance(TestEnvironment.targetContext)
        val documents = sourceDocuments(count).map { file ->
            val identity = DocumentIdentity.of(file)
                ?: throw AssertionError("No document identity could be computed for ${file.absolutePath}")
            SeededDocument(file, identity.identity)
        }

        val distinct = documents.map { it.hash }.toSet()
        if (distinct.size != documents.size) {
            throw AssertionError(
                "The seeded library must hold ${documents.size} distinct identities but produced " +
                    "${distinct.size}. DocumentIdentity is content derived, so duplicates mean the scan " +
                    "tree carries repeated content and a cover cache test over it would measure one cover " +
                    "many times rather than many covers."
            )
        }

        val now = LocalDateTime.now()
        documents.forEachIndexed { index, document ->
            database.pdfRecordDao().insert(
                PdfRecord(
                    document.hash,
                    0,
                    Uri.fromFile(document.file),
                    document.file.length().toInt(),
                    document.file.nameWithoutExtension,
                    null,
                    now.minusMinutes(index.toLong()),
                    ReadingStatus.UNSET,
                    false,
                )
            )
        }
        return documents
    }

    data class SeededDocument(val file: File, val hash: String)

    private const val PDF_SUFFIX = ".pdf"
}
