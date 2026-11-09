// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.suite.text

import com.gitlab.mudlej.MjPdfReader.suite.core.DurationCategory
import com.gitlab.mudlej.MjPdfReader.suite.core.TestDuration
import android.content.res.Configuration
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.gitlab.mudlej.MjPdfReader.suite.core.Await
import com.gitlab.mudlej.MjPdfReader.suite.core.Screens
import com.gitlab.mudlej.MjPdfReader.suite.core.TestEnvironment
import com.gitlab.mudlej.MjPdfReader.suite.core.TestId
import com.gitlab.mudlej.MjPdfReader.suite.rules.SuiteRules
import com.gitlab.mudlej.MjPdfReader.suite.seed.SettingsSeeder
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.ReaderTheme
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeActivity
import com.gitlab.mudlej.MjPdfReader.ui.text_mode.TextModeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@TestDuration(DurationCategory.NORMAL)
class TextModeSettingsClampTest {

    @get:Rule
    val rules = SuiteRules.standard()

    private val text = TextModeRobot()

    @Test
    @TestId("TXT-17")
    fun aFontSizeAboveTheMaximumIsClampedOnLoad() {
        SettingsSeeder.seed(Preferences.textModeFontSizeKey, 999f)

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            awaitTextSize(TextModeSettings.FONT_SIZE_MAX)

            text.openTypographySheet()
            assertEquals(
                "the sheet must show the clamped font size",
                TextModeSettings.FONT_SIZE_MAX,
                text.sheetSliderValue(R.id.fontSizeSlider),
                FLOAT_TOLERANCE,
            )
            text.tapSheetControl(R.id.sepiaThemeButton)
            text.closeTypographySheet()

            Await.untilValue(
                "the out of range font size is healed rather than preserved",
                read = { TestEnvironment.defaultPreferences.getFloat(Preferences.textModeFontSizeKey, 0f) },
            ) { kotlin.math.abs(it - TextModeSettings.FONT_SIZE_MAX) < FLOAT_TOLERANCE }

            text.finish()
        }
    }

    @Test
    @TestId("TXT-18")
    fun aFontSizeBelowTheMinimumIsClampedOnLoad() {
        SettingsSeeder.seed(Preferences.textModeFontSizeKey, 0f)

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            awaitTextSize(TextModeSettings.FONT_SIZE_MIN)

            text.openTypographySheet()
            assertEquals(
                "the sheet must show the clamped font size",
                TextModeSettings.FONT_SIZE_MIN,
                text.sheetSliderValue(R.id.fontSizeSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-19")
    fun aNonFiniteFontSizeFallsBackToTheDefault() {
        SettingsSeeder.seed(Preferences.textModeFontSizeKey, Float.NaN)

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            awaitTextSize(TextModeSettings.DEFAULT_FONT_SIZE)

            text.openTypographySheet()
            assertEquals(
                "the sheet must show the default font size",
                TextModeSettings.DEFAULT_FONT_SIZE,
                text.sheetSliderValue(R.id.fontSizeSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-20")
    @TestDuration(DurationCategory.SHORT)
    fun aFontSizeStoredWithTheWrongTypeFallsBackToTheDefault() {
        TestEnvironment.defaultPreferences.edit()
            .putString(Preferences.textModeFontSizeKey, "huge")
            .apply()
        val since = TextLogcat.since()

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()

            Screens.assertTopIs(TextModeActivity::class.java)
            assertTrue(
                "page one must still carry text with a wrongly typed font size stored",
                text.pageText(0).isNotBlank(),
            )
            awaitTextSize(TextModeSettings.DEFAULT_FONT_SIZE)
            TextLogcat.assertAbsentSince(since, CLASS_CAST_EXCEPTION)

            text.finish()
        }
    }

    @Test
    @TestId("TXT-22")
    fun anOutOfRangeLineSpacingIsClampedOnLoad() {
        SettingsSeeder.seed(Preferences.textModeLineSpacingKey, 50f)

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            awaitLineSpacing(TextModeSettings.LINE_SPACING_MAX)
            text.openTypographySheet()
            assertEquals(
                "the sheet must show the clamped line spacing",
                TextModeSettings.LINE_SPACING_MAX,
                text.sheetSliderValue(R.id.lineSpacingSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()
            text.finish()

            SettingsSeeder.seed(Preferences.textModeLineSpacingKey, 0.1f)
            text.openFromReaderMenu()
            text.awaitPageText(0)
            awaitLineSpacing(TextModeSettings.LINE_SPACING_MIN)
            text.openTypographySheet()
            assertEquals(
                "the sheet must show the clamped line spacing",
                TextModeSettings.LINE_SPACING_MIN,
                text.sheetSliderValue(R.id.lineSpacingSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-23")
    fun anOutOfRangeHorizontalMarginIsClampedOnLoad() {
        SettingsSeeder.seed(Preferences.textModeHorizontalMarginKey, 500)

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            awaitHorizontalPadding(TextModeSettings.HORIZONTAL_MARGIN_MAX.toInt())
            text.openTypographySheet()
            assertEquals(
                "the sheet must show the clamped margin",
                TextModeSettings.HORIZONTAL_MARGIN_MAX,
                text.sheetSliderValue(R.id.marginSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()
            text.finish()

            SettingsSeeder.seed(Preferences.textModeHorizontalMarginKey, 1)
            text.openFromReaderMenu()
            text.awaitPageText(0)
            awaitHorizontalPadding(TextModeSettings.HORIZONTAL_MARGIN_MIN.toInt())
            text.openTypographySheet()
            assertEquals(
                "the sheet must show the clamped margin",
                TextModeSettings.HORIZONTAL_MARGIN_MIN,
                text.sheetSliderValue(R.id.marginSlider),
                FLOAT_TOLERANCE,
            )
            text.closeTypographySheet()

            text.finish()
        }
    }

    @Test
    @TestId("TXT-24")
    fun anUnknownThemeNameFallsBackToSystem() {
        TestEnvironment.defaultPreferences.edit()
            .putString(Preferences.textModeThemeKey, UNKNOWN_THEME)
            .apply()
        val since = TextLogcat.since()

        TextFixtures.launchReader(TextFixtures.NORMAL).use {
            text.enterFromReader()
            assertNightModeOff()

            text.openTypographySheet()
            assertTrue(
                "the System theme must be the checked one when the stored theme name is unknown",
                text.sheetButtonChecked(R.id.systemThemeButton),
            )
            text.closeTypographySheet()

            assertEquals(
                "the text mode root must paint in the app theme surface colour",
                resolvedSurfaceColor(),
                text.rootBackgroundColor(),
            )
            TextLogcat.assertAbsentSince(since, ReaderTheme::class.java.simpleName + ".valueOf")
            TextLogcat.assertAbsentSince(since, ILLEGAL_ARGUMENT_EXCEPTION)

            text.finish()
        }
    }

    private fun assertNightModeOff() {
        val nightMode = text.onActivity { activity ->
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        assertEquals(
            "night mode must be off in the text mode activity, because the SYSTEM theme resolves colorSurface " +
                "against that activity's own configuration. Reading the process configuration instead would let " +
                "a locally forced night mode pass this guard while changing the expected colour underneath",
            Configuration.UI_MODE_NIGHT_NO,
            nightMode,
        )
    }

    private fun resolvedSurfaceColor(): Int = text.onActivity { activity ->
        val resolved = TypedValue()
        activity.theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurface,
            resolved,
            true,
        )
        if (resolved.resourceId != 0) {
            ContextCompat.getColor(activity, resolved.resourceId)
        } else {
            resolved.data
        }
    }

    private fun awaitTextSize(sp: Float) {
        val expected = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            TestEnvironment.targetContext.resources.displayMetrics,
        )
        Await.untilValue(
            "the rendered page text reads $sp sp, which is $expected pixels",
            read = { text.snapshot(0)?.textSizePx },
        ) { it != null && kotlin.math.abs(it - expected) < PIXEL_TOLERANCE }
    }

    private fun awaitLineSpacing(expected: Float) {
        Await.untilValue(
            "the rendered line spacing multiplier reads $expected",
            read = { text.snapshot(0)?.lineSpacingMultiplier },
        ) { it != null && kotlin.math.abs(it - expected) < FLOAT_TOLERANCE }
    }

    private fun awaitHorizontalPadding(marginDp: Int) {
        val density = TestEnvironment.targetContext.resources.displayMetrics.density
        val expected = (marginDp * density).toInt()
        Await.untilValue(
            "the page container pads $marginDp dp, which is $expected pixels, on both sides",
            read = { text.snapshot(0) },
        ) { it != null && it.containerPaddingLeft == expected && it.containerPaddingRight == expected }
    }

    private companion object {
        const val FLOAT_TOLERANCE = 0.001f
        const val PIXEL_TOLERANCE = 0.5f
        const val UNKNOWN_THEME = "NEON"
        const val CLASS_CAST_EXCEPTION = "ClassCastException"
        const val ILLEGAL_ARGUMENT_EXCEPTION = "IllegalArgumentException"
    }
}
