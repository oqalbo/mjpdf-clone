// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.github.barteksc.pdfviewer.PDFView
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewActionsDialog
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewCrop
import com.gitlab.mudlej.MjPdfReader.suite.view.ViewUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class ThemeAndCropBaselineTest : BaselineTestCase() {

    @get:Rule
    val systemNightMode = SystemNightModeRule()

    @Test
    @TestId("VIS-17")
    fun theDarkPagesThemeInvertsPageContentAndOnlyPageContent() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seed(Preferences.pdfPagesThemeKey, Preferences.themeDark)
        openGoldenBookPage()

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-17-night-mode.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page inverted by the app setting",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertRoughlyInverted(capture, GOLDEN_BOOK_BODY, bodySamplePoints(capture))
    }

    @Test
    @TestId("VIS-18")
    fun theExplicitLightPagesThemeMatchesTheDefaultRendering() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seed(Preferences.pdfPagesThemeKey, Preferences.themeLight)
        openGoldenBookPage()

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-18-pdf-theme-light.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page with an explicit light theme",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertMatches(
            capture,
            GOLDEN_BOOK_BODY,
            "an explicit light theme must not be a different code path from the default",
        )
    }

    @Test
    @TestId("VIS-19")
    fun theSystemFollowingPagesThemeResolvesToDarkWhenTheSystemIsDark() {
        systemNightMode.enable()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pdfPagesThemeKey to Preferences.themeSystem,
                Preferences.interfaceThemeKey to Preferences.themeLight,
            )
        )
        openGoldenBookPage()

        assertThemeSources(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO)
        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-19-pdf-theme-system-dark.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page with a system theme and the system in dark mode",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertMatches(
            capture,
            "VIS-17-night-mode.png",
            "following the system into dark must resolve to the same rendering as choosing dark outright",
        )
    }

    @Test
    @TestId("VIS-20")
    fun theSystemFollowingPagesThemeResolvesToLightWhenTheSystemIsLight() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.pdfPagesThemeKey to Preferences.themeSystem,
                Preferences.interfaceThemeKey to Preferences.themeDark,
            )
        )
        openGoldenBookPage()

        assertThemeSources(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)
        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-20-pdf-theme-system-light.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page with a system theme and the system in light mode",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertMatches(
            capture,
            GOLDEN_BOOK_BODY,
            "following the system into light must resolve to the default rendering rather than sticking to " +
                "whatever it resolved to last",
        )
    }

    @Test
    @TestId("VIS-21")
    fun automaticMarginCroppingRemovesTheBorderAndEnlargesTheBody() {
        SettingsSeeder.seed(Preferences.alwaysHideMarginsKey, true)
        val runsBefore = ViewCrop.runsSoFar()
        openCroppedMarginsDocument(runsBefore)

        val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)
        val record = ViewCrop.awaitStoredCrop(fileHash, Await.LONG_TIMEOUT_MS)
        assertNotNull("the detected crop is stored on the record", record.cropMargins)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-21-crop-margins-on.png",
            actual = capture,
            expectation = readerExpectation("margins.pdf page index 0 with margins cropped away", pageIndex = 0),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-22-crop-margins-off.png",
            "a cropped page must not look like an uncropped one",
        )
    }

    @Test
    @TestId("VIS-22")
    fun turningCroppingOffInTheReaderRestoresTheFullPage() {
        SettingsSeeder.seed(Preferences.alwaysHideMarginsKey, true)
        val runsBefore = ViewCrop.runsSoFar()
        val file = baselineFixture(BaselineFixtures.MARGINS)
        launchAt(file)
        ViewCrop.awaitDetectionFinished(runsBefore)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)

        ViewActionsDialog.openAndTap(R.string.show_margins)
        TestEnvironment.waitForIdleSync()
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
        enterFullscreenAndSettle()
        jumpToPageIndex(0)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-22-crop-margins-off.png",
            actual = capture,
            expectation = readerExpectation(
                "margins.pdf page index 0 uncropped after an in reader toggle off",
                pageIndex = 0,
            ),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-21-crop-margins-on.png",
            "toggling the crop off must restore the full page rather than leave a stale cropped render",
        )
    }

    @Test
    @TestId("VIS-23")
    fun aCroppedPageAndTheInvertingFilterComposeCorrectly() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.alwaysHideMarginsKey to true,
                Preferences.pdfPagesThemeKey to Preferences.themeDark,
            )
        )
        val runsBefore = ViewCrop.runsSoFar()
        openCroppedMarginsDocument(runsBefore)
        val fileHash = reader.awaitFileHashResolved(Await.LONG_TIMEOUT_MS)
        val record = ViewCrop.awaitStoredCrop(fileHash, Await.LONG_TIMEOUT_MS)
        assertNotNull("the crop composed with night mode is stored on the record", record.cropMargins)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-23-crop-and-night.png",
            actual = capture,
            expectation = readerExpectation("margins.pdf page index 0 cropped and inverted", pageIndex = 0),
        )
        CrossGolden.assertDiffers(
            capture,
            "VIS-21-crop-margins-on.png",
            "the dark filter must visibly invert the same cropped page",
        )
    }

    @Test
    @TestId("VIS-24")
    fun theHighQualitySettingReachesTheBitmapConfig() {
        SettingsSeeder.seed(Preferences.highQualityKey, true)
        openGoldenBookPage()

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-24-high-quality.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page at high quality",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
        CrossGolden.assertDiffers(
            capture,
            GOLDEN_BOOK_BODY,
            "high quality renders page bitmaps as ARGB_8888 rather than RGB_565, so it must change the pixels",
        )
    }

    @Test
    @TestId("VIS-25")
    fun turningAntiAliasingOffReachesTheDrawPath() {
        SettingsSeeder.seed(Preferences.antiAliasingKey, false)
        openGoldenBookPage()
        ViewUi.onView(R.id.pdfView) { view ->
            assertFalse(
                "The seeded false antiAliasing preference must reach PDFView before capture",
                (view as PDFView).isAntialiasing,
            )
        }

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-25-antialiasing-off.png",
            actual = capture,
            expectation = readerExpectation(
                "TLCL 25.12A body page with anti aliasing off",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
    }

    @Test
    @TestId("VIS-56")
    fun theDarkPagesThemeInvertsAnImageOnlyPage() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seed(Preferences.pdfPagesThemeKey, Preferences.themeDark)
        openFullscreenPage(BaselineFixtures.SCANNED)

        val capture = captureScreen()
        assertBitmapMatches(
            goldenName = "VIS-56-scanned-night-mode.png",
            actual = capture,
            expectation = readerExpectation("scanned.pdf page index 0 inverted by the app setting", pageIndex = 0),
        )
        CrossGolden.assertRoughlyInverted(capture, "VIS-05-scanned-page1.png", bodySamplePoints(capture))
    }

    @Test
    @TestId("VIS-57")
    fun theDarkInterfaceThemePaintsTheReaderChromeWithoutChangingThePageTheme() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.interfaceThemeKey to Preferences.themeDark,
                Preferences.pdfPagesThemeKey to Preferences.themeLight,
            )
        )
        launchAt(baselineFixture(BaselineFixtures.GOLDEN_BOOK))
        jumpToPageIndex(BaselineFixtures.BOOK_BODY_PAGE_INDEX)

        assertEquals(
            "the dark interface preference resolves to AppCompat night mode",
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.getDefaultNightMode(),
        )
        assertTrue("the reader configuration carries the night bit", ViewUi.uiModeNight())
        assertEquals(
            "the explicit light PDF theme keeps the page surround light",
            Preferences.pdfDarkBackgroundColor,
            ViewUi.pdfViewBackgroundColor(),
        )

        val frame = belowTheSystemStatusBar(captureScreen())
        assertBitmapMatches(
            goldenName = "VIS-57-reader-chrome-dark.png",
            actual = frame,
            expectation = readerExpectation(
                "dark reader chrome around a light TLCL page",
                pageIndex = BaselineFixtures.BOOK_BODY_PAGE_INDEX,
            ),
        )
    }

    private fun assertThemeSources(systemNightMode: Int, interfaceNightMode: Int) {
        TestEnvironment.onMainThread {
            val activity = ViewUi.reader()
            assertEquals(
                "the system configuration must remain independent of the interface theme",
                systemNightMode,
                activity.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            )
            assertEquals(
                "the interface must use the opposite theme from the system",
                interfaceNightMode,
                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            )
        }
    }

    private fun openCroppedMarginsDocument(runsBefore: Int) {
        val file = baselineFixture(BaselineFixtures.MARGINS)
        launchAt(file)
        ViewCrop.awaitDetectionFinished(runsBefore)
        reader.awaitFullQualityRender(Await.LONG_TIMEOUT_MS)
        enterFullscreenAndSettle()
    }

    private fun bodySamplePoints(capture: android.graphics.Bitmap): List<CrossGolden.Point> {
        val width = capture.width
        val height = capture.height
        return listOf(
            CrossGolden.Point((width * 0.30f).toInt(), (height * 0.30f).toInt()),
            CrossGolden.Point((width * 0.70f).toInt(), (height * 0.30f).toInt()),
            CrossGolden.Point((width * 0.30f).toInt(), (height * 0.60f).toInt()),
            CrossGolden.Point((width * 0.70f).toInt(), (height * 0.60f).toInt()),
        )
    }

    private companion object {
        const val GOLDEN_BOOK_BODY = "VIS-01-tlcl-page41.png"
    }
}
