// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.lists

import com.gitlab.mudlej.MjPdfReader.suite.fixtures.Fixtures
import java.io.File

data class RichGroup(
    val key: String,
    val pageIndex: Int,
    val color: Int,
    val quote: String,
    val note: String?,
    val createdAt: String?,
) {
    val pagePill: String get() = "Page ${pageIndex + 1}"
    val rowQuote: String get() = "“${quote.trim()}”"
    val rowDate: String? get() = createdAt?.let { ListsFixtures.readableDate(it) }
}

object ListsFixtures {

    const val RICH = "highlights-rich.pdf"
    const val RICH_MIXED_DATES = "highlights-rich-mixed-dates.pdf"
    const val PLAIN = "highlights-plain.pdf"
    const val NONE = "highlights-none.pdf"
    const val ARABIC = "highlights-arabic.pdf"
    const val HUGE = "highlights-huge.pdf"
    const val THIRDPARTY = "highlights-thirdparty.pdf"
    const val SCANNED = "highlights-scanned.pdf"
    const val LOCKED = "highlights-locked.pdf"

    const val LOCKED_PASSWORD = "letmein"

    const val YELLOW = 0xFFFFF176.toInt()
    const val BLUE = 0xFF64B5F6.toInt()
    const val GREEN = 0xFF81C784.toInt()
    const val NON_PALETTE = 0xFF123456.toInt()
    const val NON_PALETTE_CHIP = "#123456"
    const val THIRDPARTY_COLOR = 0xFF00FF00.toInt()

    const val RICH_TITLE = "The Memory Model"
    const val RICH_AUTHOR = "A. Writer"
    const val RICH_ANNOTATIONS = 13

    const val HUGE_PAGES = 600
    const val HUGE_GROUPS = 400

    const val PART_ONE = "Part One"
    const val PART_ONE_ALLOCATION = "Part One ▶ Allocation"
    const val PART_TWO_COLLECTION = "Part Two ▶ Collection"

    const val SCANNED_NOTE = "check this figure"
    const val SCANNED_TITLE = "Scanned Figure"

    val richGroups: List<RichGroup> = listOf(
        RichGroup(
            "mj-group-01", 0, YELLOW,
            "A ledger of every allocation is the only honest map of a heap.",
            "The ledger idea returns in chapter four.",
            "D:20240310090000",
        ),
        RichGroup(
            "mj-group-02", 0, BLUE,
            "An arena grows forward and forgets nothing until the cadence ends.",
            null, "D:20240115143000",
        ),
        RichGroup(
            "mj-group-03", 2, GREEN,
            "Compaction moves the living objects together so that the gaps\n" +
                "between them close, and the address of every survivor changes at\n" +
                "once, which is why a pointer held across a move is a ruin.",
            null, "D:20240522101500",
        ),
        RichGroup(
            "mj-group-04", 2, YELLOW,
            "A quarantine page is never reused while a live pointer may name it.",
            null, "D:20240103081000",
        ),
        RichGroup(
            "mj-group-05", 5, YELLOW,
            "The collector opens with a handshake and closes with a cadence.",
            null, "D:20240418173000",
        ),
        RichGroup(
            "mj-group-06", 5, BLUE,
            "A tombstone marks the place where an object used to be, and it\n" +
                "stays readable long after the object is gone so that a stale\n" +
                "reference is reported instead of quietly reading a stranger.",
            "Check this marginalia against the second edition.",
            "D:20240207120000",
        ),
        RichGroup(
            "mj-group-07", 8, NON_PALETTE,
            "Sediment gathers at the bottom of a long lived heap and stays.",
            null, "D:20240630200000",
        ),
        RichGroup(
            "mj-group-08", 9, GREEN,
            "A lantern pass lights only the objects the roots can still reach.",
            "The lantern metaphor is borrowed from the tracing literature.",
            "D:20240711064500",
        ),
        RichGroup(
            "mj-group-09", 9, YELLOW,
            "The harbour of the old generation is quiet until the tide turns.",
            null, "D:20240915215900",
        ),
    )

    val richNotedGroups: List<RichGroup> get() = richGroups.filter { it.note != null }

    val richPagePills: List<String> get() = richGroups.map { it.pagePill }

    val richMultiLineGroups: List<RichGroup>
        get() = richGroups.filter { it.key == "mj-group-03" || it.key == "mj-group-06" }

    val newestGroup: RichGroup get() = richGroups.single { it.key == "mj-group-09" }

    val nonPaletteGroup: RichGroup get() = richGroups.single { it.key == "mj-group-07" }

    val lowOnPageSixGroup: RichGroup get() = richGroups.single { it.key == "mj-group-06" }

    val arabicQuotes: List<String> = listOf(
        "القراءة رحلة هادئة " +
            "بين الصفحة والعين",
        "الذاكرة وعد تقطعه " +
            "الآلة على نفسها",
        "كل فقرة تقدم شكلا " +
            "والقارئ يقبله أو يرفضه",
    )

    val arabicNote: String =
        "راجع هذه الفكرة " +
            "في الفصل الرابع"

    const val WORD_IN_ONE_QUOTE_ON_PAGE_SIX = "handshake"
    const val WORD_IN_ONE_NOTE_ONLY = "marginalia"
    const val WORD_IN_A_YELLOW_AND_A_BLUE_QUOTE = "cadence"

    const val PAGE_WIDTH_POINTS = 612f
    const val PAGE_HEIGHT_POINTS = 792f

    private const val BODY_LEFT = 72f
    private const val BODY_RIGHT = 540f
    private const val BODY_TOP = 700f
    private const val BODY_LEADING = 16f
    private const val ASCENT_ABOVE_BASELINE = 12.21f
    private const val DESCENT_BELOW_BASELINE = 4.59f

    val lowOnPageSixLines = listOf(21, 22, 23)

    fun bodyLineBaseline(lineIndex: Int): Float = BODY_TOP - BODY_LEADING * lineIndex

    fun lowOnPageSixPdfBounds(): android.graphics.RectF = android.graphics.RectF(
        BODY_LEFT,
        bodyLineBaseline(lowOnPageSixLines.last()) - DESCENT_BELOW_BASELINE,
        BODY_RIGHT,
        bodyLineBaseline(lowOnPageSixLines.first()) + ASCENT_ABOVE_BASELINE,
    )

    fun readableDate(pdfDate: String): String =
        com.gitlab.mudlej.MjPdfReader.core.io.convertDateString(pdfDate)
            ?: throw AssertionError("The fixture date $pdfDate does not parse, so no row text can be predicted")

    fun place(name: String, targetDirectory: File): File =
        Fixtures.copyDeviceFixture(name, targetDirectory)

    fun placeAs(name: String, targetDirectory: File, newName: String): File {
        val placed = Fixtures.copyDeviceFixture(name, targetDirectory)
        val renamed = File(targetDirectory, newName)
        if (placed.name == newName) return placed
        if (!placed.renameTo(renamed)) {
            throw AssertionError("The fixture copy ${placed.absolutePath} could not be renamed to $newName")
        }
        Fixtures.scanAndWait(renamed)
        return renamed
    }
}
