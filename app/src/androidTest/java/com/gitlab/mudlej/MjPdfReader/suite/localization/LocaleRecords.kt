// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.localization

import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfEntry
import com.gitlab.mudlej.MjPdfReader.data.entity.ScannedPdfMetadataState
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import com.gitlab.mudlej.MjPdfReader.suite.intake.IntakeFixtures
import com.gitlab.mudlej.MjPdfReader.suite.scan.ScanPrefs
import org.junit.rules.ExternalResource
import java.io.File
import java.time.LocalDateTime

object LocaleRecords {
    private val seededPaths = mutableSetOf<String>()

    const val DEFAULT_PAGES = 20

    fun seed(
        title: String,
        pages: Int = DEFAULT_PAGES,
        lastOpened: LocalDateTime = LocalDateTime.now(),
        file: File? = null,
    ): PdfRecord {
        val target = file?.let { copyFixture(it, title) } ?: distinctFixture(title)
        ScanPrefs.seedSelected(seedDirectory())
        ScanPrefs.seedFolderFlat(true)
        val identity = IntakeFixtures.expectedIdentity(target)
        val record = PdfRecord(
            hash = identity,
            pageNumber = 0,
            uri = target.toUri(),
            length = pages,
            fileName = title,
            password = null,
            lastOpened = lastOpened,
            reading = ReadingStatus.UNSET,
            favorite = false,
        )
        Records.database.pdfRecordDao().insert(record)
        val path = target.absolutePath
        Records.database.scannedPdfEntryDao().upsertAll(
            listOf(
                ScannedPdfEntry(
                    path = path,
                    size = target.length(),
                    lastModified = target.lastModified() / 1_000L * 1_000L,
                    hash = identity,
                    pageCount = pages,
                    metadataState = ScannedPdfMetadataState.COMPLETE,
                )
            )
        )
        seededPaths += path
        return record
    }

    fun seedMany(count: Int, pages: Int = DEFAULT_PAGES): List<PdfRecord> =
        (1..count).map { index -> seed(titleFor(index), pages) }

    fun titleFor(index: Int): String = "loc-record-$index"

    fun clear() {
        runCatching { Records.database.pdfRecordDao().deleteAll() }
        runCatching { seedDirectory().deleteRecursively() }
        runCatching { Records.database.scannedPdfEntryDao().deleteByPaths(seededPaths.toList()) }
        seededPaths.clear()
    }

    private fun copyFixture(source: File, title: String): File {
        val target = File(seedDirectory(), "${title.hashCode().toUInt().toString(16)}-${source.name}")
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        return target
    }

    private fun distinctFixture(title: String): File {
        val target = File(seedDirectory(), "${title.hashCode().toUInt().toString(16)}.pdf")
        target.parentFile?.mkdirs()
        LocaleFixtures.seeded(LocaleFixtures.NORMAL).copyTo(target, overwrite = true)
        target.appendText("\n% $title\n")
        return target
    }

    private fun seedDirectory(): File =
        File(Fixtures.perTestRoot, "locale-records")
}

class LocaleRecordsRule : ExternalResource() {
    override fun before() {
        LocaleRecords.clear()
    }

    override fun after() {
        LocaleRecords.clear()
    }
}
