// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.state

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ReaderStateParserTest {

    private val deviceSample =
        "page=0;pageCount=20;focusPage=0;loadState=Loaded;fileHash=h27b0abf28cf7332d4ddc361ed36116819;" +
            "docUri=file:///sdcard/Download/normal.pdf;state=SHOWN;render=idle;quality=full;renderPending=0;" +
            "pageGen=0;zoom=1.000;minZoom=0.500;midZoom=2.000;maxZoom=10.000;offsetX=0.000;offsetY=-0.000;" +
            "positionOffset=0.000;pageOffsetRatio=0.739;animating=false;flinging=false;snapAligned=false;" +
            "snapDelta=0.000;swipeVertical=true;pagesPerRow=1;firstPageAlone=false;pageSnap=false;" +
            "pageFling=false;freeScroll=false;fitPolicy=WIDTH;rtl=false;spacing=30;rowFirst=0;rowLast=0;" +
            "zoomLocked=false;hSwipeLocked=false;viewW=1344;viewH=2569;docLen=35350.000;maxPageW=1344.000;" +
            "maxPageH=1739.000;pageW=1344.000;pageH=1739.000;pageRect=0.000,0.000,1344.000,1739.000;" +
            "snapOffset=0.000;annotations=0;autoScrolling=false;saving=false;dirty=false;markerPages=;" +
            "dwellMs=40;hlBoundsCalls=0;groupCacheMisses=2"

    private val selectionGroup =
        ";selPage=3;selStart=120;selEnd=164;selCharCount=2048;" +
            "selRects=10.000,20.000,300.000,44.000|10.000,46.000,180.000,70.000;" +
            "selStartHandle=10.000,44.000;selEndHandle=180.000,70.000;selDragging=end"

    @Test
    @TestId("PARSE-01")
    fun theDeviceSampleConformsToTheGrammar() {
        assertEquals(emptyList<String>(), ReaderStateParser.grammarViolations(deviceSample))
    }

    @Test
    @TestId("PARSE-02")
    fun theDeviceSampleParsesIntoTypedFields() {
        val state = ReaderStateParser.parse(deviceSample)

        assertEquals(0, state.page)
        assertEquals(20, state.pageCount)
        assertEquals(DocumentLoadState.LOADED, state.loadState)
        assertEquals("h27b0abf28cf7332d4ddc361ed36116819", state.fileHash)
        assertEquals("file:///sdcard/Download/normal.pdf", state.documentUri)
        assertEquals(ViewerState.SHOWN, state.viewerState)
        assertEquals(RenderActivity.IDLE, state.render)
        assertEquals(RenderQuality.FULL, state.quality)
        assertEquals(PageFitPolicy.WIDTH, state.fitPolicy)
        assertEquals(1.0f, state.zoom!!, 0.0001f)
        assertEquals(0.739f, state.pageOffsetRatio!!, 0.0001f)
        assertEquals(false, state.animating)
        assertEquals(1, state.pagesPerRow)
        assertEquals(30, state.spacing)
        assertEquals(1344, state.viewWidth)
        assertEquals(40L, state.dwellMs)
        assertEquals(2, state.groupCacheMisses)

        val pageRect = state.pageRect!!
        assertEquals(0.0f, pageRect.left, 0.0001f)
        assertEquals(1344.0f, pageRect.right, 0.0001f)
        assertEquals(1739.0f, pageRect.bottom, 0.0001f)

        assertTrue(state.isFullyRendered)
        assertTrue(state.isSettled)
    }

    @Test
    @TestId("PARSE-03")
    fun anEmptyValueReadsAsNullRatherThanAnEmptyString() {
        val state = ReaderStateParser.parse(deviceSample)
        assertEquals(emptyList<Int>(), state.markerPages)

        val beforeIdentity = ReaderStateParser.parse(deviceSample.replace(
            "fileHash=h27b0abf28cf7332d4ddc361ed36116819",
            "fileHash=",
        ))
        assertNull(beforeIdentity.fileHash)
    }

    @Test
    @TestId("PARSE-04")
    fun theSelectionGroupIsAbsentUntilASelectionExists() {
        val withoutSelection = ReaderStateParser.parse(deviceSample)
        assertFalse(withoutSelection.hasSelection)
        assertNull(withoutSelection.selectionPage)

        val withSelection = ReaderStateParser.parse(deviceSample + selectionGroup)
        assertTrue(withSelection.hasSelection)
        assertEquals(3, withSelection.selectionPage)
        assertEquals(120, withSelection.selectionStart)
        assertEquals(164, withSelection.selectionEnd)
        assertEquals(2048, withSelection.selectionCharCount)
        assertEquals(SelectionDrag.END, withSelection.selectionDrag)

        val rects = withSelection.selectionRects!!
        assertEquals(2, rects.size)
        assertEquals(300.0f, rects[0].right, 0.0001f)
        assertEquals(46.0f, rects[1].top, 0.0001f)

        assertEquals(10.0f, withSelection.selectionStartHandle!!.x, 0.0001f)
        assertEquals(70.0f, withSelection.selectionEndHandle!!.y, 0.0001f)
    }

    @Test
    @TestId("PARSE-05")
    fun aStateCarryingASelectionAlsoConformsToTheGrammar() {
        assertEquals(emptyList<String>(), ReaderStateParser.grammarViolations(deviceSample + selectionGroup))
    }

    @Test
    @TestId("PARSE-06")
    fun escapedSeparatorsAreDecodedInStringValues() {
        val state = ReaderStateParser.parse(
            deviceSample.replace(
                "docUri=file:///sdcard/Download/normal.pdf",
                "docUri=content://x/a%3Bb%3Dc%25d",
            )
        )
        assertEquals("content://x/a;b=c%d", state.documentUri)
    }

    @Test
    @TestId("PARSE-07")
    fun anEscapedPercentIsDecodedLastSoALiteralEscapeSurvives() {
        assertEquals("%3B", ReaderStateParser.unescape("%253B"))
        assertEquals("%3D", ReaderStateParser.unescape("%253D"))
        assertEquals("%25", ReaderStateParser.unescape("%2525"))
        assertEquals(";=%", ReaderStateParser.unescape("%3B%3D%25"))
    }

    @Test
    @TestId("PARSE-08")
    fun escapingIsTheInverseOfUnescaping() {
        listOf("a;b", "a=b", "a%b", "%3B", "%25", "content://x/y?q=1;z=2", "plain").forEach { original ->
            assertEquals(original, ReaderStateParser.unescape(ReaderStateParser.escape(original)))
        }
    }

    @Test
    @TestId("PARSE-09")
    fun anUnescapedPercentIsReportedAsAGrammarViolation() {
        val violations = ReaderStateParser.grammarViolations(
            deviceSample.replace(
                "docUri=file:///sdcard/Download/normal.pdf",
                "docUri=file:///sdcard/Download/100%done.pdf",
            )
        )
        assertEquals(1, violations.size)
        assertTrue(violations.first(), violations.first().contains("unescaped percent"))
    }

    @Test
    @TestId("PARSE-10")
    fun aPartialSelectionGroupIsReportedAsAGrammarViolation() {
        val violations = ReaderStateParser.grammarViolations("$deviceSample;selPage=3;selStart=120")
        assertTrue(violations.toString(), violations.any { it.contains("selection group is partially present") })
    }

    @Test
    @TestId("PARSE-11")
    fun malformedStatesAreRejectedRatherThanGuessedAt() {
        assertRejected("$deviceSample;")
        assertRejected("$deviceSample;page=1")
        assertRejected("page=0;pageCount")
        assertRejected("page=0;=5")
    }

    @Test
    @TestId("PARSE-12")
    fun aMissingFieldIsReportedRatherThanSilentlyDefaulted() {
        val violations = ReaderStateParser.grammarViolations(deviceSample.replace(";markerPages=", ""))
        assertTrue(violations.toString(), violations.any { it.contains("markerPages is missing") })
    }

    private fun assertRejected(raw: String) {
        val rejected = try {
            ReaderStateParser.parse(raw)
            false
        } catch (error: ReaderStateFormatException) {
            true
        }
        assertTrue("Expected the parser to reject: $raw", rejected)
    }
}
