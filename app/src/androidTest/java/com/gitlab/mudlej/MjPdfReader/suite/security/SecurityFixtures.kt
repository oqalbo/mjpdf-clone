// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.security

import android.net.Uri
import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

object SecurityFixtures {
    const val NORMAL = "normal.pdf"
    const val CORRUPT = "corrupt.pdf"
    const val BAD_XREF = "bad-xref.pdf"
    const val DANGLING_REF = "dangling-ref.pdf"
    const val CIRCULAR_REF = "circular-ref.pdf"
    const val DEEP_NEST = "deep-nest.pdf"
    const val BOMB = "bomb.pdf"
    const val BOMB_IMAGE = "bomb-image.pdf"
    const val HUGE_PAGECOUNT = "huge-pagecount.pdf"
    const val CYCLIC_PAGETREE = "cyclic-pagetree.pdf"
    const val CYCLIC_OUTLINE = "cyclic-outline.pdf"
    const val CYCLIC_OUTLINE_ACTIONS = "cyclic-outline-actions.pdf"
    const val BAD_FONT = "bad-font.pdf"
    const val BAD_FONT_LENGTH1 = "bad-font-length1.pdf"
    const val BAD_IMAGE = "bad-image.pdf"
    const val BAD_IMAGE_BPC = "bad-image-bpc.pdf"
    const val HUGE_PAGE = "huge-page.pdf"
    const val HUGE_PAGE_ASPECT = "huge-page-aspect.pdf"
    const val HUGE_TEXT = "huge-text.pdf"
    const val UNSUPPORTED_CRYPT = "unsupported-crypt.pdf"
    const val NOT_A_PDF = "not-a-pdf.pdf"
    const val NOT_A_PDF_ELF = "not-a-pdf-elf.pdf"
    const val NOT_A_PDF_ZIP = "not-a-pdf-zip.pdf"
    const val OPENACTION_JS = "openaction-js.pdf"
    const val OPENACTION_URI = "openaction-uri.pdf"
    const val FIELD_JS = "field-js.pdf"
    const val FIELD_JS_READONLY = "field-js-readonly.pdf"
    const val ATTACHMENT = "attachment.pdf"
    const val LAUNCH_ACTION = "launch-action.pdf"
    const val LAUNCH_ACTION_LIB = "launch-action-lib.pdf"
    const val SUBMITFORM = "submitform.pdf"
    const val LINK_SCHEMES = "link-schemes.pdf"
    const val LINK_DESTINATIONS = "link-destinations.pdf"
    const val HOSTILE_NAME = "hostile-name.pdf"
    const val HIGHLIGHTS = "highlights.pdf"

    const val TRUNCATED = "truncated.pdf"

    const val NORMAL_PAGES = 20
    const val BAD_XREF_PAGES = 12
    const val DANGLING_REF_BROKEN_PAGE_INDEX = 1
    const val LINK_DESTINATIONS_PAGES = 3
    const val HUGE_PAGECOUNT_REAL_PAGES = 1

    const val MAX_BOOKMARK_DEPTH = 64

    const val SHELL_MARKER = "mjsec18-marker"
    const val SHELL_MARKER_FILE = "/sdcard/mjsec18-marker.txt"
    const val OPENACTION_MARKER_FILE = "/sdcard/mjsec19-marker.txt"
    const val UNHANDLED_SCHEME_TOKEN = "mjsec60token"
    const val PORT_PLACEHOLDER = "00000"

    const val PAGE_WIDTH_POINTS = 612.0f
    const val PAGE_HEIGHT_POINTS = 792.0f

    val LINK_RECT_POINTS = RectPoints(72f, 587.41f, 359.36f, 604.21f)

    val LINK_SCHEME_RECTS: List<RectPoints> = listOf(
        RectPoints(72f, 587.41f, 263.44f, 604.21f),
        RectPoints(72f, 587.41f, 333.67f, 604.21f),
        RectPoints(72f, 587.41f, 313.34f, 604.21f),
        RectPoints(72f, 587.41f, 278.86f, 604.21f),
        RectPoints(72f, 587.41f, 300.6f, 604.21f),
        RectPoints(72f, 587.41f, 350.66f, 604.21f),
        RectPoints(72f, 587.41f, 260.27f, 604.21f),
        RectPoints(72f, 587.41f, 270.09f, 604.21f),
        RectPoints(72f, 587.41f, 348.62f, 604.21f),
        RectPoints(72f, 587.41f, 285.51f, 604.21f),
    )

    const val LONG_URI_PREFIX = "https://example.invalid/long?q="

    val LONG_URI: String = LONG_URI_PREFIX + "a".repeat(LONG_URI_CHARACTERS - LONG_URI_PREFIX.length)

    const val CONTROL_CHARACTER_URI = "https://example.invalid\r\n\u0000/smuggled"

    val LINK_SCHEME_URIS: List<String> = listOf(
        "file:///data/data/com.gitlab.mudlej.MjPdfReader/databases/app-db.db",
        "content://com.android.externalstorage.documents/document/primary%3A",
        "content://sms/inbox",
        "intent://scan/#Intent;scheme=zxing;end",
        "javascript:1",
        "market://details?id=$UNHANDLED_SCHEME_TOKEN",
        "tel:+10000000000",
        LONG_URI,
        CONTROL_CHARACTER_URI,
        "https://example.invalid/ok",
    )

    const val LINK_FILE_PAGE_INDEX = 0
    const val LINK_CONTENT_STORAGE_PAGE_INDEX = 1
    const val LINK_CONTENT_SMS_PAGE_INDEX = 2
    const val LINK_INTENT_PAGE_INDEX = 3
    const val LINK_JAVASCRIPT_PAGE_INDEX = 4
    const val LINK_UNHANDLED_PAGE_INDEX = 5
    const val LINK_TEL_PAGE_INDEX = 6
    const val LINK_LONG_PAGE_INDEX = 7
    const val LINK_CONTROL_CHARACTERS_PAGE_INDEX = 8
    const val LINK_CONTROL_PAGE_INDEX = 9

    const val LONG_URI_CHARACTERS = 65_536

    val LINK_DESTINATION_RECTS: List<RectPoints> = listOf(
        RectPoints(72f, 587.41f, 331.18f, 604.21f),
        RectPoints(72f, 587.41f, 440.95f, 604.21f),
        RectPoints(72f, 587.41f, 436.35f, 604.21f),
    )

    val FIELD_READONLY_RECT = RectPoints(200f, 640f, 460f, 664f)
    val FIELD_HIDDEN_RECT = RectPoints(200f, 580f, 460f, 604f)
    val SUBMIT_TEXT_RECT = RectPoints(200f, 640f, 460f, 664f)
    val SUBMIT_BUTTON_RECT = RectPoints(200f, 580f, 300f, 610f)
    val ATTACHMENT_RECT = RectPoints(80f, 600f, 104f, 624f)

    const val FIELD_JS_TAMPER_VALUE = "TAMPERED"
    const val TRUNCATION_FRACTION = 0.7

    val seededRoot: File = File(Fixtures.externalRoot, "Download")

    fun file(name: String): File = Fixtures.requireSeededFixture(name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))

    fun truncated(directory: File): File {
        val source = file(NORMAL)
        val bytes = source.readBytes()
        val kept = (bytes.size * TRUNCATION_FRACTION).toInt()
        val target = File(directory, TRUNCATED)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes.copyOfRange(0, kept))
        return target
    }

    fun structuralFixtures(): List<String> = listOf(
        BAD_XREF,
        DANGLING_REF,
        CIRCULAR_REF,
        DEEP_NEST,
        HUGE_PAGECOUNT,
        CYCLIC_PAGETREE,
        CYCLIC_OUTLINE,
        BAD_FONT,
        BAD_FONT_LENGTH1,
        BAD_IMAGE,
        BAD_IMAGE_BPC,
        HUGE_PAGE,
        HUGE_PAGE_ASPECT,
        UNSUPPORTED_CRYPT,
        NOT_A_PDF,
        NOT_A_PDF_ELF,
        NOT_A_PDF_ZIP,
    )

    fun actionFixtures(): List<String> = listOf(
        OPENACTION_JS,
        OPENACTION_URI,
        FIELD_JS,
        SUBMITFORM,
        ATTACHMENT,
        LAUNCH_ACTION,
        LINK_SCHEMES,
    )
}

data class RectPoints(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    fun centreXFraction(pageWidthPoints: Float = SecurityFixtures.PAGE_WIDTH_POINTS): Float =
        (left + right) / 2f / pageWidthPoints

    fun centreYFraction(pageHeightPoints: Float = SecurityFixtures.PAGE_HEIGHT_POINTS): Float =
        1f - (bottom + top) / 2f / pageHeightPoints
}
