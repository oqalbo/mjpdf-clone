// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class OrientationBaselineTest : BaselineTestCase() {

    @Test
    @TestId("VIS-52")
    fun landscapeRecomputesTheFitWidthScaleForTheWiderViewport() {
        openGoldenBookPage()
        rotateToLandscape()
        awaitLandscapeLayout()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        val settled = state()
        val width = settled.viewWidth ?: throw AssertionError("The hook reports no canvas width")
        val height = settled.viewHeight ?: throw AssertionError("The hook reports no canvas height")
        assertTrue(
            "the reader must be laid out landscape before the capture, it reads ${width}x$height",
            width > height,
        )

        val capture = captureScreen()
        assertTrue(
            "the captured frame must be landscape too, it reads ${capture.width}x${capture.height}",
            capture.width > capture.height,
        )
        assertBitmapMatches(
            goldenName = "VIS-52-landscape-page41.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page at landscape fit width",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-53")
    fun aRotationRoundTripRestoresThePortraitRenderingExactly() {
        openGoldenBookPage()

        val before = state()
        val portraitZoom = before.zoom
            ?: throw AssertionError("The hook reports no zoom to compare the round trip against")

        rotateToLandscape()
        awaitLandscapeLayout()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        rotateToPortrait()
        awaitPortraitLayout()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        val after = state()
        assertEquals("the page after the round trip", BaselineFixtures.BOOK_BODY_PAGE_INDEX as Int?, after.page)
        assertEquals(
            "the zoom after the round trip",
            portraitZoom,
            after.zoom ?: 0f,
            ZOOM_TOLERANCE,
        )

        assertScreenMatches(
            goldenName = "VIS-01-tlcl-page41.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page after a portrait to landscape to portrait round trip",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                allowStaleFrame = true,
            ),
        )
    }

    private fun rotateToLandscape() {
        TestEnvironment.uiDevice.setOrientationLeft()
        TestEnvironment.uiDevice.waitForIdle()
        Await.until("the physical display reports landscape", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.displayWidth > TestEnvironment.uiDevice.displayHeight
        }
    }

    private fun rotateToPortrait() {
        TestEnvironment.uiDevice.setOrientationNatural()
        TestEnvironment.uiDevice.waitForIdle()
        Await.until("the physical display reports portrait", Await.LONG_TIMEOUT_MS) {
            TestEnvironment.uiDevice.displayHeight > TestEnvironment.uiDevice.displayWidth
        }
    }

    private fun awaitLandscapeLayout() {
        reader.awaitState("the reader reports a landscape canvas", Await.LONG_TIMEOUT_MS) { current ->
            val width = current.viewWidth ?: return@awaitState false
            val height = current.viewHeight ?: return@awaitState false
            width > height
        }
    }

    private fun awaitPortraitLayout() {
        reader.awaitState("the reader reports a portrait canvas again", Await.LONG_TIMEOUT_MS) { current ->
            val width = current.viewWidth ?: return@awaitState false
            val height = current.viewHeight ?: return@awaitState false
            height > width
        }
    }

    private companion object {
        const val ZOOM_TOLERANCE = 0.001f
    }
}
