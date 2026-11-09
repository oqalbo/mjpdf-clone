// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.text_mode

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import com.gitlab.mudlej.MjPdfReader.R
import com.gitlab.mudlej.MjPdfReader.data.Preferences
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

data class TextModeSettings(
    val fontSize: Float = DEFAULT_FONT_SIZE,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val horizontalMargin: Int = DEFAULT_HORIZONTAL_MARGIN,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SANS,
    val readableLineLength: Boolean = DEFAULT_READABLE_LINE_LENGTH,
) {
    fun save(preferences: SharedPreferences) {
        preferences.edit()
            .putFloat(Preferences.textModeFontSizeKey, fontSize)
            .putFloat(Preferences.textModeLineSpacingKey, lineSpacing)
            .putInt(Preferences.textModeHorizontalMarginKey, horizontalMargin)
            .putString(Preferences.textModeThemeKey, theme.name)
            .putString(Preferences.textModeFontFamilyKey, fontFamily.name)
            .putBoolean(Preferences.textModeReadableLineLengthKey, readableLineLength)
            .apply()
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 18f
        const val DEFAULT_LINE_SPACING = 1.35f
        const val DEFAULT_HORIZONTAL_MARGIN = 20
        const val DEFAULT_READABLE_LINE_LENGTH = true

        const val FONT_SIZE_MIN = 12f
        const val FONT_SIZE_MAX = 36f
        const val FONT_SIZE_STEP = 1f
        const val LINE_SPACING_MIN = 1f
        const val LINE_SPACING_MAX = 2.2f
        const val LINE_SPACING_STEP = 0.05f
        const val HORIZONTAL_MARGIN_MIN = 8f
        const val HORIZONTAL_MARGIN_MAX = 48f
        const val HORIZONTAL_MARGIN_STEP = 2f

        internal fun snap(value: Float, min: Float, max: Float, step: Float, default: Float): Float {
            if (!value.isFinite()) {
                return default
            }
            val steps = ((value.coerceIn(min, max) - min) / step).roundToInt()
            return (min + steps * step).coerceIn(min, max)
        }

        fun load(preferences: SharedPreferences): TextModeSettings {
            val defaults = TextModeSettings()
            return TextModeSettings(
                fontSize = snap(
                    runCatching { preferences.getFloat(Preferences.textModeFontSizeKey, defaults.fontSize) }
                        .getOrDefault(defaults.fontSize),
                    FONT_SIZE_MIN, FONT_SIZE_MAX, FONT_SIZE_STEP, defaults.fontSize,
                ),
                lineSpacing = snap(
                    runCatching { preferences.getFloat(Preferences.textModeLineSpacingKey, defaults.lineSpacing) }
                        .getOrDefault(defaults.lineSpacing),
                    LINE_SPACING_MIN, LINE_SPACING_MAX, LINE_SPACING_STEP, defaults.lineSpacing,
                ),
                horizontalMargin = snap(
                    runCatching { preferences.getInt(Preferences.textModeHorizontalMarginKey, defaults.horizontalMargin) }
                        .getOrDefault(defaults.horizontalMargin).toFloat(),
                    HORIZONTAL_MARGIN_MIN, HORIZONTAL_MARGIN_MAX, HORIZONTAL_MARGIN_STEP,
                    defaults.horizontalMargin.toFloat(),
                ).roundToInt(),
                theme = runCatching { preferences.getString(Preferences.textModeThemeKey, null) }.getOrNull()
                    ?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: defaults.theme,
                fontFamily = runCatching { preferences.getString(Preferences.textModeFontFamilyKey, null) }.getOrNull()
                    ?.let { runCatching { ReaderFontFamily.valueOf(it) }.getOrNull() }
                    ?: defaults.fontFamily,
                readableLineLength = runCatching { preferences.getBoolean(Preferences.textModeReadableLineLengthKey, defaults.readableLineLength) }
                    .getOrDefault(defaults.readableLineLength),
            )
        }
    }
}

enum class ReaderTheme {
    SYSTEM,
    LIGHT,
    SEPIA,
    DARK,
    BLACK,
    DRACULA;

    fun colors(view: View): ReaderThemeColors {
        return when (this) {
            SYSTEM -> ReaderThemeColors(
                background = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, Color.WHITE),
                text = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface, Color.BLACK),
                label = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY),
            )
            LIGHT -> ReaderThemeColors(Color.rgb(250, 250, 247), Color.rgb(28, 28, 28), Color.rgb(94, 94, 94))
            SEPIA -> ReaderThemeColors(Color.rgb(244, 236, 216), Color.rgb(43, 33, 24), Color.rgb(105, 82, 58))
            DARK -> ReaderThemeColors(Color.rgb(30, 31, 34), Color.rgb(232, 234, 237), Color.rgb(176, 180, 186))
            BLACK -> ReaderThemeColors(Color.BLACK, Color.rgb(238, 238, 238), Color.rgb(180, 180, 180))
            DRACULA -> ReaderThemeColors(Color.rgb(40, 42, 54), Color.rgb(248, 248, 242), Color.rgb(98, 114, 164))
        }
    }
}

data class ReaderThemeColors(
    val background: Int,
    val text: Int,
    val label: Int,
)

enum class ReaderFontFamily {
    SANS,
    SERIF,
    MONO;

    fun typeface(): Typeface {
        return when (this) {
            SANS -> Typeface.SANS_SERIF
            SERIF -> Typeface.SERIF
            MONO -> Typeface.MONOSPACE
        }
    }

    fun label(context: Context): String {
        return when (this) {
            SANS -> context.getString(R.string.text_mode_font_sans)
            SERIF -> context.getString(R.string.text_mode_font_serif)
            MONO -> context.getString(R.string.text_mode_font_mono)
        }
    }
}
