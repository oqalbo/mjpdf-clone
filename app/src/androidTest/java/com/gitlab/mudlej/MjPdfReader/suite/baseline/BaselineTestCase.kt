// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import android.graphics.Bitmap
import android.graphics.Rect
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.rules.RequiresDeviceProfile
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewTestCase
import com.gitlab.mudlej.MjPdfReader.suite.rules.RequiresSoftwareRenderer
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import org.junit.Rule
import java.io.File

@RequiresSoftwareRenderer
@RequiresDeviceProfile("api36-1080x2400-440dpi-3button-en-US-swiftshader")
abstract class BaselineTestCase : ViewTestCase() {

    @get:Rule
    val baselineCleanup = BaselineCleanupRule()

    protected fun testId(): String = baselineCleanup.testId()

    protected fun baselineFixture(name: String): File =
        BaselineFixtures.place(name, suite.testDirectory.directory())

    protected fun openFullscreenPage(name: String, pageIndex: Int = 0): File {
        val file = baselineFixture(name)
        launchAt(file)
        enterFullscreenAndSettle()
        jumpToPageIndex(pageIndex)
        return file
    }

    protected fun openGoldenBookPage(pageIndex: Int = BaselineFixtures.BOOK_BODY_PAGE_INDEX): File =
        openFullscreenPage(BaselineFixtures.GOLDEN_BOOK, pageIndex)

    protected fun jumpToPageIndex(pageIndex: Int) {
        ViewUi.onView(R.id.pdfView) { view ->
            (view as com.github.barteksc.pdfviewer.PDFView).jumpTo(pageIndex)
        }
        reader.awaitPageIndex(pageIndex, Await.LONG_TIMEOUT_MS)
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    protected fun enterFullscreenAndSettle() {
        enterFullscreen()
        ViewUi.awaitVisibility(R.id.fullScreenButtonsLayout, android.view.View.GONE, OVERLAY_TIMEOUT_MS)
        reader.awaitMotionSettled()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
    }

    protected fun readerExpectation(
        contentKey: String,
        pageIndex: Int? = null,
        zoom: Float? = null,
        distinctColourFloor: Int = GateExpectation.DEFAULT_DISTINCT_COLOUR_FLOOR,
        allowStaleFrame: Boolean = false,
    ): GateExpectation = GateExpectation(
        contentKey = contentKey,
        requireReaderIdle = true,
        pageIndex = pageIndex,
        zoom = zoom,
        distinctColourFloor = distinctColourFloor,
        allowStaleFrame = allowStaleFrame,
    )

    protected fun captureScreen(): Bitmap = BaselineCapture.screen()

    protected fun assertScreenMatches(
        goldenName: String,
        expectation: GateExpectation,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult = Baselines.assertMatches(
        testId = testId(),
        goldenName = goldenName,
        actual = captureScreen(),
        expectation = expectation,
        policy = policy,
    )

    protected fun assertBitmapMatches(
        goldenName: String,
        actual: Bitmap,
        expectation: GateExpectation,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult = Baselines.assertMatches(
        testId = testId(),
        goldenName = goldenName,
        actual = actual,
        expectation = expectation,
        policy = policy,
    )

    protected fun assertCroppedScreenMatches(
        goldenName: String,
        region: Rect,
        expectation: GateExpectation,
        policy: TolerancePolicy = TolerancePolicy.DEFAULT,
    ): ComparisonResult = Baselines.assertMatches(
        testId = testId(),
        goldenName = goldenName,
        actual = BaselineCapture.crop(captureScreen(), region),
        expectation = expectation,
        policy = policy,
    )

    protected fun belowTheSystemStatusBar(screen: Bitmap): Bitmap {
        val inset = BaselineCapture.systemStatusBarInset()
        if (inset <= 0) {
            throw BaselineGateFailure(
                "The resumed screen reports a status bar inset of $inset, so this capture would carry the " +
                    "system clock into the golden and every later run would differ from it by the time of " +
                    "day. Screens that keep the system bars are compared below them, and a screen that " +
                    "reports no inset is either fullscreen, in which case it should be compared whole, or " +
                    "drawing its own bar, which the profile does not pin."
            )
        }
        return BaselineCapture.crop(screen, Rect(0, inset, screen.width, screen.height))
    }
}
