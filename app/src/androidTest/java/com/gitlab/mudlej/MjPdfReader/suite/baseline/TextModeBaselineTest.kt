// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.baseline

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.suite.text.TextModeRobot
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderTheme
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderThemeColors
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.SHORT)
class TextModeBaselineTest : BaselineTestCase() {

    @get:Rule
    val systemNightMode = SystemNightModeRule()

    private val textMode = TextModeRobot()

    @Test
    @TestId("VIS-49")
    fun textModeOpensAtTheDocumentedDefaultTypography() {
        systemNightMode.requireNightModeOff()
        openTextModeAtTheTopOfTheBodyPage()

        val stored = TextModeSettings.load(TestEnvironment.defaultPreferences)
        assertEquals(
            "the default text mode font size",
            TextModeSettings.DEFAULT_FONT_SIZE,
            stored.fontSize,
            FLOAT_TOLERANCE,
        )
        assertEquals(
            "the default text mode line spacing",
            TextModeSettings.DEFAULT_LINE_SPACING,
            stored.lineSpacing,
            FLOAT_TOLERANCE,
        )
        assertEquals(
            "the default font size must reach the page text view, not only the preference",
            spToPx(TextModeSettings.DEFAULT_FONT_SIZE),
            textMode.awaitPageText(BaselineFixtures.BOOK_BODY_PAGE_INDEX).textSizePx,
            PIXEL_TOLERANCE,
        )
        assertControlsCardGone()

        assertBitmapMatches(
            goldenName = "VIS-49-text-mode-default.png",
            actual = captureScreen(),
            expectation = GateExpectation.forScreenOutsideTheReader(
                "text mode showing TLCL 25.12A page 41 at the default typography",
            ),
        )
    }

    @Test
    @TestId("VIS-50")
    @TestDuration(DurationCategory.LONG)
    fun everyTextModeThemePaintsItsOwnBackgroundAndInk() {
        systemNightMode.requireNightModeOff()
        openTextModeAtTheTopOfTheBodyPage()

        val recordings = mutableListOf<BaselineRecorded>()
        ReaderTheme.entries.forEach { theme ->
            try {
                applyTheme(theme)
                settleBodyPageAtTheTopWithTheControlsHidden()

                val resolved = resolvedColoursOf(theme)
                assertEquals(
                    "the page list background under the $theme theme",
                    resolved.background,
                    textMode.recyclerBackgroundColor(),
                )
                assertEquals(
                    "the page item background must match it, so no strip of the previous theme survives",
                    resolved.background,
                    textMode.itemBackgroundColor(BaselineFixtures.BOOK_BODY_PAGE_INDEX),
                )
                assertEquals(
                    "the page ink colour under the $theme theme",
                    resolved.text,
                    textMode.pageTextColor(BaselineFixtures.BOOK_BODY_PAGE_INDEX),
                )
                assertControlsCardGone()

                val screen = captureScreen()
                assertEquals(
                    "the empty margin beside the text must be painted in the $theme background, the pixel there " +
                        "reads ${describe(emptyMarginPixel(screen))} against ${describe(resolved.background)}",
                    resolved.background,
                    emptyMarginPixel(screen),
                )

                assertBitmapMatches(
                    goldenName = "VIS-50-text-mode-${theme.name.lowercase()}.png",
                    actual = screen,
                    expectation = GateExpectation.forScreenOutsideTheReader(
                        "text mode showing TLCL 25.12A page 41 in the $theme theme",
                    ),
                )
            } catch (recorded: BaselineRecorded) {
                recordings += recorded
            }
        }
        if (recordings.isNotEmpty()) {
            throw BaselineRecorded(recordings.joinToString("\n\n") { it.message.orEmpty() })
        }
    }

    @Test
    @TestId("VIS-51")
    fun theLargestAllowedTypographyStillWrapsInsideItsMargins() {
        systemNightMode.requireNightModeOff()
        SettingsSeeder.seedAll(
            mapOf(
                Preferences.textModeFontSizeKey to TextModeSettings.FONT_SIZE_MAX,
                Preferences.textModeLineSpacingKey to TextModeSettings.LINE_SPACING_MAX,
                Preferences.textModeHorizontalMarginKey to TextModeSettings.HORIZONTAL_MARGIN_MAX.toInt(),
            )
        )
        openTextModeAtTheTopOfTheBodyPage()

        val snapshot = textMode.awaitPageText(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        assertEquals(
            "the maximum font size must survive the load clamp and reach the page text view",
            spToPx(TextModeSettings.FONT_SIZE_MAX),
            snapshot.textSizePx,
            PIXEL_TOLERANCE,
        )
        assertEquals(
            "the maximum line spacing must survive the load clamp",
            TextModeSettings.LINE_SPACING_MAX,
            snapshot.lineSpacingMultiplier,
            FLOAT_TOLERANCE,
        )
        assertControlsCardGone()

        val screen = captureScreen()
        assertTextWrapsInsideThePageMargins()

        assertBitmapMatches(
            goldenName = "VIS-51-text-mode-max-typography.png",
            actual = screen,
            expectation = GateExpectation.forScreenOutsideTheReader(
                "text mode showing TLCL 25.12A page 41 at font size 36, line spacing 2.2 and margin 48",
            ),
        )
    }

    private fun openTextModeAtTheTopOfTheBodyPage() {
        launchAt(baselineFixture(BaselineFixtures.GOLDEN_BOOK))
        jumpToPageIndex(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        textMode.openFromReaderMenu()
        textMode.awaitPageText(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        settleBodyPageAtTheTopWithTheControlsHidden()
    }

    private fun settleBodyPageAtTheTopWithTheControlsHidden() {
        textMode.hideControls()
        textMode.awaitControlsGone()
        Await.until(
            "the text mode page list settles with TLCL page 41 against the top of its content, which it has to be " +
                "driven to because the activity posts its own scroll to the reader's page and a golden of a " +
                "half scrolled page records nothing repeatable",
            Await.LONG_TIMEOUT_MS,
        ) {
            TestEnvironment.onMainThread {
                textMode.layoutManager().scrollToPositionWithOffset(BaselineFixtures.BOOK_BODY_PAGE_INDEX, 0)
            }
            TestEnvironment.waitForIdleSync()
            textMode.firstVisiblePosition() == BaselineFixtures.BOOK_BODY_PAGE_INDEX && bodyPageIsAgainstTheTop()
        }
        textMode.awaitScrollIdle()
        textMode.awaitPageText(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
    }

    private fun bodyPageIsAgainstTheTop(): Boolean = TestEnvironment.onMainThread {
        val list = textMode.pages()
        val body = list.findViewHolderForAdapterPosition(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
            ?.itemView
            ?: return@onMainThread false
        body.top == list.paddingTop
    }

    private fun applyTheme(theme: ReaderTheme) {
        textMode.openTypographySheet()
        textMode.tapSheetControl(themeButtonOf(theme))
        textMode.closeTypographySheet()
        assertEquals(
            "the stored text mode theme after tapping the $theme button, which is what proves the tap landed " +
                "on the button this capture is named after",
            theme.name,
            TestEnvironment.defaultPreferences.getString(Preferences.textModeThemeKey, null),
        )
    }

    private fun themeButtonOf(theme: ReaderTheme): Int = when (theme) {
        ReaderTheme.SYSTEM -> R.id.systemThemeButton
        ReaderTheme.LIGHT -> R.id.lightThemeButton
        ReaderTheme.SEPIA -> R.id.sepiaThemeButton
        ReaderTheme.DARK -> R.id.darkThemeButton
        ReaderTheme.BLACK -> R.id.blackThemeButton
        ReaderTheme.DRACULA -> R.id.draculaThemeButton
    }

    private fun resolvedColoursOf(theme: ReaderTheme): ReaderThemeColors = TestEnvironment.onMainThread {
        theme.colors(textMode.activity().findViewById(R.id.textModeRoot))
    }

    private fun assertControlsCardGone() {
        assertEquals(
            "the text mode controls card must be gone at capture time. It hides itself on a timer, so a " +
                "golden taken with it on screen would carry a control that a later run has already dismissed",
            View.GONE,
            textMode.controlsVisibility(),
        )
    }

    private fun emptyMarginPixel(screen: Bitmap): Int {
        val bounds = textMode.recyclerBounds()
        val page = textMode.awaitPageText(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        val containerLeft = bounds.left + (bounds.width() - page.containerMeasuredWidth) / 2
        return CrossGolden.pixel(
            screen,
            containerLeft + page.containerPaddingLeft / 2,
            bounds.centerY(),
        )
    }

    private fun assertTextWrapsInsideThePageMargins() {
        val page = textMode.awaitPageText(BaselineFixtures.BOOK_BODY_PAGE_INDEX)
        val expectedMargin = dpToPx(TextModeSettings.HORIZONTAL_MARGIN_MAX)
        assertEquals("the leading page-container margin", expectedMargin, page.containerPaddingLeft)
        assertEquals("the trailing page-container margin", expectedMargin, page.containerPaddingRight)

        val availableWidth = page.containerMeasuredWidth - page.containerPaddingLeft - page.containerPaddingRight
        val measurement = textMode.onItem(BaselineFixtures.BOOK_BODY_PAGE_INDEX) { item ->
            val text = item.findViewById<TextView>(R.id.pageText)
            val layout = text.layout ?: throw AssertionError("The page text has no layout to verify wrapping")
            val contentWidth = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
            val overflowingLines = (0 until layout.lineCount).mapNotNull { line ->
                val left = layout.getLineLeft(line)
                val right = layout.getLineRight(line)
                val visibleWidth = layout.getLineMax(line)
                if (left < 0f || right > contentWidth || visibleWidth > contentWidth) {
                    LineOverflow(line, left, right, visibleWidth)
                } else {
                    null
                }
            }
            TextLayoutMeasurement(text.width, contentWidth, layout.width, overflowingLines)
        }
        assertEquals(
            "the page text is measured only inside the configured horizontal margins",
            availableWidth,
            measurement.viewWidth,
        )
        assertEquals(
            "the text layout wraps to the width left inside those margins",
            measurement.contentWidth,
            measurement.layoutWidth,
        )
        assertTrue(
            "every rendered line must stay inside the ${measurement.contentWidth} pixel text content area; " +
                "overflowing lines: ${measurement.overflowingLines.joinToString()}",
            measurement.overflowingLines.isEmpty(),
        )
    }

    private data class TextLayoutMeasurement(
        val viewWidth: Int,
        val contentWidth: Int,
        val layoutWidth: Int,
        val overflowingLines: List<LineOverflow>,
    )

    private data class LineOverflow(
        val line: Int,
        val left: Float,
        val right: Float,
        val visibleWidth: Float,
    )

    private fun describe(colour: Int): String = String.format("#%08X", colour)

    private fun spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        TestEnvironment.targetContext.resources.displayMetrics,
    )

    private fun dpToPx(dp: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        TestEnvironment.targetContext.resources.displayMetrics,
    ).toInt()

    private companion object {
        const val FLOAT_TOLERANCE = 0.001f
        const val PIXEL_TOLERANCE = 0.5f
    }
}
