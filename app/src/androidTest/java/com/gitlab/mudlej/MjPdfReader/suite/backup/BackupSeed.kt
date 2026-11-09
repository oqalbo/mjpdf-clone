// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.backup

import android.content.SharedPreferences
import androidx.core.net.toUri
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.data.annotation.SourceKey
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfAnnotationSaveDestination
import com.gitlab.mudlej.MjPdfReader.data.entity.PdfRecord
import com.gitlab.mudlej.MjPdfReader.data.entity.ReadingStatus
import com.gitlab.mudlej.MjPdfReader.data.entity.UserBookmark
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import java.io.File
import java.time.LocalDateTime

object BackupSeed {
    val preferences: SharedPreferences get() = TestEnvironment.defaultPreferences

    fun record(
        hash: String,
        fileName: String = "$hash.pdf",
        pageNumber: Int = 0,
        password: String? = null,
        reading: ReadingStatus = ReadingStatus.UNSET,
        favorite: Boolean = false,
        lastOpened: LocalDateTime = LocalDateTime.now(),
        cropMargins: String? = null,
        cropMarginsVersion: Int = 0,
        autoScrollSpeed: Int? = null,
        readingDirectionOverride: String? = null,
        detectedReadingDirection: String? = null,
        documentTitle: String? = null,
        textModeJoinParagraphs: Boolean? = null,
        textModeDetectHeadings: Boolean? = null,
        textModeCodeBlocks: Boolean? = null,
        hidden: Boolean = false,
        uri: String = "file:///sdcard/Download/$fileName",
        sourceUri: String? = null,
        length: Int = 0,
    ): PdfRecord {
        val record = PdfRecord(
            hash = hash,
            pageNumber = pageNumber,
            uri = uri.toUri(),
            length = length,
            fileName = fileName,
            password = password,
            lastOpened = lastOpened,
            reading = reading,
            favorite = favorite,
            cropMargins = cropMargins,
            cropMarginsVersion = cropMarginsVersion,
            autoScrollSpeed = autoScrollSpeed,
            readingDirectionOverride = readingDirectionOverride,
            detectedReadingDirection = detectedReadingDirection,
            documentTitle = documentTitle,
            textModeJoinParagraphs = textModeJoinParagraphs,
            textModeDetectHeadings = textModeDetectHeadings,
            textModeCodeBlocks = textModeCodeBlocks,
            hidden = hidden,
            sourceUri = sourceUri,
        )
        Records.database.pdfRecordDao().insert(record)
        return record
    }

    fun seedRecords(count: Int, prefix: String = "h2seed"): List<PdfRecord> =
        (1..count).map { index -> record(hash = "$prefix$index", pageNumber = index) }

    fun records(): List<PdfRecord> = Records.all()

    fun bookmark(
        fileHash: String,
        pageIndex: Int,
        label: String? = null,
        sortOrder: Int = pageIndex,
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): UserBookmark {
        val bookmark = UserBookmark(
            fileHash = fileHash,
            pageIndex = pageIndex,
            label = label,
            createdAt = createdAt,
            sortOrder = sortOrder,
        )
        Records.database.userBookmarkDao().upsert(bookmark)
        return bookmark
    }

    fun saveDestination(
        sourceUri: String,
        destinationUri: String = "$sourceUri.saved",
        lastSavedHash: String? = null,
    ): PdfAnnotationSaveDestination {
        val destination = PdfAnnotationSaveDestination(
            sourceKey = SourceKey.of(sourceUri.toUri()),
            destinationUri = destinationUri,
            lastSavedHash = lastSavedHash,
            lastSavedAt = LocalDateTime.now(),
        )
        Records.database.pdfAnnotationSaveDestinationDao().upsert(destination)
        return destination
    }

    fun clearHistory() {
        Records.database.pdfRecordDao().deleteAll()
        Records.database.userBookmarkDao().deleteAll()
        Records.database.pdfAnnotationSaveDestinationDao().deleteAll()
    }

    fun recordCount(): Int = Records.rowCount()

    fun recordOf(hash: String): PdfRecord? = Records.byHash(hash)

    fun recordHashes(): List<String> = Records.all().map { it.hash }.sorted()

    fun allBookmarks(): List<UserBookmark> = Records.database.userBookmarkDao().findAll()

    fun bookmarkCount(): Int = allBookmarks().size

    fun saveDestinationCount(): Int =
        Records.database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM PdfAnnotationSaveDestination")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    fun journalDirectory(): File = File(TestEnvironment.targetContext.filesDir, "annotations")

    fun journalFiles(): List<File> =
        journalDirectory().takeIf { it.isDirectory }?.listFiles()?.filter { it.isFile }
            ?.sortedBy { it.name }.orEmpty()

    fun journal(sourceUri: String, lines: Int = 1): File {
        val directory = journalDirectory()
        directory.mkdirs()
        val target = File(directory, "${SourceKey.of(sourceUri.toUri())}.journal")
        target.writeText((1..lines).joinToString("\n") { index -> "{\"seeded\":$index}" } + "\n")
        return target
    }

    fun signatureFile(): File =
        File(File(TestEnvironment.targetContext.filesDir, "signature"), "signature.json")

    fun signature(): File {
        val target = signatureFile()
        target.parentFile?.mkdirs()
        target.writeText("{\"seeded\":true}")
        return target
    }

    fun coverDirectory(): File = File(TestEnvironment.targetContext.cacheDir, "covers")

    fun coverFiles(): List<File> =
        coverDirectory().takeIf { it.isDirectory }?.listFiles()?.filter { it.isFile }
            ?.sortedBy { it.name }.orEmpty()

    fun cover(hash: String, widthPx: Int = 320): File {
        val directory = coverDirectory()
        directory.mkdirs()
        val target = File(directory, "$hash-w$widthPx.jpg")
        target.writeBytes(ByteArray(64))
        return target
    }

    fun settings(values: Map<String, Any>) = SettingsSeeder.seedAll(values)

    fun setting(key: String, value: Any) = SettingsSeeder.seed(key, value)

    fun removeSettings(vararg keys: String) = SettingsSeeder.remove(*keys)

    fun autoBackupEnabled(
        enabled: Boolean = true,
        hour: Int = Preferences.autoBackupHourDefault,
        minute: Int = Preferences.autoBackupMinuteDefault,
        enabledAt: Long? = System.currentTimeMillis(),
    ) {
        val values = mutableMapOf<String, Any>(
            Preferences.autoBackupEnabledKey to enabled,
            Preferences.autoBackupHourKey to hour,
            Preferences.autoBackupMinuteKey to minute,
        )
        enabledAt?.let { values[Preferences.autoBackupEnabledAtKey] = it }
        SettingsSeeder.seedAll(values)
    }

    fun autoBackupLastResult(runAt: Long, error: String?, acknowledgedRun: Long = 0L) {
        val editor = preferences.edit()
            .putLong(Preferences.autoBackupLastRunKey, runAt)
            .putLong(Preferences.autoBackupErrorAcknowledgedRunKey, acknowledgedRun)
        if (error == null) editor.remove(Preferences.autoBackupLastErrorKey)
        else editor.putString(Preferences.autoBackupLastErrorKey, error)
        editor.apply()
    }

    fun raw(key: String): Any? = preferences.all[key]

    fun contains(key: String): Boolean = preferences.contains(key)

    fun boolean(key: String): Boolean? = raw(key) as? Boolean

    fun int(key: String): Int? = raw(key) as? Int

    fun long(key: String): Long? = raw(key) as? Long

    fun float(key: String): Float? = raw(key) as? Float

    fun string(key: String): String? = raw(key) as? String

    @Suppress("UNCHECKED_CAST")
    fun stringSet(key: String): Set<String>? = raw(key) as? Set<String>

    fun importResultPending(): String? = string(Preferences.importResultPendingKey)

    fun awaitPreference(
        key: String,
        expected: Any?,
        timeoutMs: Long = Await.DEFAULT_TIMEOUT_MS,
    ): Any? = Await.untilValue(
        "preference $key reads $expected",
        timeoutMs,
        read = { raw(key) },
    ) { it == expected }
}
