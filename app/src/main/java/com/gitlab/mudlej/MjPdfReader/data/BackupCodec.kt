// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.data

import com.gitlab.mudlej.MjPdfReader.R
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object BackupCodec {

    fun encode(data: BackupData): JsonObject {
        val root = JsonObject()
        root.addProperty(SCHEMA_VERSION_MEMBER, data.schemaVersion)
        data.appVersionCode?.let { root.addProperty(canonical.appVersionCode, it) }
        data.exportedAt?.let { root.addProperty(canonical.exportedAt, it) }
        data.settings?.let { root.add(canonical.settings, encodeList(it, ::encodeSetting)) }
        data.pdfRecords?.let { root.add(canonical.pdfRecords, encodeList(it, ::encodeRecord)) }
        data.userBookmarks?.let { root.add(canonical.userBookmarks, encodeList(it, ::encodeBookmark)) }
        return root
    }

    fun decode(json: String): BackupData {
        val root = try {
            JsonParser.parseString(json)
        } catch (exception: Exception) {
            throw BackupException(R.string.backup_error_not_valid)
        }
        if (!root.isJsonObject) {
            throw BackupException(R.string.backup_error_not_valid)
        }
        val rootObject = root.asJsonObject
        return when {
            rootObject.has(SCHEMA_VERSION_MEMBER) ->
                decodeData(rootObject, canonical, rootObject.intMember(SCHEMA_VERSION_MEMBER, 0))
            isLegacyMinified(rootObject) ->
                decodeData(rootObject, legacyMinified, LEGACY_SCHEMA_VERSION)
            else -> throw BackupException(R.string.backup_error_not_valid)
        }
    }

    private fun isLegacyMinified(root: JsonObject): Boolean {
        val exportedAt = root.get(legacyMinified.exportedAt) ?: return false
        if (!exportedAt.isJsonPrimitive || !exportedAt.asJsonPrimitive.isString) {
            return false
        }
        return looksLikeLegacySettings(root.arrayMember(legacyMinified.settings)) ||
            looksLikeLegacyRecords(root.arrayMember(legacyMinified.pdfRecords)) ||
            looksLikeLegacyBookmarks(root.arrayMember(legacyMinified.userBookmarks))
    }

    private fun looksLikeLegacySettings(array: JsonArray?): Boolean {
        val first = array?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val kind = first.stringMember(legacyMinified.settingType) ?: return false
        return kind in legacySettingKinds
    }

    private fun looksLikeLegacyRecords(array: JsonArray?): Boolean {
        val first = array?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val hash = first.stringMember(legacyMinified.recordHash) ?: return false
        return legacyHashRegex.matches(hash)
    }

    private fun looksLikeLegacyBookmarks(array: JsonArray?): Boolean {
        val first = array?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        return first.stringMember(legacyMinified.bookmarkFileHash) != null &&
            first.nullableIntMember(legacyMinified.bookmarkPageIndex) != null
    }

    private fun decodeData(root: JsonObject, names: MemberNames, schemaVersion: Int): BackupData {
        return BackupData(
            schemaVersion = schemaVersion,
            appVersionCode = root.longMember(names.appVersionCode),
            exportedAt = root.stringMember(names.exportedAt),
            settings = root.arrayMember(names.settings)?.map { decodeSetting(it, names) },
            pdfRecords = root.arrayMember(names.pdfRecords)?.map { decodeRecord(it, names) },
            userBookmarks = root.arrayMember(names.userBookmarks)?.map { decodeBookmark(it, names) },
        )
    }

    private fun encodeSetting(setting: BackupSetting): JsonObject {
        val json = JsonObject()
        setting.key?.let { json.addProperty(canonical.settingKey, it) }
        setting.type?.let { json.addProperty(canonical.settingType, it) }
        setting.value?.let { json.addProperty(canonical.settingValue, it) }
        setting.values?.let { values ->
            val array = JsonArray()
            values.forEach { array.add(it) }
            json.add(canonical.settingValues, array)
        }
        return json
    }

    private fun decodeSetting(element: JsonElement, names: MemberNames): BackupSetting {
        val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: return BackupSetting()
        return BackupSetting(
            key = json.stringMember(names.settingKey),
            type = json.stringMember(names.settingType),
            value = json.stringMember(names.settingValue),
            values = json.stringListMember(names.settingValues),
        )
    }

    private fun encodeRecord(record: BackupPdfRecord): JsonObject {
        val json = JsonObject()
        record.hash?.let { json.addProperty(canonical.recordHash, it) }
        json.addProperty(canonical.recordPageNumber, record.pageNumber)
        json.addProperty(canonical.recordLength, record.length)
        record.fileName?.let { json.addProperty(canonical.recordFileName, it) }
        record.password?.let { json.addProperty(canonical.recordPassword, it) }
        record.lastOpened?.let { json.addProperty(canonical.recordLastOpened, it) }
        record.reading?.let { json.addProperty(canonical.recordReading, it) }
        json.addProperty(canonical.recordFavorite, record.favorite)
        record.cropMargins?.let { json.addProperty(canonical.recordCropMargins, it) }
        json.addProperty(canonical.recordCropMarginsVersion, record.cropMarginsVersion)
        record.autoScrollSpeed?.let { json.addProperty(canonical.recordAutoScrollSpeed, it) }
        record.readingDirectionOverride?.let { json.addProperty(canonical.recordReadingDirectionOverride, it) }
        record.detectedReadingDirection?.let { json.addProperty(canonical.recordDetectedReadingDirection, it) }
        record.documentTitle?.let { json.addProperty(canonical.recordDocumentTitle, it) }
        record.uri?.let { json.addProperty(canonical.recordUri, it) }
        record.textModeJoinParagraphs?.let { json.addProperty(canonical.recordTextModeJoinParagraphs, it) }
        record.textModeDetectHeadings?.let { json.addProperty(canonical.recordTextModeDetectHeadings, it) }
        record.textModeCodeBlocks?.let { json.addProperty(canonical.recordTextModeCodeBlocks, it) }
        json.addProperty(canonical.recordHidden, record.hidden)
        record.sourceUri?.let { json.addProperty(canonical.recordSourceUri, it) }
        return json
    }

    private fun decodeRecord(element: JsonElement, names: MemberNames): BackupPdfRecord {
        val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: return BackupPdfRecord()
        return BackupPdfRecord(
            hash = json.stringMember(names.recordHash),
            pageNumber = json.intMember(names.recordPageNumber, 0),
            length = json.intMember(names.recordLength, -1),
            fileName = json.stringMember(names.recordFileName),
            password = json.stringMember(names.recordPassword),
            lastOpened = json.stringMember(names.recordLastOpened),
            reading = json.stringMember(names.recordReading),
            favorite = json.booleanMember(names.recordFavorite, false),
            cropMargins = json.stringMember(names.recordCropMargins),
            cropMarginsVersion = json.intMember(names.recordCropMarginsVersion, 0),
            autoScrollSpeed = json.nullableIntMember(names.recordAutoScrollSpeed),
            readingDirectionOverride = json.stringMember(names.recordReadingDirectionOverride),
            detectedReadingDirection = json.stringMember(names.recordDetectedReadingDirection),
            documentTitle = json.stringMember(names.recordDocumentTitle),
            uri = json.stringMember(names.recordUri),
            textModeJoinParagraphs = json.nullableBooleanMember(names.recordTextModeJoinParagraphs),
            textModeDetectHeadings = json.nullableBooleanMember(names.recordTextModeDetectHeadings),
            textModeCodeBlocks = json.nullableBooleanMember(names.recordTextModeCodeBlocks),
            hidden = json.booleanMember(names.recordHidden, false),
            sourceUri = json.stringMember(names.recordSourceUri),
        )
    }

    private fun encodeBookmark(bookmark: BackupUserBookmark): JsonObject {
        val json = JsonObject()
        bookmark.fileHash?.let { json.addProperty(canonical.bookmarkFileHash, it) }
        json.addProperty(canonical.bookmarkPageIndex, bookmark.pageIndex)
        bookmark.label?.let { json.addProperty(canonical.bookmarkLabel, it) }
        bookmark.createdAt?.let { json.addProperty(canonical.bookmarkCreatedAt, it) }
        json.addProperty(canonical.bookmarkSortOrder, bookmark.sortOrder)
        return json
    }

    private fun decodeBookmark(element: JsonElement, names: MemberNames): BackupUserBookmark {
        val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: return BackupUserBookmark()
        return BackupUserBookmark(
            fileHash = json.stringMember(names.bookmarkFileHash),
            pageIndex = json.intMember(names.bookmarkPageIndex, -1),
            label = json.stringMember(names.bookmarkLabel),
            createdAt = json.stringMember(names.bookmarkCreatedAt),
            sortOrder = json.intMember(names.bookmarkSortOrder, -1),
        )
    }

    private fun <T> encodeList(items: List<T>, transform: (T) -> JsonObject): JsonArray {
        val array = JsonArray()
        items.forEach { array.add(transform(it)) }
        return array
    }

    private fun JsonObject.member(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }

    private fun JsonObject.stringMember(name: String): String? =
        member(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

    private fun JsonObject.stringListMember(name: String): List<String>? =
        arrayMember(name)?.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }
        }

    private fun JsonObject.intMember(name: String, default: Int): Int =
        nullableIntMember(name) ?: default

    private fun JsonObject.nullableIntMember(name: String): Int? =
        member(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.longMember(name: String): Long? =
        member(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.booleanMember(name: String, default: Boolean): Boolean =
        nullableBooleanMember(name) ?: default

    private fun JsonObject.nullableBooleanMember(name: String): Boolean? =
        member(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }

    private fun JsonObject.arrayMember(name: String): JsonArray? =
        member(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private class MemberNames(
        val appVersionCode: String,
        val exportedAt: String,
        val settings: String,
        val pdfRecords: String,
        val userBookmarks: String,
        val settingKey: String,
        val settingType: String,
        val settingValue: String,
        val settingValues: String,
        val recordHash: String,
        val recordPageNumber: String,
        val recordLength: String,
        val recordFileName: String,
        val recordPassword: String,
        val recordLastOpened: String,
        val recordReading: String,
        val recordFavorite: String,
        val recordCropMargins: String,
        val recordCropMarginsVersion: String,
        val recordAutoScrollSpeed: String,
        val recordReadingDirectionOverride: String,
        val recordDetectedReadingDirection: String,
        val recordDocumentTitle: String,
        val recordUri: String,
        val recordTextModeJoinParagraphs: String,
        val recordTextModeDetectHeadings: String,
        val recordTextModeCodeBlocks: String,
        val recordHidden: String,
        val recordSourceUri: String,
        val bookmarkFileHash: String,
        val bookmarkPageIndex: String,
        val bookmarkLabel: String,
        val bookmarkCreatedAt: String,
        val bookmarkSortOrder: String,
    )

    private const val SCHEMA_VERSION_MEMBER = "schemaVersion"
    private const val LEGACY_SCHEMA_VERSION = 1

    private val legacySettingKinds = setOf(
        Preferences.kindBoolean,
        Preferences.kindInt,
        Preferences.kindLong,
        Preferences.kindFloat,
        Preferences.kindString,
        Preferences.kindStringSet,
    )
    private val legacyHashRegex = Regex("[0-9a-fA-F]{32,}")

    private val canonical = MemberNames(
        appVersionCode = "appVersionCode",
        exportedAt = "exportedAt",
        settings = "settings",
        pdfRecords = "pdfRecords",
        userBookmarks = "userBookmarks",
        settingKey = "key",
        settingType = "type",
        settingValue = "value",
        settingValues = "values",
        recordHash = "hash",
        recordPageNumber = "pageNumber",
        recordLength = "length",
        recordFileName = "fileName",
        recordPassword = "password",
        recordLastOpened = "lastOpened",
        recordReading = "reading",
        recordFavorite = "favorite",
        recordCropMargins = "cropMargins",
        recordCropMarginsVersion = "cropMarginsVersion",
        recordAutoScrollSpeed = "autoScrollSpeed",
        recordReadingDirectionOverride = "readingDirectionOverride",
        recordDetectedReadingDirection = "detectedReadingDirection",
        recordDocumentTitle = "documentTitle",
        recordUri = "uri",
        recordTextModeJoinParagraphs = "textModeJoinParagraphs",
        recordTextModeDetectHeadings = "textModeDetectHeadings",
        recordTextModeCodeBlocks = "textModeCodeBlocks",
        recordHidden = "hidden",
        recordSourceUri = "sourceUri",
        bookmarkFileHash = "fileHash",
        bookmarkPageIndex = "pageIndex",
        bookmarkLabel = "label",
        bookmarkCreatedAt = "createdAt",
        bookmarkSortOrder = "sortOrder",
    )

    private val legacyMinified = MemberNames(
        appVersionCode = "a",
        exportedAt = "b",
        settings = "c",
        pdfRecords = "d",
        userBookmarks = "e",
        settingKey = "a",
        settingType = "b",
        settingValue = "c",
        settingValues = "d",
        recordHash = "a",
        recordPageNumber = "b",
        recordLength = "c",
        recordFileName = "d",
        recordPassword = "e",
        recordLastOpened = "f",
        recordReading = "g",
        recordFavorite = "h",
        recordCropMargins = "i",
        recordCropMarginsVersion = "j",
        recordAutoScrollSpeed = "k",
        recordReadingDirectionOverride = "l",
        recordDetectedReadingDirection = "m",
        recordDocumentTitle = "n",
        recordUri = "o",
        recordTextModeJoinParagraphs = "p",
        recordTextModeDetectHeadings = "q",
        recordTextModeCodeBlocks = "r",
        recordHidden = "s",
        recordSourceUri = "t",
        bookmarkFileHash = "a",
        bookmarkPageIndex = "b",
        bookmarkLabel = "c",
        bookmarkCreatedAt = "d",
        bookmarkSortOrder = "e",
    )
}
