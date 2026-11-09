// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.annotations

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.annotationsave.SaveDigest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class SignatureCommitTest : AnnotationTestCase() {

    private val stamp = StampRobot()

    @Test
    @TestId("ANN-60")
    fun committingAPendingStampRecordsAnAddSignatureEditBeforeTheDestinationIsChosen() {
        openReader(AnnotationFixtures.NORMAL) { _, file ->
            val journal = journal()
            val bytesBefore = file.length()
            val digestBefore = SaveDigest.of(file)

            SignaturePad.openFromReaderActions()
            SignaturePad.awaitDialog()
            SignaturePad.drawOneStroke()
            SignaturePad.awaitPlaceEnabled(true)
            SignaturePad.pressPlace()

            val placed = AnnotationDump.awaitPendingStamp().rect
            val page = frame()
            stamp.dragCentreBy(page, -page.viewBounds.width() * MOVE_FRACTION, 0f)
            val positioned = stamp.awaitStampSettled(placed)
            val storedSignature = JSONObject(SignaturePad.storedSignatureFile().readText())

            AnnotationReaderUi.tapSaveFab()

            AnnotationDialogs.awaitTitle(string(R.string.save_highlights), Await.LONG_TIMEOUT_MS)
            val line = AnnotationJournalFile.awaitLineCount(journal, 1).single()

            TestEnvironment.uiDevice.pressBack()
            AnnotationDialogs.awaitTitleGone(string(R.string.save_highlights))

            assertEquals("the journalled operation", AnnotationJournalFile.OP_ADD_SIGNATURE, line.op)
            assertEquals("the journalled page index", 0, line.page)
            assertEquals("the journalled rectangle carries four values", 4, line.rect.size)
            assertEquals("the journalled left edge", positioned.left, line.rect[0], ONE_PDF_UNIT)
            assertEquals("the journalled top edge", positioned.top, line.rect[1], ONE_PDF_UNIT)
            assertEquals("the journalled right edge", positioned.right, line.rect[2], ONE_PDF_UNIT)
            assertEquals("the journalled bottom edge", positioned.bottom, line.rect[3], ONE_PDF_UNIT)
            assertEquals(
                "the journalled stroke count matches the stored signature",
                storedSignature.getJSONArray("strokes").length(),
                line.strokes.size,
            )
            assertEquals(
                "the journalled colour matches the stored signature",
                storedSignature.getInt("color"),
                line.color,
            )
            assertTrue("the journalled stroke width must be positive", line.strokeWidth!! > 0f)

            AnnotationDump.awaitNoPendingStamp()

            assertEquals("dismissing the chooser must not write the document", bytesBefore, file.length())
            assertEquals("dismissing the chooser must not write the document", digestBefore, SaveDigest.of(file))
        }
    }

    private fun string(resId: Int): String = TestEnvironment.targetContext.getString(resId)

    private companion object {
        const val ONE_PDF_UNIT = 1f
        const val MOVE_FRACTION = 0.12f
    }
}
