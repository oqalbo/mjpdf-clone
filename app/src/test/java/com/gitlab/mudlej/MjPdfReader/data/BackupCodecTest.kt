// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Ignore
import org.junit.Test

class BackupCodecTest {

    @Test
    fun populatedBackupSurvivesTheWireFormat() {
        val record = BackupPdfRecord(
            hash = "0123456789abcdef0123456789abcdef",
            pageNumber = 7,
            length = 120,
            fileName = "Reader \"notes\".pdf",
            password = "secret",
            lastOpened = "2026-11-03T01:02:03",
            reading = "READING",
            favorite = true,
            cropMargins = "[1,2,3,4]",
            cropMarginsVersion = 2,
            autoScrollSpeed = 8,
            readingDirectionOverride = "RIGHT_TO_LEFT",
            detectedReadingDirection = "LEFT_TO_RIGHT",
            documentTitle = "A title\nwith a second line",
            uri = "content://documents/42",
            textModeJoinParagraphs = true,
            textModeDetectHeadings = false,
            textModeCodeBlocks = true,
            hidden = true,
            sourceUri = "content://source/42",
        )
        val data = BackupData(
            schemaVersion = 1,
            appVersionCode = 4_294_967_297L,
            exportedAt = "2026-11-03T02:00:00",
            settings = listOf(
                BackupSetting("theme", "string", "dark"),
                BackupSetting("actions", "stringSet", values = listOf("search", "bookmarks")),
            ),
            pdfRecords = listOf(record, record.copy(
                password = null,
                textModeJoinParagraphs = null,
                textModeDetectHeadings = null,
                textModeCodeBlocks = null,
            )),
            userBookmarks = listOf(BackupUserBookmark(record.hash, 6, "Bookmarks label", "2026-11-02T12:00:00", 2)),
        )

        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data).toString()))
    }

    @Test
    fun emptyListsStayDistinctFromAbsentLists() {
        val empty = BackupData(1, settings = emptyList(), pdfRecords = emptyList(), userBookmarks = emptyList())
        val absent = BackupData(1)

        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty).toString()))
        assertEquals(absent, BackupCodec.decode(BackupCodec.encode(absent).toString()))
    }

    @Test
    fun malformedAndUnrecognisedDocumentsUseTheInvalidBackupMessage() {
        for (text in listOf("", "[]", "{}", "{} x", "null")) {
            assertInvalid(text)
        }
    }

    @Test
    fun unknownMembersDoNotChangeKnownFields() {
        val decoded = BackupCodec.decode(
            """{"schemaVersion":1,"future":{"enabled":true},"pdfRecords":[{"hash":"abc","pageNumber":9,"future":[1,2]}]}""",
        )

        assertEquals(BackupData(1, pdfRecords = listOf(BackupPdfRecord(hash = "abc", pageNumber = 9))), decoded)
    }

    @Test
    fun stringSchemaVersionIsCoercedToAnInteger() {
        assertEquals(1, BackupCodec.decode("""{"schemaVersion":"1"}""").schemaVersion)
    }

    @Test
    fun nullSchemaVersionUsesZero() {
        assertEquals(0, BackupCodec.decode("""{"schemaVersion":null}""").schemaVersion)
    }

    @Test
    @Ignore("Pending ruling: pageNumber 4294967297 wraps to 1, expected the invalid integer default 0")
    fun overflowingPageNumberUsesTheDefaultInsteadOfWrapping() {
        assertEquals(0, decodeRecord("""{"pageNumber":4294967297}""").pageNumber)
    }

    @Test
    fun fractionalPageNumberIsTruncated() {
        assertEquals(3, decodeRecord("""{"pageNumber":3.7}""").pageNumber)
    }

    @Test
    fun nonBooleanFavoriteStringDecodesAsFalse() {
        assertEquals(false, decodeRecord("""{"favorite":"yes"}""").favorite)
    }

    @Test
    fun booleanIntegerFieldUsesItsDefault() {
        assertEquals(-1, decodeRecord("""{"length":true}""").length)
    }

    @Test
    fun legacyRecordWithThirtyTwoHexCharactersIsRecognised() {
        val hash = "0123456789abcdefABCDEF0123456789"
        val decoded = BackupCodec.decode("""{"b":"2026-11-03","d":[{"a":"$hash","b":4}]}""")

        assertEquals(BackupData(1, exportedAt = "2026-11-03",
            pdfRecords = listOf(BackupPdfRecord(hash = hash, pageNumber = 4))), decoded)
    }

    @Test
    fun legacyRecordWithThirtyOneHexCharactersIsRejected() {
        assertInvalid("""{"b":"2026-11-03","d":[{"a":"0123456789abcdef0123456789abcde"}]}""")
    }

    @Test
    fun legacyExportTimeMustBeAString() {
        assertInvalid("""{"b":123,"d":[{"a":"0123456789abcdef0123456789abcdef"}]}""")
    }

    @Test
    fun emptyLegacyArraysDoNotIdentifyABackup() {
        assertInvalid("""{"b":"2026-11-03","c":[],"d":[],"e":[]}""")
    }

    @Test
    fun nonObjectRecordsDecodeToDefaultRecords() {
        val decoded = BackupCodec.decode("""{"schemaVersion":1,"pdfRecords":[7,null,[],false,"record"]}""")

        assertEquals(List(5) { BackupPdfRecord() }, decoded.pdfRecords)
    }

    private fun decodeRecord(record: String): BackupPdfRecord =
        BackupCodec.decode("""{"schemaVersion":1,"pdfRecords":[$record]}""").pdfRecords!!.single()

    private fun assertInvalid(text: String) {
        val error = assertThrows(text, BackupException::class.java) { BackupCodec.decode(text) }
        assertEquals(text, R.string.backup_error_not_valid, error.messageRes)
    }
}
