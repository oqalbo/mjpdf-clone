// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Rotation
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.data.Records
import com.gitlab.mudlej.MjPdfReader.suite.robot.ReaderRobot
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderFontFamily
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderTheme
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeTypographyTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val reader = ReaderRobot()
    private val text = TextModeRobot()

    @Test
    @TestId("TXT-16")
    fun raisingTheFontSizePersistsAndEnlargesTheText() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader()
            val before = text.awaitPageText(0).textSizePx

            text.openTypographySheet()
            text.dragSheetSliderToMaximum(R.id.fontSizeSlider)
            text.closeTypographySheet()

            val expected = spToPx(TextModeSettings.FONT_SIZE_MAX)
            awaitTextSize(expected)
            assertEquals(
                "the stored text mode font size",
                TextModeSettings.FONT_SIZE_MAX,
                TestEnvironment.defaultPreferences.getFloat(Preferences.textModeFontSizeKey, 0f),
                FLOAT_TOLERANCE,
            )
            assertTrue(
                "the rendered text must grow, it was $before pixels and is now ${text.snapshot(0)?.textSizePx}",
                (text.snapshot(0)?.textSizePx ?: 0f) > before,
            )

            TestEnvironment.uiDevice.pressBack()
            text.awaitFinished()
            text.openFromReaderMenu()
            text.awaitPageText(0)
            awaitTextSize(expected)

            text.finish()
        }
    }

    @Test
    @TestId("TXT-21")
    fun theLineSpacingChangePersistsSnappedToTheStep() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader()

            text.openTypographySheet()
            text.dragSheetSliderToMaximum(R.id.lineSpacingSlider)
            text.closeTypographySheet()

            assertEquals(
                "the stored text mode line spacing",
                TextModeSettings.LINE_SPACING_MAX,
                TestEnvironment.defaultPreferences.getFloat(Preferences.textModeLineSpacingKey, 0f),
                FLOAT_TOLERANCE,
            )
            Await.untilValue(
                "the rendered line spacing multiplier reaches ${TextModeSettings.LINE_SPACING_MAX}",
                read = { text.snapshot(0)?.lineSpacingMultiplier },
            ) { it != null && kotlin.math.abs(it - TextModeSettings.LINE_SPACING_MAX) < FLOAT_TOLERANCE }

            text.openTypographySheet()
            assertEquals(
                "the reopened sheet must show the stored line spacing",
                TextModeSettings.LINE_SPACING_MAX,
                text.sheetSliderValue(R.id.lineSpacingSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-25")
    fun aThemeChoiceRepaintsTheViewAndPersists() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader()

            text.openTypographySheet()
            text.tapSheetControl(R.id.sepiaThemeButton)
            text.closeTypographySheet()

            assertEquals(
                "the stored text mode theme",
                ReaderTheme.SEPIA.name,
                TestEnvironment.defaultPreferences.getString(Preferences.textModeThemeKey, null),
            )

            text.hideControls()
            Await.untilValue(
                "the text mode root repaints in the sepia background",
                read = { text.rootBackgroundColor() },
            ) { it == SEPIA_BACKGROUND }
            assertEquals(
                "the page list background must be sepia too, leaving no white gap",
                SEPIA_BACKGROUND,
                text.recyclerBackgroundColor(),
            )
            assertEquals("the page item background must be sepia", SEPIA_BACKGROUND, text.itemBackgroundColor(0))
            assertEquals("the page text colour must be the sepia ink", SEPIA_TEXT, text.pageTextColor(0))

            text.finish()
        }
    }

    @Test
    @TestId("TXT-26")
    fun theFontFamilyChangeAppliesToThePageText() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader()

            text.openTypographySheet()
            text.tapSheetControl(R.id.serifFontButton)
            assertTrue(
                "the tap must reach the Serif button, which reads back as the checked font in the sheet",
                text.sheetButtonChecked(R.id.serifFontButton),
            )
            text.closeTypographySheet()

            assertEquals(
                "the stored text mode font family",
                ReaderFontFamily.SERIF.name,
                TestEnvironment.defaultPreferences.getString(Preferences.textModeFontFamilyKey, null),
            )
            Await.untilValue(
                "the page text switches to the serif typeface",
                read = { text.snapshot(0)?.typeface },
            ) { it === Typeface.SERIF }

            text.finish()
        }
    }

    @Test
    @TestId("TXT-27")
    fun readableLineLengthCapsTheTextColumn() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            text.enterFromReader()

            Rotation.toLandscape(TextModeActivity::class.java)
            text.awaitPageText(0)

            val maxWidth = TestEnvironment.targetContext.resources
                .getDimensionPixelSize(R.dimen.text_mode_content_max_width)
            val listWidth = text.recyclerWidth()
            assertTrue(
                "this display is $listWidth pixels wide and the readable line length cap is $maxWidth pixels, so " +
                    "the cap cannot bind and the test would pass without proving anything. The pinned portrait " +
                    "profile is 1080 pixels wide, which is why this test rotates to landscape first.",
                listWidth > maxWidth,
            )

            Await.untilValue(
                "the page container is capped at the readable line length",
                read = { text.snapshot(0)?.containerMeasuredWidth },
            ) { it == maxWidth }

            text.openTypographySheet()
            text.setSheetSwitch(R.id.readableLineLengthSwitch, false)
            text.closeTypographySheet()

            assertFalse(
                "the stored readable line length flag",
                TestEnvironment.defaultPreferences
                    .getBoolean(Preferences.textModeReadableLineLengthKey, true),
            )
            Await.untilValue(
                "the page container expands to the full page list width",
                read = { text.snapshot(0)?.containerMeasuredWidth },
            ) { it == text.recyclerWidth() }

            text.finish()
        }
    }

    @Test
    @TestId("TXT-28")
    fun resetRestoresDefaultTypographyAndClearsThePerBookOverrides() {
        TextFixtures.launchReader(TextFixtures.STRUCTURED).use {
            reader.awaitShown()
            reader.awaitLoaded()
            reader.awaitRenderIdle()
            val hash = reader.awaitFileHashResolved()

            text.openFromReaderMenu()
            text.awaitPageText(0)

            text.openTypographySheet()
            text.dragSheetSliderToMaximum(R.id.fontSizeSlider)
            text.tapSheetControl(R.id.draculaThemeButton)
            text.tapSheetControl(R.id.monoFontButton)
            text.setSheetSwitch(R.id.joinParagraphsSwitch, false)
            text.setSheetSwitch(R.id.detectHeadingsSwitch, false)
            Records.awaitRow(hash, "the per book reflow overrides are stored") {
                it?.textModeJoinParagraphs == false && it.textModeDetectHeadings == false
            }

            text.tapSheetControl(R.id.resetSettingsButton)

            assertTrue("Join Paragraphs must read on after Reset", text.sheetSwitchChecked(R.id.joinParagraphsSwitch))
            assertTrue("Detect Headings must read on after Reset", text.sheetSwitchChecked(R.id.detectHeadingsSwitch))
            assertTrue("Code Blocks must read on after Reset", text.sheetSwitchChecked(R.id.codeBlocksSwitch))
            assertTrue(
                "Readable Line Length must read on after Reset",
                text.sheetSwitchChecked(R.id.readableLineLengthSwitch),
            )
            assertTrue("the System theme must be the checked one", text.sheetButtonChecked(R.id.systemThemeButton))
            assertTrue("the Sans font must be the checked one", text.sheetButtonChecked(R.id.sansFontButton))
            text.closeTypographySheet()

            val preferences = TestEnvironment.defaultPreferences
            assertEquals(
                "the font size returns to its default",
                TextModeSettings.DEFAULT_FONT_SIZE,
                preferences.getFloat(Preferences.textModeFontSizeKey, 0f),
                FLOAT_TOLERANCE,
            )
            assertEquals(
                "the line spacing returns to its default",
                TextModeSettings.DEFAULT_LINE_SPACING,
                preferences.getFloat(Preferences.textModeLineSpacingKey, 0f),
                FLOAT_TOLERANCE,
            )
            assertEquals(
                "the horizontal margin returns to its default",
                TextModeSettings.DEFAULT_HORIZONTAL_MARGIN,
                preferences.getInt(Preferences.textModeHorizontalMarginKey, 0),
            )
            assertEquals(
                "the theme returns to its default",
                ReaderTheme.SYSTEM.name,
                preferences.getString(Preferences.textModeThemeKey, null),
            )
            assertEquals(
                "the font family returns to its default",
                ReaderFontFamily.SANS.name,
                preferences.getString(Preferences.textModeFontFamilyKey, null),
            )
            assertTrue(
                "readable line length returns to its default",
                preferences.getBoolean(Preferences.textModeReadableLineLengthKey, false),
            )

            val cleared = Records.awaitRow(hash, "the per book reflow overrides are cleared") {
                it?.textModeJoinParagraphs == null
            }
            assertNull("Detect Headings must be cleared by Reset", cleared?.textModeDetectHeadings)
            assertNull("Code Blocks must be cleared by Reset", cleared?.textModeCodeBlocks)

            CapturedReflow.compare(
                "STRUCTURED_PAGE_1_DEFAULT",
                CapturedReflow.STRUCTURED_PAGE_1_DEFAULT,
                text.pageText(0).trim(),
            )

            text.finish()
        }
    }

    private fun awaitTextSize(expectedPx: Float) {
        Await.untilValue(
            "the rendered page text reaches $expectedPx pixels",
            read = { text.snapshot(0)?.textSizePx },
        ) { it != null && kotlin.math.abs(it - expectedPx) < PIXEL_TOLERANCE }
    }

    private fun spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        TestEnvironment.targetContext.resources.displayMetrics,
    )

    private companion object {
        const val FLOAT_TOLERANCE = 0.001f
        const val PIXEL_TOLERANCE = 0.5f
        val SEPIA_BACKGROUND = Color.rgb(244, 236, 216)
        val SEPIA_TEXT = Color.rgb(43, 33, 24)
    }
}
