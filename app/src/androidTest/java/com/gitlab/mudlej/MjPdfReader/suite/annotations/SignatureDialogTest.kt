// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Snackbars
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SignatureDialogTest : AnnotationTestCase() {

    @Test
    @TestId("ANN-48")
    @TestDuration(DurationCategory.SHORT)
    fun placeStaysDisabledUntilThereIsInk() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            assertFalse(
                "a clean install must carry no stored signature",
                SignaturePad.storedSignatureFile().exists(),
            )

            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()

            SignaturePad.awaitPlaceEnabled(false)
            assertFalse(
                "an empty pad must not write a signature file",
                SignaturePad.storedSignatureFile().exists(),
            )
        }
    }

    @Test
    @TestId("ANN-49")
    fun drawingEnablesPlaceAndPlacingStoresTheSignature() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            val journal = journal()

            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()
            SignaturePad.awaitPlaceEnabled(false)

            SignaturePad.drawOneStroke()
            SignaturePad.awaitPlaceEnabled(true)

            SignaturePad.pressPlace()

            val stored = SignaturePad.storedSignatureFile()
            Await.untilFileExists(stored)
            val json = JSONObject(stored.readText())
            assertTrue("the stored signature carries strokes", json.getJSONArray("strokes").length() > 0)
            assertTrue("the stored aspect must be positive", json.getDouble("aspect") > 0.0)
            assertTrue("the stored stroke width must be positive", json.getDouble("strokeWidth") > 0.0)
            assertEquals("the default ink colour is stored", SignaturePad.INK_BLACK, json.getInt("color"))

            Snackbars.awaitText(string(R.string.signature_pending_hint))

            val stamp = AnnotationDump.awaitPendingStamp()
            assertEquals("the stamp is placed on the page in view", 0, stamp.page)
            val pageWidth = AnnotationGeometry.pagePointSize(0).x
            assertEquals(
                "the stamp opens at four tenths of the page width",
                pageWidth * PAGE_WIDTH_FRACTION,
                stamp.rect.width,
                pageWidth * WIDTH_TOLERANCE_FRACTION,
            )

            AnnotationReaderUi.awaitSaveFabVisible()
            AnnotationJournalFile.assertStaysAbsent(journal)
        }
    }

    @Test
    @TestId("ANN-50")
    fun clearEmptiesThePadAndDisablesPlace() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()

            SignaturePad.drawOneStroke()
            SignaturePad.awaitPlaceEnabled(true)

            SignaturePad.pressClear()

            SignaturePad.awaitPlaceEnabled(false)
            assertTrue("clearing must not close the dialog", SignaturePad.isDialogShown())
            assertFalse(
                "a cleared pad must not write a signature file",
                SignaturePad.storedSignatureFile().exists(),
            )
        }
    }

    @Test
    @TestId("ANN-51")
    fun theStoredSignatureIsPreloadedNextTime() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()
            SignaturePad.drawOneStroke()
            SignaturePad.awaitPlaceEnabled(true)
            SignaturePad.pressPlace()

            val stored = SignaturePad.storedSignatureFile()
            Await.untilFileExists(stored)
            val afterPlacement = stored.readBytes()

            AnnotationDump.awaitPendingStamp()
            StampRobot(selection).tapDiscardBadge(frame())
            AnnotationDump.awaitNoPendingStamp()

            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()

            assertTrue(
                "a stored signature must arm Place without any drawing",
                SignaturePad.isPlaceEnabled(),
            )
            assertTrue(
                "reopening the dialog must not rewrite the stored signature",
                afterPlacement.contentEquals(stored.readBytes()),
            )
        }
    }

    @Test
    @TestId("ANN-52")
    fun theChosenInkColourIsStored() {
        openReader(AnnotationFixtures.NORMAL) { _, _ ->
            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()

            SignaturePad.pickInk(R.string.signature_color_blue)
            SignaturePad.drawOneStroke()
            SignaturePad.awaitPlaceEnabled(true)
            SignaturePad.pressPlace()

            val stored = SignaturePad.storedSignatureFile()
            Await.untilFileExists(stored)
            assertEquals(
                "the stored signature carries the chosen ink colour",
                SignaturePad.INK_BLUE,
                JSONObject(stored.readText()).getInt("color"),
            )
            assertEquals(
                "the pending stamp carries the chosen ink colour",
                SignaturePad.INK_BLUE,
                AnnotationDump.awaitPendingStamp().color,
            )
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val PAGE_WIDTH_FRACTION = 0.4f
        const val WIDTH_TOLERANCE_FRACTION = 0.02f
    }
}
