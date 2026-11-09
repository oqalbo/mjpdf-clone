// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.data.PageFitPolicy
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.state.PageFitPolicy as HookPageFitPolicy
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import com.gitlab.mudlej.MjPdfReader.suite.zoom.ReaderCanvas
import com.github.barteksc.pdfviewer.PDFView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class FitAndZoomBaselineTest : BaselineTestCase() {

    @Test
    @TestId("VIS-11")
    fun theExplicitFitWidthPolicyRendersExactlyLikeTheDefault() {
        SettingsSeeder.seed(Preferences.pageFitPolicyKey, PageFitPolicy.WIDTH.name)
        openGoldenBookPage()

        assertEquals("the resolved fit policy", HookPageFitPolicy.WIDTH, state().fitPolicy)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-11-fit-width.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page at fit width",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertMatches(
            capture,
            GOLDEN_BOOK_BODY,
            "WIDTH is the shipped default, so setting it explicitly must change nothing",
        )
    }

    @Test
    @TestId("VIS-12")
    fun fitPageScalesTheWholePageIntoTheViewportAndCentresIt() {
        SettingsSeeder.seed(Preferences.pageFitPolicyKey, PageFitPolicy.BOTH.name)
        openGoldenBookPage()

        val settled = state()
        assertEquals("the resolved fit policy", HookPageFitPolicy.BOTH, settled.fitPolicy)
        assertEquals("the page the capture is of", BaselineFixtures.BOOK_BODY_PAGE_INDEX as Int?, settled.page)
        assertNotNull("the hook reports the fit page zoom", settled.zoom)

        assertScreenMatches(
            goldenName = "VIS-12-fit-page.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page at fit page",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-13")
    fun fitHeightIsNotSilentlyAliasedToFitWidth() {
        SettingsSeeder.seed(Preferences.pageFitPolicyKey, PageFitPolicy.HEIGHT.name)
        openGoldenBookPage()

        assertEquals("the resolved fit policy", HookPageFitPolicy.HEIGHT, state().fitPolicy)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-13-fit-height.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page at fit height",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-11-fit-width.png",
            "fit height and fit width must produce different renderings on a portrait page",
        )
    }

    @Test
    @TestId("VIS-14")
    fun anExplicitZoomFactorProducesOneSpecificRendering() {
        openGoldenBookPage()

        zoomAtPageOrigin(EXPLICIT_ZOOM)

        val settled = state()
        assertEquals("the page the capture is of", BaselineFixtures.BOOK_BODY_PAGE_INDEX as Int?, settled.page)
        assertEquals("the applied zoom", EXPLICIT_ZOOM, settled.zoom ?: 0f, ZOOM_TOLERANCE)

        assertScreenMatches(
            goldenName = "VIS-14-zoom-2.5.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page at zoom $EXPLICIT_ZOOM anchored at the page origin",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                zoom = EXPLICIT_ZOOM,
            ),
        )
    }

    @Test
    @TestId("VIS-15")
    fun aPannedViewLoadsTheTilesForARegionThatIsNotThePageOrigin() {
        openGoldenBookPage()

        zoomAtPageOrigin(PANNED_ZOOM)
        val origin = state()
        val targetOffsetX = (origin.offsetX
            ?: throw AssertionError("The hook reports no horizontal page-origin offset")) + PANNED_OFFSET_X
        val targetOffsetY = (origin.offsetY
            ?: throw AssertionError("The hook reports no vertical page-origin offset")) + PANNED_OFFSET_Y

        setViewport(PANNED_ZOOM, targetOffsetX, targetOffsetY)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        val settled = state()
        assertEquals("the applied zoom", PANNED_ZOOM, settled.zoom ?: 0f, ZOOM_TOLERANCE)
        assertEquals("the applied horizontal offset", targetOffsetX, settled.offsetX ?: 0f, OFFSET_TOLERANCE)
        assertEquals("the applied vertical offset", targetOffsetY, settled.offsetY ?: 0f, OFFSET_TOLERANCE)

        assertScreenMatches(
            goldenName = "VIS-15-zoom-3-panned.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page at zoom $PANNED_ZOOM panned to " +
                    "($PANNED_OFFSET_X, $PANNED_OFFSET_Y)",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                zoom = PANNED_ZOOM,
            ),
        )
    }

    @Test
    @TestId("VIS-16")
    fun aDoubleTapZoomsToTheDocumentedMidStepWithoutOvershootingTheCeiling() {
        openGoldenBookPage()

        val before = state()
        val midZoom = before.midZoom
            ?: throw AssertionError("The reader state hook reports no mid zoom to double tap towards")
        val maxZoom = before.maxZoom
            ?: throw AssertionError("The reader state hook reports no maximum zoom to compare against")

        doubleTapAtCanvasFraction(DOUBLE_TAP_X, DOUBLE_TAP_Y)
        val settled = reader.awaitState(
            "the double tap settles at the mid zoom step",
            Await.LONG_TIMEOUT_MS,
        ) { current ->
            current.animating == false &&
                current.zoom != null &&
                Math.abs(current.zoom!! - midZoom) <= ZOOM_TOLERANCE
        }
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        assertEquals("the double tap lands on the mid zoom step", midZoom, settled.zoom ?: 0f, ZOOM_TOLERANCE)
        assertTrue(
            "the double tap must not overshoot the zoom ceiling, it reached ${settled.zoom} against $maxZoom",
            (settled.zoom ?: 0f) <= maxZoom + ZOOM_TOLERANCE,
        )

        assertScreenMatches(
            goldenName = "VIS-16-double-tap-mid-zoom.png",
            expectation = readerExpectation(
                "TLCL 25.12A body page after one double tap at the recorded coordinate",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
                zoom = midZoom,
            ),
        )
    }

    private fun zoomAtPageOrigin(zoom: Float) {
        ViewUi.onView(com.gitlab.mudlej.MjPdfReader.R.id.pdfView) { view ->
            (view as PDFView).debugSetZoomAtPageOrigin(BaselineFixtures.BOOK_BODY_PAGE_INDEX, zoom)
        }
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    private fun doubleTapAtCanvasFraction(horizontal: Float, vertical: Float) {
        ReaderCanvas.doubleTap(ReaderCanvas.at(horizontal, vertical))
    }

    private companion object {
        const val GOLDEN_BOOK_BODY = "VIS-01-tlcl-page41.png"
        const val EXPLICIT_ZOOM = 2.5f
        const val PANNED_ZOOM = 3.0f
        const val PANNED_OFFSET_X = -420f
        const val PANNED_OFFSET_Y = -900f
        const val ZOOM_TOLERANCE = 0.001f
        const val OFFSET_TOLERANCE = 0.5f
        const val DOUBLE_TAP_X = 0.5f
        const val DOUBLE_TAP_Y = 0.4f
    }
}
